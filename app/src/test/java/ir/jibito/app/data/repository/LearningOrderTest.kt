package ir.jibito.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.category.CategoryLearning
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.data.sms.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** یادگیری دسته: «آخرین نظر» کاربر از روی زمان انتخاب است، نه تاریخ تراکنش. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearningOrderTest {

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
    fun changingAnOldTransactionCountsAsTheLatestChoice() = runBlocking {
        val dao = db.transactionFlowDao()
        val cafe = db.categoryDao().insert(CategoryEntity(name = "کافه", icon = "☕", colorHex = "#000000", flowType = 2))
        val restaurant = db.categoryDao().insert(CategoryEntity(name = "رستوران", icon = "🍽", colorHex = "#000000", flowType = 2))
        // انتخاب‌های قدیمی (قبل از نسخه‌ی ۱۲ دیتابیس، بدون زمان انتخاب) روی تراکنش‌های تازه‌تر
        val oldest = dao.insert(tx(smsId = 1, date = 1_000, categoryId = null))
        dao.insert(tx(smsId = 2, date = 3_000, categoryId = cafe))
        dao.insert(tx(smsId = 3, date = 4_000, categoryId = cafe))

        // کاربر الان دسته‌ی قدیمی‌ترین تراکنش را «رستوران» می‌گذارد ← این آخرین نظر اوست
        repo.setCategory(oldest, restaurant)

        assertNotNull(dao.byId(oldest)!!.categorizedAt)
        assertEquals(restaurant, CategoryLearning(db).decide("کافه نادری", 2)!!.categoryId)
    }

    @Test
    fun undoRestoresTheChoiceTime() = runBlocking {
        val dao = db.transactionFlowDao()
        val cafe = db.categoryDao().insert(CategoryEntity(name = "کافه", icon = "☕", colorHex = "#000000", flowType = 2))
        val id = dao.insert(tx(smsId = 1, date = 1_000, categoryId = null))

        val before = repo.snapshotForUndo(id)
        repo.setCategory(id, cafe)
        assertNotNull(dao.byId(id)!!.categorizedAt)

        repo.restore(before)
        assertNull(dao.byId(id)!!.categoryId)
        assertNull(dao.byId(id)!!.categorizedAt)
    }

    @Test
    fun removingTheCategoryClearsTheChoiceTime() = runBlocking {
        val dao = db.transactionFlowDao()
        val cafe = db.categoryDao().insert(CategoryEntity(name = "کافه", icon = "☕", colorHex = "#000000", flowType = 2))
        val id = dao.insert(tx(smsId = 1, date = 1_000, categoryId = null))
        repo.setCategory(id, cafe)
        repo.setCategory(id, null)
        assertNull(dao.byId(id)!!.categorizedAt)
        assertFalse(dao.byId(id)!!.isAutoCategorized)
    }

    private fun tx(smsId: Long?, date: Long, categoryId: Long?) = TransactionFlowEntity(
        smsId = smsId,
        bankId = 1,
        flowType = 2,
        amount = 1_000_000,
        remainAfter = null,
        dateEpoch = date,
        merchant = "کافه نادری",
        suggestedCategory = null,
        isFailedPurchase = false,
        categoryId = categoryId,
        description = null,
        smsContent = smsId?.let { "sms $it" },
        source = SOURCE_SMS_AUTO,
    )
}
