package ir.jibito.app.data.wallet

import ir.jibito.app.util.JalaliMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class SalaryDetectorTest {

    private val hour = 60L * 60 * 1000
    private val mehr = JalaliMonth(1405, 7)
    private lateinit var savedZone: TimeZone
    private var nextId = 1L

    @Before
    fun fixZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"))
    }

    @After
    fun restoreZone() = TimeZone.setDefault(savedZone)

    private fun at(month: JalaliMonth, day: Int, hourOfDay: Int = 10) = SalaryDetector.dayIn(month, day) + hourOfDay * hour

    private fun deposit(month: JalaliMonth, day: Int, amount: Long, bank: Int = 11, merchant: String? = null) =
        FlowRow(nextId++, bank, "1234", 1, amount, at(month, day), merchant, isOneOff = false)

    @Test
    fun `واریز بزرگ و منظم هر ماه حقوق است`() {
        val rows = (-4..0).map { deposit(mehr.plus(it), 1, 480_000_000L + it * 1_000_000L) } +
            // واریزهای کوچک و نامنظم
            listOf(deposit(mehr, 5, 3_000_000), deposit(mehr.plus(-1), 12, 25_000_000), deposit(mehr.plus(-2), 20, 90_000_000))
        val found = SalaryDetector.detect(rows, emptySet(), at(mehr, 15))
        assertEquals(1, found.size)
        assertEquals(1, found[0].dayOfMonth)
        assertEquals(5, found[0].months)
        assertEquals(478_000_000L, found[0].amountRial)
        assertEquals(SalaryDetector.keyOf(11, null), found[0].key)
    }

    @Test
    fun `حقوق آخر یا اول ماه هم یک روز حساب می‌شود`() {
        val days = listOf(30, 1, 29, 2)
        val rows = days.mapIndexed { i, d -> deposit(mehr.plus(i - 3), d, 300_000_000) }
        val found = SalaryDetector.detect(rows, emptySet(), at(mehr, 15))
        assertEquals(1, found.size)
        assertTrue(found[0].dayOfMonth in listOf(29, 30, 1, 2))
    }

    @Test
    fun `کمتر از سه ماه، روز پراکنده، قدیمی یا ردشده حقوق نیست`() {
        val now = at(mehr, 15)
        val twoMonths = listOf(deposit(mehr, 1, 300_000_000), deposit(mehr.plus(-1), 1, 300_000_000))
        assertTrue(SalaryDetector.detect(twoMonths, emptySet(), now).isEmpty())
        val scattered = listOf(1, 10, 20, 5).mapIndexed { i, d -> deposit(mehr.plus(i - 3), d, 300_000_000) }
        assertTrue(SalaryDetector.detect(scattered, emptySet(), now).isEmpty())
        val stopped = (-5..-2).map { deposit(mehr.plus(it), 1, 300_000_000) }
        assertTrue(SalaryDetector.detect(stopped, emptySet(), now).isEmpty())
        val regular = (-3..0).map { deposit(mehr.plus(it), 1, 300_000_000) }
        assertTrue(SalaryDetector.detect(regular, setOf(SalaryDetector.keyOf(11, null)), now).isEmpty())
    }

    @Test
    fun `حقوق بعدی`() {
        val salary = Salary(SalaryDetector.keyOf(11, null), 11, 480_000_000, dayOfMonth = 1)
        val aban = mehr.plus(1)
        val receivedMehr = listOf(deposit(mehr, 1, 480_000_000))
        // ۱۵ مهر، حقوق مهر آمده ← ۱ آبان
        assertEquals(SalaryDetector.dayIn(aban, 1), SalaryDetector.nextPayday(salary, receivedMehr, at(mehr, 15)))
        // ۲۸ مهر، حقوق آبان زودتر آمده ← ۱ آذر
        val early = receivedMehr + deposit(mehr, 27, 470_000_000)
        assertEquals(SalaryDetector.dayIn(aban.plus(1), 1), SalaryDetector.nextPayday(salary, early, at(mehr, 28)))
        // ۲۸ مهر، هنوز نیامده ← ۱ آبان
        assertEquals(SalaryDetector.dayIn(aban, 1), SalaryDetector.nextPayday(salary, receivedMehr, at(mehr, 28)))
        // روز ۲۵، امروز ۲۶ و نیامده (دیر کرده) ← ۲۵ ماه بعد
        val on25 = salary.copy(dayOfMonth = 25)
        assertEquals(SalaryDetector.dayIn(aban, 25), SalaryDetector.nextPayday(on25, emptyList(), at(mehr, 26)))
        // روز ۲۵، امروز ۲۰ ← همین ماه
        assertEquals(SalaryDetector.dayIn(mehr, 25), SalaryDetector.nextPayday(on25, emptyList(), at(mehr, 20)))
    }
}
