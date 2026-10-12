package ir.jibito.app.ui.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetSuggestionTest {

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
}
