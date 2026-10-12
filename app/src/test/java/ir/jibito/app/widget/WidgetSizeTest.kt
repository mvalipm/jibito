package ir.jibito.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizeTest {

    @Test
    fun picksLayoutFromReportedSize() {
        assertEquals(WidgetSize.SLIM, WidgetSize.pick(300, 70))
        assertEquals(WidgetSize.SMALL, WidgetSize.pick(160, 160))
        assertEquals(WidgetSize.SMALL, WidgetSize.pick(300, 120))
        // ۳×۲ روی گوشی کاربر (سامسونگ): حدود ۲۶۰ در ۱۸۰
        assertEquals(WidgetSize.MEDIUM, WidgetSize.pick(262, 184))
        assertEquals(WidgetSize.LARGE, WidgetSize.pick(300, 300))
    }

    /** لانچری که اندازه را نمی‌گوید ← متوسط (همان چیدمان پیش‌فرض) */
    @Test
    fun unknownSizeIsMedium() = assertEquals(WidgetSize.MEDIUM, WidgetSize.pick(0, 0))

    /** همان انتخابِ اندروید ۱۲ به بعد: بزرگ‌ترین چیدمانی که در اندازه جا می‌شود */
    @Test
    fun pickMatchesLargestFittingLayout() {
        for (w in listOf(120, 160, 230, 300, 400)) for (h in listOf(50, 105, 160, 260, 400)) {
            val fitting = WidgetSize.entries.filter { it.widthDp <= w && it.heightDp <= h }
            val expected = fitting.maxByOrNull { it.widthDp * it.heightDp } ?: WidgetSize.SLIM
            assertEquals("$w×$h", expected, WidgetSize.pick(w, h))
        }
    }
}
