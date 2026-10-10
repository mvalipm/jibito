package ir.jibito.app.data.category

import ir.jibito.app.util.Jalali
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RentGuessTest {

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

    private val now = j(1404, 7, 10)

    private fun monthly(rial: Long, months: IntRange = 4..7, day: Int = 2) = months.map { rial to j(1404, it, day) }

    @Test
    fun `مبلغ بزرگ و ثابت هر ماه ← شبیه اجاره`() {
        assertTrue(RentGuess.looksLikeRent(monthly(150_000_000), now))
    }

    @Test
    fun `مبلغ ثابت ولی کوچک اجاره نیست`() {
        assertFalse(RentGuess.looksLikeRent(monthly(RentGuess.MIN_AMOUNT_RIAL - 1), now))
        assertTrue(RentGuess.looksLikeRent(monthly(RentGuess.MIN_AMOUNT_RIAL), now))
    }

    @Test
    fun `مبلغ‌های پراکنده یا روزهای نامنظم اجاره نیست`() {
        val amounts = listOf(150_000_000L, 20_000_000L, 90_000_000L, 400_000_000L)
        assertFalse(RentGuess.looksLikeRent(amounts.mapIndexed { i, a -> a to j(1404, 4 + i, 2) }, now))
        val days = listOf(2, 15, 28, 9)
        assertFalse(RentGuess.looksLikeRent(days.mapIndexed { i, d -> 150_000_000L to j(1404, 4 + i, d) }, now))
    }

    @Test
    fun `کمتر از سه ماه یا قطع‌شده اجاره نیست`() {
        assertFalse(RentGuess.looksLikeRent(monthly(150_000_000, 6..7), now))
        assertFalse(RentGuess.looksLikeRent(monthly(150_000_000, 2..4), now))
    }

    @Test
    fun `پرداخت‌های قدیمی‌تر از شش ماه شمرده نمی‌شوند`() {
        assertFalse(RentGuess.looksLikeRent(monthly(150_000_000, 1..1) + monthly(150_000_000, 6..7), now))
    }

    @Test
    fun `بدون پرداخت ← اجاره نیست`() {
        assertFalse(RentGuess.looksLikeRent(emptyList(), now))
    }

    @Test
    fun `شروع پنجره شش ماه شمسی است`() {
        assertEquals(j(1404, 2, 1) - 12 * 60 * 60 * 1000, RentGuess.windowStart(now))
    }
}
