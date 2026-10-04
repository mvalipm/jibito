package ir.jibito.app.widget

import ir.jibito.app.util.JalaliMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpendWidgetTest {

    private val day = 24 * 60 * 60 * 1000L
    private val month = JalaliMonth(1405, 7) // مهر: ۳۰ روز

    /** ساعت ۲۰ روز d ماه */
    private fun at(d: Int) = month.startMillis() + (d - 1) * day + 20 * 60 * 60 * 1000L

    @Test
    fun `روزهای مانده با خود امروز`() {
        assertEquals(30, SpendWidget.daysLeft(at(1)))
        assertEquals(22, SpendWidget.daysLeft(at(9)))
        assertEquals(1, SpendWidget.daysLeft(at(30)))
    }

    @Test
    fun `سهم امروز از آنچه تا دیروز مانده حساب می‌شود`() {
        val n = SpendWidget.Numbers(todayRial = 6_050_000, monthRial = 60_000_000, overallBudgetRial = 300_000_000)
        // تا دیروز ۵۳٫۹۵ میلیون ریال خرج شده؛ ۲۴۶٫۰۵ میلیون مانده برای ۲۲ روز
        assertEquals(246_050_000L / 22, SpendWidget.dailyShare(n, at(9)))
    }

    @Test
    fun `بدون بودجه یا بودجه‌ی تمام‌شده سهمی نیست`() {
        assertNull(SpendWidget.dailyShare(SpendWidget.Numbers(1_000, 5_000, null), at(9)))
        assertNull(SpendWidget.dailyShare(SpendWidget.Numbers(1_000, 400_000_000, 300_000_000), at(9)))
    }

    @Test
    fun `طرح ویجت از روی اندازه`() {
        assertEquals(SpendWidget.Size.LARGE, SpendWidget.sizeOf(356f, 290f))
        assertEquals(SpendWidget.Size.MEDIUM, SpendWidget.sizeOf(356f, 180f))
        assertEquals(SpendWidget.Size.SQUARE, SpendWidget.sizeOf(170f, 180f))
        assertEquals(SpendWidget.Size.SQUARE, SpendWidget.sizeOf(200f, 300f))
        assertEquals(SpendWidget.Size.STRIP, SpendWidget.sizeOf(356f, 80f))
        assertEquals(SpendWidget.Size.STRIP, SpendWidget.sizeOf(110f, 180f))
    }
}
