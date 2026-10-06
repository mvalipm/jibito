package ir.jibito.app.data.repository

import ir.jibito.app.util.JalaliMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class SpendTrendTest {

    private val mehr = JalaliMonth(1404, 7)
    private val shahrivar = JalaliMonth(1404, 6)
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `جمع هر ماه و مقایسه با همین موقع ماه قبل`() {
        val now = mehr.startMillis() + 10 * day
        val spends = listOf(
            shahrivar.startMillis() + 2 * day to 4_000_000L, // قبل از روز ۱۰ شهریور
            shahrivar.startMillis() + 20 * day to 9_000_000L, // بعد از روز ۱۰: در مقایسه نیست
            mehr.startMillis() + 1 * day to 3_000_000L,
            mehr.startMillis() + 9 * day to 2_000_000L,
        )
        val t = SpendTrend.compute(spends, mehr, count = 3, now = now)
        assertEquals(listOf(JalaliMonth(1404, 5), shahrivar, mehr), t.months.map { it.month })
        assertEquals(listOf(0L, 13_000_000L, 5_000_000L), t.months.map { it.spentRial })
        assertEquals(4_000_000L, t.lastMonthSameTimeRial)
        assertEquals(25, t.vsLastMonthPercent)
    }

    @Test
    fun `ماه گذشته مقایسه‌ی همین موقع ندارد و پایه‌ی کوچک درصد نمی‌دهد`() {
        val past = SpendTrend.compute(emptyList(), shahrivar, 6, now = mehr.startMillis() + day)
        assertNull(past.lastMonthSameTimeRial)
        assertNull(past.vsLastMonthPercent)

        val tiny = SpendTrend.compute(listOf(shahrivar.startMillis() to 50_000L), mehr, 2, now = mehr.startMillis() + day)
        assertNull(tiny.vsLastMonthPercent)
    }

    @Test
    fun `ماه جاری، پیش‌بینی آخر ماه و میانگین ماه‌های تمام‌شده`() {
        // مهر ۳۰ روز است؛ ۵ میلیون در ۱۰ روز ← ۱۵ میلیون آخر ماه
        val now = mehr.startMillis() + 10 * day
        val spends = listOf(
            shahrivar.startMillis() + 2 * day to 13_000_000L,
            mehr.startMillis() + 1 * day to 5_000_000L,
        )
        val t = SpendTrend.compute(spends, mehr, count = 3, now = now)
        assertTrue(t.isCurrent)
        assertEquals(15_000_000L, t.projectedRial)
        // مرداد خرجی ندارد و مهر هنوز تمام نشده: فقط شهریور
        assertEquals(13_000_000L, t.averageRial)

        val early = SpendTrend.compute(spends, mehr, count = 3, now = mehr.startMillis() + day)
        assertNull(early.projectedRial)

        val past = SpendTrend.compute(spends, shahrivar, count = 2, now = now)
        assertFalse(past.isCurrent)
        assertNull(past.projectedRial)
    }

    @Test
    fun `خرج یک‌باره در ستون هست ولی در میانگین، مقایسه و ریتم پیش‌بینی نه`() {
        val now = mehr.startMillis() + 10 * day
        val spends = listOf(
            shahrivar.startMillis() + 2 * day to 4_000_000L,
            mehr.startMillis() + 1 * day to 3_000_000L,
            mehr.startMillis() + 9 * day to 2_000_000L,
        )
        val oneOffs = listOf(
            shahrivar.startMillis() + 5 * day to 100_000_000_000L, // خرید خانه
            mehr.startMillis() + 3 * day to 50_000_000_000L,
        )
        val t = SpendTrend.compute(spends, mehr, count = 3, now = now, oneOffs = oneOffs)
        assertEquals(listOf(0L, 100_004_000_000L, 50_005_000_000L), t.months.map { it.spentRial })
        assertEquals(listOf(0L, 4_000_000L, 5_000_000L), t.months.map { it.routineRial })
        assertEquals(4_000_000L, t.lastMonthSameTimeRial)
        assertEquals(25, t.vsLastMonthPercent)
        assertEquals(4_000_000L, t.averageRial)
        // ریتم ۱۰ روز اول (۵ میلیون) سه برابر می‌شود؛ خرج یک‌باره فقط یک بار اضافه می‌شود
        assertEquals(50_015_000_000L, t.projectedRial)
    }
}
