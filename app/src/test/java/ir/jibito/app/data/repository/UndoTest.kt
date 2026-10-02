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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «برگردان»: بعد از تعیین دسته یا حذف، همه‌چیز (حتی اثر یادگیری روی تراکنش‌های دیگر) به حالت قبل برمی‌گردد. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UndoTest {

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
    fun undoCategoryAlsoRevertsLearnedAutoCategories() = runBlocking {
        val dao = db.transactionFlowDao()
        val cafe = db.categoryDao().insert(CategoryEntity(name = "کافه", icon = "☕", colorHex = "#000000", flowType = 2))
        // دو انتخاب قبلی کاربر؛ سومی دسته را برای این فروشگاه خودکار می‌کند
        dao.insert(tx(smsId = 1, date = 1_000, categoryId = cafe))
        dao.insert(tx(smsId = 2, date = 2_000, categoryId = cafe))
        val other = dao.insert(tx(smsId = 3, date = 3_000, categoryId = null))
        val picked = dao.insert(tx(smsId = 4, date = 4_000, categoryId = null))

        val before = repo.snapshotForUndo(picked)
        repo.setCategory(picked, cafe)
        // یادگیری اثر کرد: تراکنش دیگرِ همین فروشگاه هم خودکار «کافه» شد
        assertEquals(cafe, dao.byId(other)!!.categoryId)
        assertTrue(dao.byId(other)!!.isAutoCategorized)

        repo.restore(before)
        assertNull(dao.byId(picked)!!.categoryId)
        assertNull(dao.byId(other)!!.categoryId)
        assertFalse(dao.byId(other)!!.isAutoCategorized)
        // انتخاب‌های قدیمی کاربر دست نخورده‌اند
        assertEquals(cafe, dao.byId(1)!!.categoryId)
    }

    @Test
    fun undoDeleteBringsManualTransactionBack() = runBlocking {
        val dao = db.transactionFlowDao()
        val id = dao.insert(tx(smsId = null, date = 5_000, categoryId = null, source = SOURCE_MANUAL))

        val before = repo.snapshotForUndo(id)
        repo.deleteManual(id)
        assertTrue(dao.byId(id)!!.isDeleted)

        repo.restore(before)
        assertFalse(dao.byId(id)!!.isDeleted)
    }

    private fun tx(smsId: Long?, date: Long, categoryId: Long?, source: String = SOURCE_SMS_AUTO) = TransactionFlowEntity(
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
        source = source,
    )
}
