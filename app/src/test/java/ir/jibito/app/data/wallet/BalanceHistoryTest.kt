package ir.jibito.app.data.wallet

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BalanceHistoryTest {

    private val hour = 60L * 60 * 1000
    private val day = 24 * hour
    private lateinit var savedZone: TimeZone

    /** ۱ مهر ۱۴۰۵ = ۲۳ سپتامبر ۲۰۲۶، نیمه‌شب تهران */
    private var start = 0L

    @Before
    fun fixZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"))
        start = Calendar.getInstance().apply { clear(); set(2026, Calendar.SEPTEMBER, 23) }.timeInMillis
    }

    @After
    fun restoreZone() = TimeZone.setDefault(savedZone)

    private var nextId = 1L

    private fun point(dayIndex: Int, remain: Long, bank: Int = 11, account: String? = "1234", hourOfDay: Int = 12, flowType: Int = 2, amount: Long = 0) =
        BalancePointRow(nextId++, bank, account, remain, start + dayIndex * day + hourOfDay * hour, flowType, amount)

    private fun grid(days: Int) = DayGrid.of(start, start + (days - 1) * day + 20 * hour)

    @Test
    fun `بازه‌ی روزها با هر دو سر`() {
        val g = grid(5)
        assertEquals(5, g.size)
        assertEquals(start, g.starts.first())
        assertEquals(start + 5 * day, g.end)
        assertEquals(2, g.indexOf(start + 2 * day + 3 * hour))
        assertNull(g.indexOf(start - 1))
        assertNull(g.indexOf(g.end))
        assertEquals(30, DayGrid.lastDays(30, start + 40 * day + 9 * hour).size)
    }

    @Test
    fun `مانده‌ی هر روز آخرین پیامک تا آخر همان روز است و قبل از اولین پیامک معلوم نیست`() {
        val points = listOf(
            point(1, 500, hourOfDay = 9),
            point(1, 300, hourOfDay = 18),
            point(3, 900),
        )
        val s = BalanceHistory.daily(points, grid(5))
        assertEquals(listOf(null, 300L, 300L, 900L, 900L), s.values)
        assertEquals(1, s.firstKnown)
        assertEquals(listOf(300L, 300L, 900L, 900L), s.known)
        assertTrue(s.hasTrend)
    }

    @Test
    fun `پیامک قبل از بازه مانده‌ی روز اول را می‌دهد`() {
        val s = BalanceHistory.daily(listOf(point(-10, 700), point(2, 400)), grid(4))
        assertEquals(listOf(700L, 700L, 400L, 400L), s.values)
    }

    @Test
    fun `آمار بازه از روزهای معلوم`() {
        val s = BalanceHistory.daily(listOf(point(1, 800), point(2, 200), point(3, 900), point(4, 600)), grid(5))
        val stats = s.stats()!!
        assertEquals(800L, stats.startRial)
        assertEquals(600L, stats.endRial)
        assertEquals(-200L, stats.changeRial)
        assertEquals(200L, stats.minRial)
        assertEquals(start + 2 * day, stats.minDay)
        assertEquals(900L, stats.maxRial)
        assertEquals(start + 1 * day, stats.startDay)
        assertNull(BalanceHistory.daily(emptyList(), grid(3)).stats())
    }

    @Test
    fun `حساب‌ها مثل کیف پول گروه می‌شوند`() {
        val rows = listOf(
            point(0, 100, bank = 11, account = "1111"),
            point(1, 200, bank = 11, account = "2222"),
            // بی‌شماره در بانکی که شماره دارد: کنار می‌رود
            point(2, 999, bank = 11, account = null),
            // بانکی که هیچ‌وقت شماره ندارد: یک حساب برای کل بانک
            point(0, 50, bank = 15, account = null),
        )
        // کاربر گفته ۲۲۲۲ همان ۱۱۱۱ است
        val links = listOf(
            AccountLink(11, "1111", "1111", "حقوق", decided = true),
            AccountLink(11, "2222", "1111", null, decided = true),
        )
        val grouped = BalanceHistory.byAccount(rows, links)
        assertEquals(setOf(AccountRef(11, "1111"), AccountRef(15, null)), grouped.keys)
        assertEquals(listOf(100L, 200L), grouped.getValue(AccountRef(11, "1111")).map { it.remainAfter })

        val o = BalanceHistory.overview(rows, links, emptySet(), grid(3))
        val salary = o.accounts.first { it.ref.bankId == 11 }
        assertEquals("حقوق", salary.name)
        assertEquals(listOf(100L, 200L, 200L), salary.series.values)
    }

    @Test
    fun `جمع فقط از روزی که همه‌ی حساب‌ها معلوم‌اند و بدون حساب‌های بیرون از جمع`() {
        val rows = listOf(
            point(0, 100, bank = 11, account = "1111"),
            point(2, 40, bank = 15, account = null),
            point(0, 5_000, bank = 40, account = "9"),
        )
        val o = BalanceHistory.overview(rows, emptyList(), setOf(WalletExclusion.wholeBank(40)), grid(4))
        assertEquals(listOf(null, null, 140L, 140L), o.total.values)
        assertTrue(o.accounts.first { it.ref.bankId == 40 }.excluded)
        assertFalse(o.accounts.first { it.ref.bankId == 11 }.excluded)
        // تازه‌ترین پیامک اول
        assertEquals(15, o.accounts.first().ref.bankId)
    }

    @Test
    fun `بدون حساب جمعی هم نیست`() {
        val o = BalanceHistory.overview(emptyList(), emptyList(), emptySet(), grid(3))
        assertEquals(listOf<Long?>(null, null, null), o.total.values)
        assertFalse(o.total.hasTrend)
        assertTrue(o.accounts.isEmpty())
    }

    @Test
    fun `کلید حساب رفت و برگشت دارد`() {
        assertEquals(AccountRef(11, "1234"), AccountRef.parse(AccountRef(11, "1234").key))
        assertEquals(AccountRef(15, null), AccountRef.parse(AccountRef(15, null).key))
        assertNull(AccountRef.parse("all"))
    }

    @Test
    fun `حساب ساکت`() {
        val now = start + 40 * day
        assertTrue(BalanceHistory.isStale(now - 30 * day, now))
        assertFalse(BalanceHistory.isStale(now - 29 * day, now))
    }

    @Test
    fun `بعد از واریز بزرگ چند روزه نصفش خرج شد`() {
        val points = listOf(
            point(0, 5_000_000),
            // حقوق ۴۰ میلیون ریال ← ۴۵ میلیون
            point(1, 45_000_000, flowType = 1, amount = 40_000_000),
            point(3, 30_000_000),
            // هدف: ۵ + ۲۰ = ۲۵ میلیون
            point(6, 24_000_000),
            point(8, 10_000_000),
        )
        val r = BalanceHistory.depositRhythm(points, grid(10), nowMillis = start + 9 * day + hour)!!
        assertEquals(40_000_000L, r.amountRial)
        assertEquals(5, r.halfSpentDays)
        assertEquals(8, r.daysSince)
    }

    @Test
    fun `واریزی که هنوز بیش از نصفش مانده`() {
        val points = listOf(point(0, 5_000_000), point(2, 45_000_000, flowType = 1, amount = 40_000_000), point(4, 30_000_000))
        val r = BalanceHistory.depositRhythm(points, grid(6), nowMillis = start + 5 * day + hour)!!
        assertNull(r.halfSpentDays)
        assertEquals(3, r.daysSince)
    }

    @Test
    fun `واریز کوچک یا بیرون از بازه حساب نمی‌شود`() {
        val points = listOf(point(-5, 50_000_000, flowType = 1, amount = 40_000_000), point(1, 6_000_000, flowType = 1, amount = 1_000_000))
        assertNull(BalanceHistory.depositRhythm(points, grid(4), nowMillis = start + 3 * day))
    }

    @Test
    fun `تراکنش‌های یک حساب، با شماره‌هایی که کاربر یکی دانسته`() {
        val rows = listOf(FlowAccountRow(1, "1111"), FlowAccountRow(2, "2222"), FlowAccountRow(3, "3333"), FlowAccountRow(4, null))
        val links = listOf(AccountLink(11, "2222", "1111", null, decided = true))
        assertEquals(setOf(1L, 2L), BalanceHistory.flowIdsOf(AccountRef(11, "1111"), rows, links))
        assertEquals(setOf(3L), BalanceHistory.flowIdsOf(AccountRef(11, "3333"), rows, links))
        assertEquals(setOf(1L, 2L, 3L, 4L), BalanceHistory.flowIdsOf(AccountRef(11, null), rows, links))
    }
}
