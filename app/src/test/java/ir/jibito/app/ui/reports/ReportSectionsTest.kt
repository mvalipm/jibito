package ir.jibito.app.ui.reports

import ir.jibito.app.data.repository.Insight
import ir.jibito.app.ui.reports.ReportSection.BALANCE
import ir.jibito.app.ui.reports.ReportSection.INSIGHTS
import ir.jibito.app.ui.reports.ReportSection.SPEND
import ir.jibito.app.util.JalaliMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSectionsTest {

    @Test
    fun `قبل از ۲۰ بار، نمودار خرج باز است`() {
        assertEquals(SPEND, ReportSections.favorite(emptyMap()))
        assertEquals(SPEND, ReportSections.favorite(mapOf(BALANCE to 19)))
    }

    @Test
    fun `بیش از دو برابر بقیه، کارت باز موقع ورود می‌شود`() {
        assertEquals(BALANCE, ReportSections.favorite(mapOf(BALANCE to 20)))
        assertEquals(BALANCE, ReportSections.favorite(mapOf(SPEND to 4, BALANCE to 13, INSIGHTS to 6)))
        assertEquals(INSIGHTS, ReportSections.favorite(mapOf(SPEND to 3, BALANCE to 2, INSIGHTS to 15)))
    }

    @Test
    fun `دو برابرِ یکی از بقیه کافی نیست`() {
        // ۱۴ دقیقاً دو برابر ۷ است، «بیش از» نیست
        assertEquals(SPEND, ReportSections.favorite(mapOf(SPEND to 2, BALANCE to 14, INSIGHTS to 7)))
        assertEquals(SPEND, ReportSections.favorite(mapOf(SPEND to 1, BALANCE to 11, INSIGHTS to 10)))
    }

    @Test
    fun `نکته با عدد تازه، تازه نیست؛ نکته‌ی دیگر تازه است`() {
        val month = JalaliMonth(1405, 7)
        val seen = setOf(
            ReportSections.key(Insight.CategoryChange(1, "رستوران", "🍽", null, 6_200_000L, 40)),
            ReportSections.key(Insight.WeekdayPeak(java.util.Calendar.FRIDAY, 2.03)),
        )
        assertFalse(
            ReportSections.hasNew(
                listOf(Insight.CategoryChange(1, "رستوران", "🍽", null, 8_000_000L, 55), Insight.WeekdayPeak(java.util.Calendar.FRIDAY, 2.4)),
                seen,
            )
        )
        assertTrue(ReportSections.hasNew(listOf(Insight.CategoryChange(1, "رستوران", "🍽", null, -3_000_000L, -20)), seen))
        assertTrue(ReportSections.hasNew(listOf(Insight.BusiestDay(month, 6, 22_000_000L, 3)), seen))
        assertFalse(ReportSections.hasNew(emptyList(), seen))
    }
}
