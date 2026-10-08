package ir.jibito.app.ui.summary

import ir.jibito.app.R
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.data.wallet.BalanceForecast
import ir.jibito.app.data.wallet.ForecastBasis
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

    /** پیش‌بینی موجودی: آخرین روز افق منفی یعنی کم می‌آید */
    private fun forecast(enough: Boolean, payday: Long? = null) = BalanceForecast(
        days = listOf(now, now + 86_400_000L),
        values = listOf(1_000_000L, if (enough) 500_000L else -500_000L),
        routineRial = 0,
        payments = emptyList(),
        salary = null,
        paydayMillis = payday,
        basis = ForecastBasis.RHYTHM,
    )

    @Test
    fun `کمی تند رفتن یعنی یواش‌تر با لحن نرم`() {
        // ۱۱ از ۳۰ در یک‌سوم ماه ← آخر ماه حدود ۳۳ (۱۱۰٪)
        val line = moodOf(summary(11_000_000, 30_000_000), now)
        assertEquals(Mood.WARN, line.mood)
        assertEquals(R.string.hero_warn_pace, line.sentence)
    }

    @Test
    fun `تند رفتن لحن تندتر دارد`() {
        // ۱۵ از ۳۰ ← حدود ۴۵ (۱۵۰٪)
        val line = moodOf(summary(15_000_000, 30_000_000), now)
        assertEquals(Mood.WARN, line.mood)
        assertEquals(R.string.hero_warn_pace_fast, line.sentence)
    }

    @Test
    fun `دو برابر بودجه را با عدد می‌گوید`() {
        // ۲۰ از ۳۰ ← حدود ۶۰ (۲۰۰٪)
        val line = moodOf(summary(20_000_000, 30_000_000), now)
        assertEquals(Mood.WARN, line.mood)
        assertEquals(R.string.hero_warn_pace_far, line.sentence)
        assertEquals(2, line.args[1])
    }

    @Test
    fun `موجودی کم بیاید، بودجه‌ی آروم هم یواش‌تر می‌شود`() {
        val month = moodOf(summary(7_000_000, 30_000_000), now, forecast(enough = false))
        assertEquals(Mood.WARN, month.mood)
        assertEquals(R.string.hero_balance_short_month, month.sentence)
        val salary = moodOf(summary(7_000_000, 30_000_000), now, forecast(enough = false, payday = now + 5 * 86_400_000L))
        assertEquals(R.string.hero_balance_short_salary, salary.sentence)
    }

    @Test
    fun `موجودی کافی حال جیب را عوض نمی‌کند`() {
        val line = moodOf(summary(7_000_000, 30_000_000), now, forecast(enough = true))
        assertEquals(Mood.CALM, line.mood)
        assertEquals(R.string.hero_calm, line.sentence)
    }

    @Test
    fun `بی‌بودجه با موجودی کم، دعوت به سقف می‌ماند ولی رنگ یواش‌تر است`() {
        val line = moodOf(summary(7_000_000, null), now, forecast(enough = false))
        assertEquals(Mood.WARN, line.mood)
        assertEquals(R.string.hero_no_budget, line.sentence)
    }

    @Test
    fun `بیرون زدن از بودجه بر کم آمدن موجودی مقدم است`() {
        assertEquals(Mood.OVER, moodOf(summary(33_600_000, 30_000_000), now, forecast(enough = false)).mood)
    }

    @Test
    fun `پیش‌بینی موجودی فقط برای ماه جاری است`() {
        val line = moodOf(summary(20_000_000, 30_000_000), month.endMillis() + 1, forecast(enough = false))
        assertEquals(Mood.CALM, line.mood)
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
