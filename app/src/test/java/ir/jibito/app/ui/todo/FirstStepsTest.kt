package ir.jibito.app.ui.todo

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.domain.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstStepsTest {

    private fun tx(id: Long, merchant: String?, categoryId: Long? = null, auto: Boolean = false, date: Long = id) = Transaction(
        id = id, bank = null, body = "", dateMillis = date,
        transaction = ParsedTransaction(FlowType.WITHDRAWAL, 1), merchant = merchant, suggestedCategory = null,
        isFailedPurchase = false, categoryId = categoryId, categoryName = null, categoryIcon = null, isAutoCategorized = auto,
    )

    @Test
    fun `پرتکرارترین فروشگاه‌ها اول، فقط سه تا`() {
        val list = List(5) { tx(it + 1L, "اسنپ") } + List(3) { tx(it + 10L, "کافه لمیز") } +
            List(4) { tx(it + 20L, "افق کوروش") } + tx(30, "نانوایی") + tx(31, null)
        val c = merchantCandidates(list)
        assertEquals(listOf("اسنپ", "افق کوروش", "کافه لمیز"), c.map { it.name })
        assertEquals(5, c[0].uncategorized)
        assertTrue(c.none { it.taught })
    }

    @Test
    fun `یادداده می‌ماند و تیک می‌خورد؛ دسته‌ی خودکار یاد دادن حساب نمی‌شود`() {
        val list = listOf(
            tx(1, "اسنپ", categoryId = 5), tx(2, "اسنپ"), tx(3, "اسنپ"),
            // همه دسته دارند ولی خودکار: نه چیزی برای یاد دادن هست، نه کاربر یادش داده
            tx(4, "دیجی‌کالا", categoryId = 7, auto = true), tx(5, "دیجی‌کالا", categoryId = 7, auto = true),
            tx(6, "کافه لمیز"),
        )
        val c = merchantCandidates(list)
        assertEquals(listOf("اسنپ", "کافه لمیز"), c.map { it.name })
        assertTrue(c[0].taught)
        assertFalse(c[1].taught)
    }

    @Test
    fun `همه‌ی خرج‌های یک فروشگاه دسته گرفت، باز هم با تیک می‌ماند تا فهرست جابه‌جا نشود`() {
        val c = merchantCandidates(listOf(tx(1, "اسنپ", categoryId = 5), tx(2, "اسنپ", categoryId = 5, auto = true)))
        assertEquals(1, c.size)
        assertTrue(c[0].taught)
        assertEquals(0, c[0].uncategorized)
    }

    @Test
    fun `لمس فروشگاه تازه‌ترین خرج بی‌دسته‌اش را باز می‌کند`() {
        val list = listOf(tx(1, "اسنپ", date = 100), tx(2, "اسنپ", date = 300), tx(3, "اسنپ", categoryId = 5, date = 500))
        assertEquals(2L, merchantCandidates(list).single().openId)
    }

    @Test
    fun `بودجه‌ی پیشنهادی تا دو رقم اول گرد می‌شود`() {
        // ۲۲٫۴ میلیون تومان ← ۲۲ میلیون
        assertEquals(220_000_000L, suggestedBudgetRial(224_000_000L))
        // ۲۲٫۶ میلیون ← ۲۳ میلیون
        assertEquals(230_000_000L, suggestedBudgetRial(226_000_000L))
        // ۹۵۶ هزار ← ۹۶۰ هزار
        assertEquals(9_600_000L, suggestedBudgetRial(9_560_000L))
        // ۸٫۷۴ میلیون ← ۸٫۷ میلیون
        assertEquals(87_000_000L, suggestedBudgetRial(87_400_000L))
        assertNull(suggestedBudgetRial(0))
    }

    @Test
    fun `هفت روز باز کردن اپ بالای خلاصه، بعد در کارها`() {
        var state = Long.MIN_VALUE to 0
        assertTrue(FirstStepsPlacement.onSummary(state.second))
        // روز اول، دو بار باز شد: یک روز
        state = FirstStepsPlacement.recordOpen(state.first, state.second, 20_000)
        state = FirstStepsPlacement.recordOpen(state.first, state.second, 20_000)
        assertEquals(1, state.second)
        // دو هفته نیامد: روزهای نیامده شمرده نمی‌شوند
        state = FirstStepsPlacement.recordOpen(state.first, state.second, 20_014)
        assertEquals(2, state.second)
        for (day in 20_015L..20_019L) state = FirstStepsPlacement.recordOpen(state.first, state.second, day)
        assertEquals(7, state.second)
        assertTrue(FirstStepsPlacement.onSummary(state.second))
        state = FirstStepsPlacement.recordOpen(state.first, state.second, 20_020)
        assertFalse(FirstStepsPlacement.onSummary(state.second))
    }
}
