package ir.jibito.app.data.category

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.repository.SOURCE_SMS_AUTO
import ir.jibito.app.util.JalaliMonth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** حدس دسته برای طرف حسابِ تازه: کلمه‌ی مشترک با فروشگاه‌های قبلی، و اجاره‌ی ماهانه‌ی کارت‌به‌کارت. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryLearningGuessTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private var smsId = 1L

    @Before
    fun setUp() {
        context.deleteDatabase(AppDatabase.NAME)
        db = AppDatabase.build(context)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(AppDatabase.NAME)
    }

    private suspend fun category(name: String, code: String? = null) =
        db.categoryDao().insert(CategoryEntity(name = name, icon = "•", colorHex = "#000000", flowType = 2, code = code))

    private suspend fun spend(merchant: String, categoryId: Long?, amount: Long = 1_000_000, date: Long = 1_000, auto: Boolean = false) {
        db.transactionFlowDao().insert(
            TransactionFlowEntity(
                smsId = smsId++,
                bankId = 1,
                flowType = 2,
                amount = amount,
                remainAfter = null,
                dateEpoch = date,
                merchant = merchant,
                suggestedCategory = null,
                isFailedPurchase = false,
                categoryId = categoryId,
                description = null,
                smsContent = "sms",
                source = SOURCE_SMS_AUTO,
                isAutoCategorized = auto,
            )
        )
    }

    /** وسط ماه شمسیِ [monthsAgo] ماه پیش */
    private fun midMonth(monthsAgo: Int) = JalaliMonth.current().plus(-monthsAgo).startMillis() + 14 * DAY + DAY / 2

    private suspend fun monthlyTransfers(card: String, amount: Long, categoryId: Long? = null) {
        (1..4).forEach { spend(card, categoryId, amount = amount, date = midMonth(it)) }
    }

    private fun learning() = CategoryLearning(db)

    @Test
    fun freshMerchantSharingAWordGetsASuggestionNeverAuto() = runBlocking {
        val bags = category("کیف")
        spend("زنبیل آرمان", bags)
        spend("زنبیل مهر", bags)
        assertNull(CategorySuggester.suggest("زنبیل دانا"))

        val decision = learning().decide("زنبیل دانا", 2)!!
        assertEquals(bags, decision.categoryId)
        assertFalse(decision.auto)
    }

    @Test
    fun theUsersExactChoicesWinOverTheGuess() = runBlocking {
        val bags = category("کیف")
        val hats = category("کلاه")
        spend("زنبیل آرمان", bags)
        spend("زنبیل مهر", bags)
        spend("زنبیل دانا", hats)

        assertEquals(hats, learning().decide("زنبیل دانا", 2)!!.categoryId)
    }

    @Test
    fun categoriesTheAppPickedItselfAreNotLearnedFrom() = runBlocking {
        val bags = category("کیف")
        spend("زنبیل آرمان", bags, auto = true)
        spend("زنبیل مهر", bags, auto = true)

        assertNull(learning().decide("زنبیل دانا", 2))
    }

    @Test
    fun theFixedWordListStaysInChargeOfMerchantsItKnows() = runBlocking {
        val restaurant = category("رستوران")
        spend("کافه آرتا", restaurant)
        spend("کافه لمیز", restaurant)

        assertNull(learning().decide("کافه نادری", 2))
    }

    @Test
    fun aBigMonthlyCardTransferIsGuessedAsRent() = runBlocking {
        val rent = category("اجاره و مسکن", code = "home.rent")
        monthlyTransfers("کارت/حساب …1234", 60_000_000)

        val decision = learning().decide("کارت/حساب …1234", 2)!!
        assertEquals(rent, decision.categoryId)
        assertFalse(decision.auto)
    }

    @Test
    fun aCardTransferWithoutAMonthlyPatternIsNotGuessed() = runBlocking {
        category("اجاره و مسکن", code = "home.rent")
        listOf(60_000_000L, 5_000_000L, 90_000_000L, 20_000_000L).forEachIndexed { i, amount ->
            spend("کارت/حساب …1234", null, amount = amount, date = midMonth(i + 1))
        }

        assertNull(learning().decide("کارت/حساب …1234", 2))
    }

    @Test
    fun aSmallMonthlyCardTransferIsNotGuessedAsRent() = runBlocking {
        category("اجاره و مسکن", code = "home.rent")
        monthlyTransfers("کارت/حساب …1234", 3_000_000)

        assertNull(learning().decide("کارت/حساب …1234", 2))
    }

    @Test
    fun whatTheUserChoseForTheCardBeatsTheRentGuess() = runBlocking {
        category("اجاره و مسکن", code = "home.rent")
        val lend = category("قرض دادم")
        monthlyTransfers("کارت/حساب …1234", 60_000_000, categoryId = lend)

        assertEquals(lend, learning().decide("کارت/حساب …1234", 2)!!.categoryId)
    }

    @Test
    fun noRentGuessWhenTheRentCategoryIsArchived() = runBlocking {
        val rent = category("اجاره و مسکن", code = "home.rent")
        db.categoryDao().archive(rent)
        monthlyTransfers("کارت/حساب …1234", 60_000_000)

        assertNull(learning().decide("کارت/حساب …1234", 2))
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
