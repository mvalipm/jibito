package ir.jibito.app.notify

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WeeklyDigestRuleTest {

    private lateinit var savedZone: TimeZone

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"))
    }

    @After
    fun tearDown() = TimeZone.setDefault(savedZone)

    private fun at(d: Int, h: Int, m: Int = 0) = Calendar.getInstance().apply { clear(); set(2025, Calendar.OCTOBER, d, h, m) }.timeInMillis

    @Test
    fun `جمعه عصر یک بار، و فردا صبح اگر جا ماند`() {
        // ۳ اکتبر ۲۰۲۵ جمعه است
        val slot = at(3, 19)
        assertEquals(slot, WeeklyDigestRule.lastSlot(at(3, 19, 30)))
        assertEquals(slot, WeeklyDigestRule.lastSlot(at(9, 10)))
        assertFalse(WeeklyDigestRule.isDue(at(3, 18, 59), 0L))
        assertTrue(WeeklyDigestRule.isDue(at(3, 19, 15), 0L))
        assertTrue(WeeklyDigestRule.isDue(at(4, 9), 0L))
        assertFalse(WeeklyDigestRule.isDue(at(3, 21), lastSentSlot = slot))
        // چند روز بعد دیگر خلاصه‌ی کهنه نمی‌آید
        assertFalse(WeeklyDigestRule.isDue(at(6, 12), 0L))
    }

    @Test
    fun `جمع این هفته و هفته‌ی قبل و پرخرج‌ترین دسته`() {
        val now = at(3, 19)
        val day = WeeklyDigestRule.DAY_MS
        val d = WeeklyDigestRule.summarize(
            listOf(
                Triple(now - 1 * day, 3_000_000L, 1L),
                Triple(now - 2 * day, 2_000_000L, 2L),
                Triple(now - 3 * day, 2_000_000L, 2L),
                Triple(now - 9 * day, 5_000_000L, 1L),
                Triple(now - 20 * day, 99_000_000L, 1L),
            ),
            now,
        )
        assertEquals(7_000_000L, d.thisWeekRial)
        assertEquals(5_000_000L, d.lastWeekRial)
        assertEquals(2L, d.topRootId)
        assertEquals(40, d.changePercent)
        assertNull(WeeklyDigestRule.Digest(5, 0, null).changePercent)
    }

    @Test
    fun `خرج یک‌باره در جمع هفته هست ولی در مقایسه و پرخرج‌ترین دسته نه`() {
        val now = at(3, 19)
        val day = WeeklyDigestRule.DAY_MS
        val d = WeeklyDigestRule.summarize(
            listOf(
                Triple(now - 1 * day, 3_000_000L, 1L),
                Triple(now - 2 * day, 4_000_000L, 2L),
                Triple(now - 9 * day, 5_000_000L, 1L),
            ),
            now,
            oneOffs = listOf(now - 1 * day to 100_000_000_000L, now - 10 * day to 50_000_000_000L),
        )
        assertEquals(100_007_000_000L, d.thisWeekRial)
        assertEquals(100_000_000_000L, d.thisWeekOneOffRial)
        assertEquals(2L, d.topRootId)
        // ۷ میلیون در برابر ۵ میلیون (هر دو بدون خرج یک‌باره)
        assertEquals(40, d.changePercent)
    }
}
