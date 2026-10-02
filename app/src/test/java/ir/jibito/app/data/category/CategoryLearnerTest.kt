package ir.jibito.app.data.category

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
