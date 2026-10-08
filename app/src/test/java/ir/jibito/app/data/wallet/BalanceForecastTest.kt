package ir.jibito.app.data.wallet

import ir.jibito.app.util.JalaliMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class BalanceForecastTest {

    private val hour = 60L * 60 * 1000
    private val mehr = JalaliMonth(1405, 7) // ۳۰ روز
    private val shahrivar = JalaliMonth(1405, 6) // ۳۱ روز
    private lateinit var savedZone: TimeZone
    private var nextId = 1L

    @Before
    fun fixZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"))
    }

    @After
    fun restoreZone() = TimeZone.setDefault(savedZone)

    private fun at(month: JalaliMonth, day: Int, hourOfDay: Int = 12) = SalaryDetector.dayIn(month, day) + hourOfDay * hour

    private fun out(month: JalaliMonth, day: Int, amount: Long, oneOff: Boolean = false) =
        FlowRow(nextId++, 11, "1234", 2, amount, at(month, day, 10), null, oneOff)

    private val rent = PlannedPayment("اجاره", 170_000_000, 3)
    private val loan = PlannedPayment("قسط", 42_000_000, 20)

    /** شهریور: روزی ۱ میلیون ریال + اجاره‌ی ۳ شهریور؛ مهر: اجاره‌ی ۳ مهر */
    private fun history() = (1..31).map { out(shahrivar, it, 1_000_000) } +
        out(shahrivar, 3, 170_000_000) + out(mehr, 3, 170_000_000)

    @Test
    fun `الگوی ماه قبل، بدون پرداخت‌های ثابت، تا آخر ماه`() {
        val f = BalanceForecastCalc.compute(300_000_000, history(), listOf(rent, loan), salary = null, nowMillis = at(mehr, 15))!!
        assertEquals(ForecastBasis.PATTERN, f.basis)
        // ۱۵ مهر تا ۳۰ مهر: امروز + ۱۵ روز
        assertEquals(16, f.days.size)
        assertEquals(SalaryDetector.dayIn(mehr, 30), f.days.last())
        assertEquals(15_000_000L, f.routineRial)
        // اجاره‌ی مهر پرداخت شده؛ فقط قسط ۲۰ مهر
        assertEquals(listOf(UpcomingPayment("قسط", 42_000_000, SalaryDetector.dayIn(mehr, 20))), f.payments)
        assertEquals(300_000_000L - 15_000_000 - 42_000_000, f.endRial)
        assertTrue(f.enough)
        assertNull(f.salary)
    }

    @Test
    fun `روزی که پول تمام می‌شود`() {
        val f = BalanceForecastCalc.compute(50_000_000, history(), listOf(rent, loan), null, at(mehr, 15))!!
        // ۲۰ مهر: ۵۰ − ۵ − ۴۲ = ۳؛ ۲۳ مهر: صفر؛ ۲۴ مهر: منفی
        assertEquals(SalaryDetector.dayIn(mehr, 24), f.zeroDay)
        assertFalse(f.enough)
        assertEquals(-7_000_000L, f.endRial)
        assertEquals(f.endRial, f.lowRial)
    }

    @Test
    fun `تا شب قبل از حقوق`() {
        val salary = Salary(SalaryDetector.keyOf(11, null), 11, 480_000_000, dayOfMonth = 25)
        val f = BalanceForecastCalc.compute(300_000_000, history(), listOf(loan), salary, at(mehr, 15))!!
        assertEquals(SalaryDetector.dayIn(mehr, 25), f.paydayMillis)
        assertEquals(SalaryDetector.dayIn(mehr, 24), f.days.last())
        assertEquals(salary, f.salary)
    }

    @Test
    fun `قسطی که زودتر پرداخت شده دوباره کم نمی‌شود`() {
        val rows = history() + out(mehr, 14, 42_000_000)
        val f = BalanceForecastCalc.compute(300_000_000, rows, listOf(rent, loan), null, at(mehr, 15))!!
        assertTrue(f.payments.isEmpty())
    }

    @Test
    fun `خرج یک‌باره‌ی ماه قبل تکرار نمی‌شود`() {
        val rows = history() + out(shahrivar, 20, 900_000_000, oneOff = true)
        val f = BalanceForecastCalc.compute(300_000_000, rows, listOf(rent), null, at(mehr, 15))!!
        assertEquals(15_000_000L, f.routineRial)
    }

    @Test
    fun `بدون ماه قبل ریتم همین ماه، و سه روز اول هیچ`() {
        val rows = (1..10).map { out(mehr, it, 2_000_000) }
        val f = BalanceForecastCalc.compute(100_000_000, rows, emptyList(), null, at(mehr, 10))!!
        assertEquals(ForecastBasis.RHYTHM, f.basis)
        // ۲۰ روز مانده × روزی ۲ میلیون
        assertEquals(40_000_000L, f.routineRial)
        assertNull(BalanceForecastCalc.compute(100_000_000, rows.take(2), emptyList(), null, at(mehr, 2)))
    }

    @Test
    fun `فقط حساب‌هایی که در جمع کیف پول‌اند`() {
        val now = at(mehr, 15)
        val points = listOf(
            BalancePointRow(1, 11, "1234", 100, now - hour, 2, 1),
            BalancePointRow(2, 15, null, 100, now - hour, 2, 1),
            BalancePointRow(3, 40, "9", 100, now - hour, 2, 1),
        )
        val overview = BalanceHistory.overview(points, emptyList(), setOf(WalletExclusion.wholeBank(40)), DayGrid.lastDays(1, now))
        val rows = listOf(
            FlowRow(10, 11, "1234", 2, 5, now, null, false),
            FlowRow(11, 11, null, 2, 5, now, null, false),
            FlowRow(12, 15, "77", 2, 5, now, null, false),
            FlowRow(13, 40, "9", 2, 5, now, null, false),
        )
        assertEquals(listOf(10L, 12L), BalanceForecastCalc.walletRows(rows, overview, emptyList()).map { it.id })
    }
}
