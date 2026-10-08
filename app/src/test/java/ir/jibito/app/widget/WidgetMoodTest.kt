package ir.jibito.app.widget

import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.widget.SpendWidget.Companion.Mood
import ir.jibito.app.widget.SpendWidget.Companion.moodOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** حال جیب ویجت هم‌نظر با سرصفحه‌ی خلاصه: کم آمدن موجودی هم «یواش‌تر» است */
class WidgetMoodTest {

    private val month = JalaliMonth(1405, 7)
    /** یک‌سوم ماه گذشته */
    private val now = month.startMillis() + (month.endMillis() - month.startMillis()) / 3

    private fun numbers(spentToman: Long, budgetToman: Long?, short: Boolean = false) =
        SpendWidget.Numbers(
            todayRial = 0,
            monthRial = spentToman * 10,
            overallBudgetRial = budgetToman?.let { it * 10 },
            balanceShort = short,
        )

    @Test
    fun `بودجه‌ی آروم با موجودی کافی آروم است`() {
        assertEquals(Mood.CALM, moodOf(numbers(5_000_000, 30_000_000), now))
    }

    @Test
    fun `موجودی کم بیاید، ویجت یواش‌تر است`() {
        assertEquals(Mood.WARN, moodOf(numbers(5_000_000, 30_000_000, short = true), now))
    }

    @Test
    fun `بی‌بودجه بی‌رنگ است مگر موجودی کم بیاید`() {
        assertEquals(Mood.NONE, moodOf(numbers(5_000_000, null), now))
        assertEquals(Mood.WARN, moodOf(numbers(5_000_000, null, short = true), now))
    }

    @Test
    fun `بیرون زدن از بودجه مقدم است`() {
        assertEquals(Mood.OVER, moodOf(numbers(31_000_000, 30_000_000, short = true), now))
    }
}
