package ir.jibito.app.data.recurring

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.Jalali
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RecurringDetectorTest {

    private lateinit var savedZone: TimeZone

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"))
    }

    @After
    fun tearDown() = TimeZone.setDefault(savedZone)

    /** تاریخ شمسی ← میلی‌ثانیه (ظهر) */
    private fun j(y: Int, m: Int, d: Int): Long {
        val (gy, gm, gd) = Jalali.toGregorian(y, m, d)
        return Calendar.getInstance().apply { clear(); set(gy, gm - 1, gd, 12, 0) }.timeInMillis
    }

    private var id = 1L
    private fun pay(date: Long, merchant: String, rial: Long) = Transaction(
        id = id++, bank = null, body = "", dateMillis = date, transaction = ParsedTransaction(FlowType.WITHDRAWAL, rial),
        merchant = merchant, suggestedCategory = null, isFailedPurchase = false, categoryId = null,
        categoryName = null, categoryIcon = null, isAutoCategorized = false,
    )

    private val now = j(1404, 7, 10)

    @Test
    fun `اجاره‌ی هر ماه پیدا می‌شود`() {
        val list = listOf(
            pay(j(1404, 4, 1), "کارت ۶۰۳۷۹۹", 150_000_000),
            pay(j(1404, 5, 2), "کارت ۶۰۳۷۹۹", 150_000_000),
            pay(j(1404, 6, 1), "کارت ۶۰۳۷۹۹", 150_000_000),
            pay(j(1404, 7, 3), "کارت ۶۰۳۷۹۹", 150_000_000),
        )
        val s = RecurringDetector.detect(list, existingTitles = emptyList(), dismissedKeys = emptySet(), now = now)
        assertEquals(1, s.size)
        assertEquals(150_000_000L, s[0].amountRial)
        assertEquals(4, s[0].months)
        assertTrue(s[0].dayOfMonth in 1..3)
    }

    @Test
    fun `خریدهای مکرر از یک فروشگاه پرداخت ماهانه نیستند`() {
        val list = (4..7).flatMap { m -> listOf(3, 12, 20).map { d -> pay(j(1404, m, d), "کافه لمیز", 1_800_000) } }
        assertTrue(RecurringDetector.detect(list, emptyList(), emptySet(), now).isEmpty())
    }

    @Test
    fun `روزهای پراکنده یا قدیمی یا ردشده پیشنهاد نمی‌شوند`() {
        val scattered = listOf(4 to 2, 5 to 15, 6 to 28).map { (m, d) -> pay(j(1404, m, d), "باشگاه", 9_000_000) }
        assertTrue(RecurringDetector.detect(scattered, emptyList(), emptySet(), now).isEmpty())

        val stopped = (1..4).map { m -> pay(j(1404, m, 5), "قسط وام", 40_000_000) }
        assertTrue(RecurringDetector.detect(stopped, emptyList(), emptySet(), now).isEmpty())

        val monthly = (5..7).map { m -> pay(j(1404, m, 5), "شهریه", 30_000_000) }
        assertEquals(1, RecurringDetector.detect(monthly, emptyList(), emptySet(), now).size)
        assertTrue(RecurringDetector.detect(monthly, emptyList(), setOf(RecurringDetector.normalize("شهریه")), now).isEmpty())
        // یادآوری‌اش از قبل ساخته شده
        assertTrue(RecurringDetector.detect(monthly, listOf("شهریه مدرسه"), emptySet(), now).isEmpty())
    }
}
