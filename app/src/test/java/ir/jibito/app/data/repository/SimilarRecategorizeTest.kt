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

/** «تراکنش‌های مشابه هم عوض شود؟»: فقط بی‌دسته و دسته‌ی خودکار عوض می‌شوند، نه انتخاب کاربر. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimilarRecategorizeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepositoryImpl
    private var cafe = 0L
    private var taxi = 0L

    @Before
    fun setUp() {
        context.deleteDatabase(AppDatabase.NAME)
        db = AppDatabase.build(context)
        repo = TransactionRepositoryImpl(db, SmsReader(context), SyncState(context))
        runBlocking {
            cafe = db.categoryDao().insert(CategoryEntity(name = "کافه", icon = "☕", colorHex = "#000000", flowType = 2))
            taxi = db.categoryDao().insert(CategoryEntity(name = "تاکسی", icon = "🚕", colorHex = "#000000", flowType = 2))
        }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(AppDatabase.NAME)
    }

    @Test
    fun changesOnlyUncategorizedAndAutoCategorized() = runBlocking {
        val dao = db.transactionFlowDao()
        val picked = dao.insert(tx(1))
        val none = dao.insert(tx(2))
        val auto = dao.insert(tx(3, categoryId = taxi, auto = true))
        val byUser = dao.insert(tx(4, categoryId = taxi, auto = false))
        val otherMerchant = dao.insert(tx(5, merchant = "جای دیگر"))
        val otherFlow = dao.insert(tx(6, flowType = 1))
        repo.setCategory(picked, cafe)

        assertEquals(2, repo.countSimilar(picked, cafe))
        val result = repo.recategorizeSimilar(picked, cafe)!!
        assertEquals(2, result.count)

        assertEquals(cafe, dao.byId(none)!!.categoryId)
        assertEquals(cafe, dao.byId(auto)!!.categoryId)
        assertTrue(dao.byId(auto)!!.isAutoCategorized)
        // انتخاب خود کاربر، طرف حساب دیگر و خرج/درآمد دیگر دست نمی‌خورند
        assertEquals(taxi, dao.byId(byUser)!!.categoryId)
        assertNull(dao.byId(otherMerchant)!!.categoryId)
        assertNull(dao.byId(otherFlow)!!.categoryId)
        // خود تراکنش انتخاب کاربر می‌ماند (در یادگیری شمرده می‌شود)
        assertFalse(dao.byId(picked)!!.isAutoCategorized)
    }

    @Test
    fun undoBringsEverythingBack() = runBlocking {
        val dao = db.transactionFlowDao()
        val picked = dao.insert(tx(1))
        val none = dao.insert(tx(2))
        val auto = dao.insert(tx(3, categoryId = taxi, auto = true))
        repo.setCategory(picked, cafe)

        val result = repo.recategorizeSimilar(picked, cafe)!!
        repo.restore(result.undo)

        assertNull(dao.byId(none)!!.categoryId)
        assertEquals(taxi, dao.byId(auto)!!.categoryId)
        assertTrue(dao.byId(auto)!!.isAutoCategorized)
        assertEquals(cafe, dao.byId(picked)!!.categoryId)
    }

    @Test
    fun personTransferIsNeverOffered() = runBlocking {
        val dao = db.transactionFlowDao()
        val picked = dao.insert(tx(1, merchant = "کارت ۶۰۳۷۱۲۳۴"))
        dao.insert(tx(2, merchant = "کارت ۶۰۳۷۱۲۳۴"))
        dao.insert(tx(3, merchant = "کارت ۶۰۳۷۱۲۳۴"))
        repo.setCategory(picked, cafe)

        assertEquals(0, repo.countSimilar(picked, cafe))
        assertNull(repo.recategorizeSimilar(picked, cafe))
    }

    @Test
    fun noMerchantMeansNothingToChange() = runBlocking {
        val dao = db.transactionFlowDao()
        val picked = dao.insert(tx(1, merchant = null))
        dao.insert(tx(2, merchant = null))
        repo.setCategory(picked, cafe)

        assertEquals(0, repo.countSimilar(picked, cafe))
    }

    private fun tx(
        smsId: Long,
        merchant: String? = "کافه نادری",
        flowType: Int = 2,
        categoryId: Long? = null,
        auto: Boolean = false,
    ) = TransactionFlowEntity(
        smsId = smsId,
        bankId = 1,
        flowType = flowType,
        amount = 1_000_000,
        remainAfter = null,
        dateEpoch = smsId * 1_000,
        merchant = merchant,
        suggestedCategory = null,
        isFailedPurchase = false,
        categoryId = categoryId,
        isAutoCategorized = auto,
        description = null,
        smsContent = "sms $smsId",
        source = SOURCE_SMS_AUTO,
    )
}
