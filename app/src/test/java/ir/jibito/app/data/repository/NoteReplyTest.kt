package ir.jibito.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.data.sms.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** جعبه‌ی «بنویس» نوتیفیکیشن: نوشته همیشه یادداشت می‌شود و اگر با دسته‌ای جور شد، دسته هم می‌گیرد. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteReplyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepositoryImpl

    @Before
    fun setUp() {
        context.deleteDatabase(AppDatabase.NAME)
        db = AppDatabase.build(context)
        repo = TransactionRepositoryImpl(db, SmsReader(context), SyncState(context))
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(AppDatabase.NAME)
    }

    @Test
    fun matchingTextSetsCategoryAndKeepsTheNote() = runBlocking {
        val transport = db.categoryDao().insert(CategoryEntity(name = "حمل‌ونقل", flowType = 2))
        val taxi = db.categoryDao().insert(CategoryEntity(name = "تاکسی اینترنتی", flowType = 2, parentId = transport))
        val id = db.transactionFlowDao().insert(tx(smsId = 1))

        val reply = repo.applyNoteReply(id, "  اسنپ تا   فرودگاه ")!!

        assertEquals(taxi, reply.categoryId)
        assertEquals("تاکسی اینترنتی", reply.categoryName)
        val row = db.transactionFlowDao().byId(id)!!
        assertEquals("اسنپ تا فرودگاه", row.note)
        assertEquals(taxi, row.categoryId)
        // مثل انتخاب خود کاربر: «خودکار» نیست و زمان انتخاب دارد (برای یادگیری)
        assertEquals(false, row.isAutoCategorized)
        assertEquals(true, row.categorizedAt != null)
    }

    /** کاربری که در تنظیمات لایه‌ها را کم کرده: دسته‌ی جورشده تا همان لایه بالا می‌رود */
    @Test
    fun matchFollowsTheChosenDepth() = runBlocking {
        val transport = db.categoryDao().insert(CategoryEntity(name = "حمل‌ونقل", flowType = 2))
        val car = db.categoryDao().insert(CategoryEntity(name = "خودرو شخصی", flowType = 2, parentId = transport))
        val fuel = db.categoryDao().insert(CategoryEntity(name = "سوخت", flowType = 2, parentId = car))

        // فقط دسته‌ی اصلی: «بنزین زدم» ← «سوخت» ← «حمل‌ونقل»
        val onlyRoots = db.transactionFlowDao().insert(tx(smsId = 1))
        val reply = repo.applyNoteReply(onlyRoots, "بنزین زدم", maxDepth = 1)!!
        assertEquals(transport, reply.categoryId)
        assertEquals("حمل‌ونقل", reply.categoryName)
        assertEquals(transport, db.transactionFlowDao().byId(onlyRoots)!!.categoryId)
        assertEquals("بنزین زدم", db.transactionFlowDao().byId(onlyRoots)!!.note)

        // تا زیردسته: اسم دقیق دسته‌ی لایه‌ی ۳ هم به زیردسته می‌رسد
        val twoLevels = db.transactionFlowDao().insert(tx(smsId = 2))
        assertEquals(car, repo.applyNoteReply(twoLevels, "سوخت", maxDepth = 2)!!.categoryId)

        // همه‌ی لایه‌ها (پیش‌فرض): همان دسته‌ی دقیق
        val all = db.transactionFlowDao().insert(tx(smsId = 3))
        assertEquals(fuel, repo.applyNoteReply(all, "بنزین زدم")!!.categoryId)
    }

    @Test
    fun unmatchedTextIsOnlyANote() = runBlocking {
        db.categoryDao().insert(CategoryEntity(name = "کافه", flowType = 2))
        val id = db.transactionFlowDao().insert(tx(smsId = 1))

        val reply = repo.applyNoteReply(id, "قرض به علی")!!

        assertNull(reply.categoryId)
        assertEquals("قرض به علی", db.transactionFlowDao().byId(id)!!.note)
        assertNull(db.transactionFlowDao().byId(id)!!.categoryId)
    }

    @Test
    fun depositsOnlyMatchIncomeCategories() = runBlocking {
        db.categoryDao().insert(CategoryEntity(name = "هدیه", flowType = 2))
        val incomeGift = db.categoryDao().insert(CategoryEntity(name = "هدیه", flowType = 1))
        val id = db.transactionFlowDao().insert(tx(smsId = 1, flowType = 1))

        assertEquals(incomeGift, repo.applyNoteReply(id, "هدیه")!!.categoryId)
    }

    @Test
    fun emptyTextChangesNothing() = runBlocking {
        val id = db.transactionFlowDao().insert(tx(smsId = 1))
        assertNull(repo.applyNoteReply(id, "   "))
        assertNull(db.transactionFlowDao().byId(id)!!.note)
    }

    @Test
    fun setNoteCleansAndClears() = runBlocking {
        val id = db.transactionFlowDao().insert(tx(smsId = 1))
        repo.setNote(id, "  شام \n تولد ")
        assertEquals("شام تولد", db.transactionFlowDao().byId(id)!!.note)
        repo.setNote(id, "")
        assertNull(db.transactionFlowDao().byId(id)!!.note)
    }

    @Test
    fun noteIsPartOfSyncKeysSoRescanKeepsIt() = runBlocking {
        val id = db.transactionFlowDao().insert(tx(smsId = 1))
        repo.setNote(id, "قسط وام")
        assertEquals("قسط وام", db.transactionFlowDao().smsKeys().single { it.id == id }.note)
    }

    private fun tx(smsId: Long, flowType: Int = 2) = TransactionFlowEntity(
        smsId = smsId,
        bankId = 1,
        flowType = flowType,
        amount = 1_000_000,
        remainAfter = null,
        dateEpoch = 1_000,
        merchant = null,
        suggestedCategory = null,
        isFailedPurchase = false,
        categoryId = null,
        description = null,
        smsContent = "sms $smsId",
        source = SOURCE_SMS_AUTO,
    )
}
