package ir.jibito.app.data.repository

import ir.jibito.app.util.JalaliMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthSummaryTest {

    private val day = 24L * 60 * 60 * 1000
    private val month = JalaliMonth(1405, 7) // مهر: ۳۰ روز

    private fun summary(spent: Long, budget: Long?) = MonthSummary(
        month = month,
        totalSpentRial = spent,
        totalIncomeRial = 0,
        uncategorizedRial = 0,
        categories = emptyList(),
        incomeCategories = emptyList(),
        uncategorizedIncomeRial = 0,
        overallBudgetRial = budget,
    )

    @Test
    fun `روزی چقدر - باقی‌مانده تقسیم بر روزهای مانده با امروز`() {
        // ۱۰ روز (کامل) از ماه مانده، ۱۰ میلیون ریال مانده ← روزی یک میلیون ریال
        val now = month.endMillis() - 10 * day
        assertEquals(1_000_000L, summary(spent = 20_000_000, budget = 30_000_000).dailyAllowanceRial(now))
        // وسط روز: آن روز هم حساب می‌شود (۱۰ روز و نصفی ← ۱۱ روز)
        assertEquals(10_000_000L / 11, summary(20_000_000, 30_000_000).dailyAllowanceRial(now - day / 2))
    }

    @Test
    fun `بدون بودجه، بودجه‌ی تمام‌شده، یا ماه دیگر ← چیزی نشان داده نمی‌شود`() {
        val now = month.startMillis() + day
        assertNull(summary(20_000_000, null).dailyAllowanceRial(now))
        assertNull(summary(30_000_000, 30_000_000).dailyAllowanceRial(now))
        assertNull(summary(10_000_000, 30_000_000).dailyAllowanceRial(month.endMillis() + day))
    }

    @Test
    fun `باقی‌مانده‌ی منفی یعنی بیشتر از بودجه`() {
        assertEquals(-5_000_000L, summary(35_000_000, 30_000_000).overallRemainingRial)
    }

    @Test
    fun `سرعت خرج - نصف ماه گذشته و ۶۰٪ بودجه خرج شده ← ۱۰٪ تندتر`() {
        val mid = month.startMillis() + (month.endMillis() - month.startMillis()) / 2
        val pace = summary(spent = 18_000_000, budget = 30_000_000).paceDelta(mid)!!
        assertEquals(0.10f, pace, 0.001f)
        assertNull(summary(18_000_000, null).paceDelta(mid))
        assertNull(summary(18_000_000, 30_000_000).paceDelta(month.endMillis() + day))
    }
}
