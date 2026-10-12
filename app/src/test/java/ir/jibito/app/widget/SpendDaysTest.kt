package ir.jibito.app.widget

import ir.jibito.app.data.local.entity.DatedAmount
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SpendDaysTest {

    private lateinit var savedZone: TimeZone

    @Before
    fun zone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"))
    }

    @After
    fun restore() = TimeZone.setDefault(savedZone)

    private val hour = 60 * 60 * 1000L
    private val todayStart get() = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 12) }.timeInMillis

    private fun spend(daysAgo: Int, rial: Long, oneOff: Boolean = false) =
        DatedAmount(null, rial, SpendDays.dayStarts(todayStart, daysAgo + 1).first() + 10 * hour, oneOff)

    @Test
    fun dayStartsEndsWithToday() {
        val days = SpendDays.dayStarts(todayStart, 7)
        assertEquals(7, days.size)
        assertEquals(todayStart, days.last())
        assertEquals(6 * 24 * hour, days.last() - days.first())
    }

    @Test
    fun perDayBucketsEachSpendIntoItsDay() {
        val week = SpendDays.dayStarts(todayStart, 7)
        val rows = listOf(spend(0, 500), spend(0, 300), spend(2, 1_000), spend(6, 50), spend(7, 9_999))
        assertEquals(listOf(50L, 0, 0, 0, 1_000, 0, 800), SpendDays.perDay(rows, week, SpendDays.nextDay(todayStart)))
    }

    @Test
    fun typicalIsAverageOfPreviousDaysWithoutOneOffsOrToday() {
        val previous = SpendDays.dayStarts(todayStart, 31).dropLast(1)
        val rows = (1..30).map { spend(it, 1_000) } + spend(3, 1_000_000, oneOff = true) + spend(0, 50_000)
        assertEquals(1_000L, SpendDays.typical(rows, previous, todayStart, firstSpend = 0L))
    }

    @Test
    fun typicalCountsOnlyDaysSinceFirstSpend() {
        val previous = SpendDays.dayStarts(todayStart, 31).dropLast(1)
        // ده روز سابقه، روزی ۲۰۰۰ در پنج روزش ← میانگین ۱۰۰۰ (نه تقسیم بر ۳۰)
        val rows = listOf(10, 8, 6, 4, 2).map { spend(it, 2_000) }
        assertEquals(1_000L, SpendDays.typical(rows, previous, todayStart, firstSpend = rows.first().dateEpoch))
    }

    @Test
    fun typicalNeedsAWeekOfHistory() {
        val previous = SpendDays.dayStarts(todayStart, 31).dropLast(1)
        val rows = listOf(spend(3, 1_000))
        assertNull(SpendDays.typical(rows, previous, todayStart, firstSpend = rows.first().dateEpoch))
        assertNull(SpendDays.typical(emptyList(), previous, todayStart, firstSpend = null))
    }

    @Test
    fun versus() {
        assertEquals(SpendDays.Versus.More(17), SpendDays.versus(1_700, 1_000))
        assertEquals(SpendDays.Versus.Less(40), SpendDays.versus(600, 1_000))
        assertEquals(SpendDays.Versus.About, SpendDays.versus(1_100, 1_000))
        assertEquals(SpendDays.Versus.About, SpendDays.versus(900, 1_000))
    }
}
