package ir.jibito.app.data.repository

import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.CategorySum
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

    @Test
    fun `خرج یک‌باره در جمع ماه هست ولی از بودجه کم نمی‌شود`() {
        val home = CategoryEntity(id = 1, name = "خانه و خانواده", flowType = 2)
        val food = CategoryEntity(id = 2, name = "خوراک", flowType = 2)
        val s = BudgetRepositoryImpl.buildSummary(
            month = month,
            spendSums = listOf(
                // ۱۰۰ میلیارد ریال خرید خانه + ۵ میلیون خرج عادی خانه
                CategorySum(1, 100_005_000_000L, oneOffRial = 100_000_000_000L),
                CategorySum(2, 8_000_000L),
                CategorySum(null, 2_000_000L, oneOffRial = 1_000_000L),
            ),
            incomeSums = emptyList(),
            categories = listOf(home, food),
            budgets = mapOf(1L to 10_000_000L),
            overallBudgetRial = 30_000_000L,
        )
        assertEquals(100_015_000_000L, s.totalSpentRial)
        assertEquals(100_001_000_000L, s.oneOffRial)
        assertEquals(14_000_000L, s.budgetSpentRial)
        assertEquals(16_000_000L, s.overallRemainingRial)
        val h = s.categories.single { it.categoryId == 1L }
        assertEquals(100_005_000_000L, h.spentRial)
        assertEquals(5_000_000L, h.budgetSpentRial)
    }
}
