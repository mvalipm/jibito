package ir.jibito.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.data.sms.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «اسم فروشگاه کدومه؟»: کاربر خط اسم را نشان می‌دهد؛ این تراکنش و هم‌شکل‌هایش اسم و پیشنهاد دسته می‌گیرند */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MerchantTeachingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepositoryImpl

    private val snappOtp = "پاسارگاد\nخرید\nاسنپ مارکت\nمبلغ:12,321,000\nرمز: 04720\n09:57:36"
    private val digikalaOtp = "پاسارگاد\nخرید\nدیجی کالا\nمبلغ:850,000\nرمز: 91833\n21:04:10"
    private val transferOtp = "پاسارگاد\nانتقال به 610433*2805\nمبلغ:10,000,000\nرمز: 44410\n19:50:19"

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
    fun teachingALineNamesThisAndSameShapedTransactions() = runBlocking {
        val dao = db.transactionFlowDao()
        val snapp = dao.insert(tx(1, bankId = 12, otp = snappOtp))
        val digikala = dao.insert(tx(2, bankId = 12, otp = digikalaOtp))
        val transfer = dao.insert(tx(3, bankId = 12, otp = transferOtp))
        val otherBank = dao.insert(tx(4, bankId = 11, otp = digikalaOtp))

        val others = repo.teachMerchant(snapp, "اسنپ مارکت", MerchantLesson(fromOtp = true, lineIndex = 2))

        assertEquals(1, others)
        assertEquals("اسنپ مارکت", dao.byId(snapp)!!.merchant)
        assertEquals("سوپرمارکت", dao.byId(snapp)!!.suggestedCategory)
        assertEquals("دیجی کالا", dao.byId(digikala)!!.merchant)
        assertNull("different shape", dao.byId(transfer)!!.merchant)
        assertNull("rules are per bank", dao.byId(otherBank)!!.merchant)
        assertEquals(1, db.reviewDao().merchantRules().size)

        // دوباره یاد دادن همان شکل، قانون تکراری نمی‌سازد
        repo.teachMerchant(digikala, "دیجی کالا", MerchantLesson(fromOtp = true, lineIndex = 2))
        assertEquals(1, db.reviewDao().merchantRules().size)
    }

    @Test
    fun typedNameIsForThisTransactionOnlyAndSurvivesInSyncKeys() = runBlocking {
        val dao = db.transactionFlowDao()
        val id = dao.insert(tx(1, bankId = 12, otp = null))
        assertEquals(0, repo.teachMerchant(id, "  کرایه خونه  ", lesson = null))
        assertEquals("کرایه خونه", dao.byId(id)!!.merchant)
        assertTrue(db.reviewDao().merchantRules().isEmpty())
        // خواندن دوباره‌ی پیامک‌ها اسم قبلی را از همین ستون می‌بیند (SmsSync نگهش می‌دارد)
        assertEquals("کرایه خونه", dao.smsKeys().single { it.id == id }.merchant)
    }

    private fun tx(smsId: Long, bankId: Int, otp: String?) = TransactionFlowEntity(
        smsId = smsId,
        bankId = bankId,
        flowType = 2,
        amount = 12_321_000,
        remainAfter = null,
        dateEpoch = 1_000 + smsId,
        merchant = null,
        suggestedCategory = null,
        isFailedPurchase = false,
        categoryId = null,
        description = null,
        smsContent = "777.888.16305454.1\n-12,321,000\n07/16_09:57\nمانده: 13,451,930",
        source = SOURCE_SMS_AUTO,
        otpBody = otp,
    )
}
