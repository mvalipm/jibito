package ir.jibito.app.data.repository

import ir.jibito.app.util.JalaliMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class SpendCurveTest {

    private val mehr = JalaliMonth(1405, 7) // ۳۰ روز
    private val shahrivar = JalaliMonth(1405, 6) // ۳۱ روز
    private val day = 24L * 60 * 60 * 1000
    private val noon = 12L * 60 * 60 * 1000
    private lateinit var savedZone: TimeZone

    @Before
    fun fixZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"))
    }

    @After
    fun restoreZone() = TimeZone.setDefault(savedZone)

    private fun on(month: JalaliMonth, dayOfMonth: Int, rial: Long) = month.startMillis() + (dayOfMonth - 1) * day + noon to rial

    @Test
    fun `منحنی تجمعی ماه جاری تا امروز و کل ماه قبل`() {
        val now = mehr.startMillis() + 4 * day + noon // ۵ مهر
        val spends = listOf(
            on(shahrivar, 1, 10_000_000L),
            on(shahrivar, 31, 5_000_000L),
            on(mehr, 1, 2_000_000L),
            on(mehr, 3, 3_000_000L),
            on(mehr, 3, 1_000_000L),
        )
        val c = SpendCurve.compute(spends, mehr, now, budgetRial = null)
        assertEquals(30, c.days)
        assertTrue(c.isCurrent)
        assertEquals(listOf(2_000_000L, 2_000_000L, 6_000_000L, 6_000_000L, 6_000_000L), c.cumulative)
        assertEquals(31, c.previous.size)
        assertEquals(10_000_000L, c.previous.first())
        assertEquals(15_000_000L, c.previous.last())
        assertEquals(10_000_000L, c.previousAt(4))
        // روز ۳۱ ندارد: آخر ماه قبل
        assertEquals(15_000_000L, c.previousAt(40))
    }

    @Test
    fun `پیش‌بینی با الگوی ماه قبل`() {
        val now = mehr.startMillis() + 9 * day + noon // ۱۰ مهر
        val spends = listOf(
            on(shahrivar, 2, 10_000_000L),
            on(shahrivar, 20, 4_000_000L),
            on(shahrivar, 30, 6_000_000L),
            on(mehr, 5, 8_000_000L),
        )
        val c = SpendCurve.compute(spends, mehr, now, budgetRial = 30_000_000L)
        assertTrue(c.projectionFromPattern)
        // اولین نقطه همان امروز است و تا روز ۳۰ ادامه دارد
        assertEquals(21, c.projection.size)
        assertEquals(8_000_000L, c.projection.first())
        // از ۱۰ تا ۳۰ شهریور ۱۰ میلیون خرج شد (روز ۳۱ در مهر ۳۰روزه نیست)
        assertEquals(18_000_000L, c.projectedEndRial)
        assertEquals(12_000_000L, c.valueAt(19))
        assertEquals(30_000_000L, c.budgetRial)
    }

    @Test
    fun `بدون الگوی ماه قبل، ریتم همین ماه؛ سه روز اول بی‌پیش‌بینی`() {
        val spends = listOf(on(mehr, 1, 3_000_000L))
        val c = SpendCurve.compute(spends, mehr, mehr.startMillis() + 2 * day + noon, null) // ۳ مهر
        assertFalse(c.projectionFromPattern)
        assertEquals(30_000_000L, c.projectedEndRial)

        val early = SpendCurve.compute(spends, mehr, mehr.startMillis() + day + noon, null) // ۲ مهر
        assertTrue(early.projection.isEmpty())
    }

    @Test
    fun `ماه تمام‌شده همه‌ی روزها را دارد و پیش‌بینی ندارد`() {
        val c = SpendCurve.compute(listOf(on(shahrivar, 31, 1_000_000L)), shahrivar, mehr.startMillis() + day, null)
        assertFalse(c.isCurrent)
        assertEquals(31, c.cumulative.size)
        assertEquals(1_000_000L, c.spentRial)
        assertTrue(c.projection.isEmpty())
    }

    @Test
    fun `خرج یک‌باره در منحنی این ماه هست ولی در ماه قبل و مقایسه نه`() {
        val now = mehr.startMillis() + 4 * day + noon // ۵ مهر
        val spends = listOf(on(shahrivar, 1, 10_000_000L), on(mehr, 1, 2_000_000L))
        val oneOffs = listOf(on(shahrivar, 10, 1_000_000_000_000L), on(mehr, 3, 500_000_000L))
        val c = SpendCurve.compute(spends, mehr, now, budgetRial = null, oneOffs = oneOffs)
        assertEquals(listOf(2_000_000L, 2_000_000L, 502_000_000L, 502_000_000L, 502_000_000L), c.cumulative)
        assertEquals(10_000_000L, c.previous.last())
        assertEquals(500_000_000L, c.oneOffRial)
        assertEquals(2_000_000L, c.routineAt(0))
        assertEquals(2_000_000L, c.routineAt(4))
        // الگوی ماه قبل (بدون خرید یک‌باره‌اش) بعد از روز ۵ خرجی نداشت
        assertTrue(c.projectionFromPattern)
        assertEquals(502_000_000L, c.projectedEndRial)
        assertEquals(2_000_000L, c.routineAt(29))
        // خط نمودار بدون خرج یک‌باره
        assertEquals(List(5) { 2_000_000L }, c.routineCumulative)
        assertEquals(c.routineCumulative.last(), c.routineProjection.first())
        assertEquals(2_000_000L, c.routineProjection.last())
    }

    @Test
    fun `ریتم پیش‌بینی خرج یک‌باره را تکرار نمی‌کند`() {
        val now = mehr.startMillis() + 4 * day + noon // ۵ مهر، ماه قبل بی‌خرج
        val c = SpendCurve.compute(
            listOf(on(mehr, 1, 2_000_000L)), mehr, now, budgetRial = null,
            oneOffs = listOf(on(mehr, 2, 100_000_000L)),
        )
        assertFalse(c.projectionFromPattern)
        assertEquals(112_000_000L, c.projectedEndRial)
    }

    @Test
    fun `بدون خرج یک‌باره چیزی عوض نمی‌شود`() {
        val now = mehr.startMillis() + 4 * day + noon
        val c = SpendCurve.compute(listOf(on(mehr, 1, 2_000_000L)), mehr, now, budgetRial = null)
        assertEquals(0L, c.oneOffRial)
        assertEquals(c.valueAt(2), c.routineAt(2))
        assertEquals(c.cumulative, c.routineCumulative)
        assertEquals(c.projection, c.routineProjection)
    }
}
