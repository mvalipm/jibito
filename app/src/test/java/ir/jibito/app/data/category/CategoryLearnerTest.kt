package ir.jibito.app.data.category

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryLearnerTest {

    private val market = 2L
    private val restaurant = 3L

    @Test
    fun `بدون سابقه ← هیچ`() {
        assertNull(CategoryLearner.decide(emptyList(), "افق کوروش"))
    }

    @Test
    fun `یک و دو تأیید ← فقط پیشنهاد`() {
        assertEquals(LearnedDecision(market, auto = false), CategoryLearner.decide(listOf(market), "افق کوروش"))
        assertEquals(LearnedDecision(market, auto = false), CategoryLearner.decide(listOf(market, market), "افق کوروش"))
    }

    @Test
    fun `سه تأیید پشت سر هم ← خودکار`() {
        assertEquals(LearnedDecision(market, auto = true), CategoryLearner.decide(listOf(market, market, market), "افق کوروش"))
    }

    @Test
    fun `مثال خطادار سند - یک اصلاح وسطش شمارش را می‌شکند`() {
        // تازه‌ترین اول: سوپرمارکت، سوپرمارکت، رستوران(اصلاح)، سوپرمارکت، سوپرمارکت
        val choices = listOf(market, market, restaurant, market, market)
        assertEquals(LearnedDecision(market, auto = false), CategoryLearner.decide(choices, "افق کوروش"))
    }

    @Test
    fun `آخرین انتخاب متفاوت ← همان پیشنهاد می‌شود`() {
        assertEquals(LearnedDecision(restaurant, auto = false), CategoryLearner.decide(listOf(restaurant, market, market, market), "x"))
    }

    @Test
    fun `انتقال به کارت یک شخص هیچ‌وقت خودکار نمی‌شود`() {
        assertEquals(LearnedDecision(market, auto = false), CategoryLearner.decide(List(10) { market }, "کارت/حساب …1234"))
    }

    @Test
    fun `اسم شرکت پرداخت یا درگاه هیچ‌وقت خودکار نمی‌شود`() {
        for (name in listOf("به پرداخت ملت", "شرکت پرداخت الکترونیک سامان", "شاپرک", "پایانه", "زرین‌پال", "IPG Saman", "خرید اینترنتی")) {
            assertEquals(name, LearnedDecision(market, auto = false), CategoryLearner.decide(List(10) { market }, name))
        }
    }

    @Test
    fun `اسم فروشگاه واقعی اسم عمومی نیست`() {
        for (name in listOf("افق کوروش", "اسنپ", "رستوران شاندیز", "دیجی کالا", "کافه لمیز")) {
            assertFalse(name, CategoryLearner.isGenericMerchant(name))
        }
        assertTrue(CategoryLearner.isGenericMerchant("آسان‌پرداخت"))
        assertTrue(CategoryLearner.isGenericMerchant("ایران کیش"))
        assertTrue(CategoryLearner.isGenericMerchant("پوز"))
        assertTrue(CategoryLearner.isGenericMerchant("  "))
    }

    @Test
    fun `طرف حسابی که قبلاً دسته‌ی دیگری هم گرفته شاهد بیشتری لازم دارد`() {
        // تازه‌ترین اول: سه بار سوپرمارکت، ولی قبلش رستوران ← هنوز فقط پیشنهاد
        assertEquals(LearnedDecision(market, auto = false), CategoryLearner.decide(listOf(market, market, market, restaurant), "فروشگاه زنجیره‌ای"))
        val five = List(CategoryLearner.AMBIGUOUS_THRESHOLD) { market } + restaurant
        assertEquals(LearnedDecision(market, auto = true), CategoryLearner.decide(five, "فروشگاه زنجیره‌ای"))
    }

    @Test
    fun `انتخاب‌های خیلی قدیمی‌تر از پنجره شمرده نمی‌شوند`() {
        val choices = List(CategoryLearner.LOOKBACK) { market } + restaurant
        assertEquals(LearnedDecision(market, auto = true), CategoryLearner.decide(choices, "افق کوروش"))
    }
}
