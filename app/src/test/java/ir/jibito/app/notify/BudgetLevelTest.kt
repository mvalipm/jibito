package ir.jibito.app.notify

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetLevelTest {

    @Test
    fun `سطح هشدار بودجه`() {
        assertEquals(0, BudgetLevel.of(spentRial = 0, budgetRial = 1_000_000))
        assertEquals(0, BudgetLevel.of(spentRial = 799_999, budgetRial = 1_000_000))
        assertEquals(80, BudgetLevel.of(spentRial = 800_000, budgetRial = 1_000_000))
        assertEquals(80, BudgetLevel.of(spentRial = 999_999, budgetRial = 1_000_000))
        assertEquals(100, BudgetLevel.of(spentRial = 1_000_000, budgetRial = 1_000_000))
        assertEquals(100, BudgetLevel.of(spentRial = 5_000_000, budgetRial = 1_000_000))
    }

    @Test
    fun `بدون بودجه هیچ‌وقت هشدار نمی‌دهد`() {
        assertEquals(0, BudgetLevel.of(spentRial = 9_000_000, budgetRial = 0))
    }
}
