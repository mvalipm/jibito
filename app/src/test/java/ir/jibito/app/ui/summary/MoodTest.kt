package ir.jibito.app.ui.summary

import ir.jibito.app.R
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.util.JalaliMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class MoodTest {

    private val month = JalaliMonth(1405, 7)
    /** ۱۰ روز از ماه گذشته (یک‌سوم ماه) */
    private val now = month.startMillis() + (month.endMillis() - month.startMillis()) / 3

    private fun summary(spentToman: Long, budgetToman: Long?) = MonthSummary(
        month = month,
        totalSpentRial = spentToman * 10,
        totalIncomeRial = 0,
        uncategorizedRial = 0,
        categories = emptyList(),
        incomeCategories = emptyList(),
        uncategorizedIncomeRial = 0,
        overallBudgetRial = budgetToman?.let { it * 10 },
    )

    @Test
    fun `بدون بودجه آروم است`() {
        assertEquals(Mood.CALM, moodOf(summary(5_000_000, null), now).mood)
        assertEquals(R.string.hero_no_spend, moodOf(summary(0, null), now).sentence)
    }

    @Test
    fun `طبق ریتم، آخر ماه اضافه میاری`() {
        val line = moodOf(summary(7_000_000, 30_000_000), now)
        assertEquals(Mood.CALM, line.mood)
        assertEquals(R.string.hero_calm, line.sentence)
    }

    @Test
    fun `تند رفتن یعنی یواش‌تر`() {
        val line = moodOf(summary(15_000_000, 30_000_000), now)
        assertEquals(Mood.WARN, line.mood)
        assertEquals(R.string.hero_warn_pace, line.sentence)
    }

    @Test
    fun `۸۰ درصد بودجه یعنی یواش‌تر`() {
        val line = moodOf(summary(26_400_000, 30_000_000), now)
        assertEquals(Mood.WARN, line.mood)
        assertEquals(R.string.hero_warn, line.sentence)
        assertEquals(88, line.args[0])
    }

    @Test
    fun `بیشتر از بودجه یعنی بیرون زد`() {
        assertEquals(Mood.OVER, moodOf(summary(33_600_000, 30_000_000), now).mood)
    }

    @Test
    fun `ماه گذشته با پس‌انداز`() {
        val line = moodOf(summary(20_000_000, 30_000_000), month.endMillis() + 1)
        assertEquals(Mood.CALM, line.mood)
        assertEquals(R.string.hero_past_calm, line.sentence)
    }
}
