package ir.jibito.app.ui.screenshot

import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.takahirom.roborazzi.captureRoboImage
import ir.jibito.app.widget.CardLine
import ir.jibito.app.widget.CardsViews
import ir.jibito.app.widget.SpendDays
import ir.jibito.app.widget.SpendViews
import ir.jibito.app.widget.SpendWidget
import ir.jibito.app.widget.WidgetSize
import ir.jibito.app.util.Jalali
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** اسکرین‌شات دو ویجت صفحه‌ی اصلی («خرج امروز» و «موجودی کارت‌ها») در هر اندازه، حالت و روشن/تیره */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class WidgetScreenshotTest : ScreenshotTestBase() {

    private val day = 24 * hour

    private fun spend(todayToman: Long, monthToman: Long, budgetToman: Long?) = SpendWidget.Numbers(
        todayRial = todayToman * 10,
        monthRial = monthToman * 10,
        overallBudgetRial = budgetToman?.let { it * 10 },
        weekRial = listOf(3_100_000L, 6_800_000, 2_400_000, 1_200_000, 5_900_000, 4_400_000, todayToman).map { it * 10 },
        weekStarts = SpendDays.dayStarts(Jalali.startOfDay(now), SpendDays.WEEK),
        typicalRial = 49_000_000,
        recent = listOf(
            SpendWidget.Recent(1, "اسنپ‌فود", 4_500_000, now - 2 * hour, "#E4572E"),
            SpendWidget.Recent(2, "هایپراستار", 23_000_000, now - 5 * hour, "#1E9E6A"),
            SpendWidget.Recent(3, null, 5_000_000, now - day, null),
        ),
    )

    private val cards = listOf(
        CardLine(11, "6104337812345678", null, 243_000_000, now - 2 * hour, listOf(300, 280, 290, 260, 270, 250, 243).map { it * 1_000_000L }, -4_500_000),
        CardLine(15, "8490001209", null, 128_000_000, now - day, listOf(110, 105, 100, 98, 98, 128, 128).map { it * 1_000_000L }, 30_000_000),
        CardLine(40, "3341", "حقوق", 55_000_000, now - 3 * day, listOf(70, 68, 61, 55, 55, 55, 55).map { it * 1_000_000L }),
        // مانده‌ی کهنه (بیش از ۳۰ روز) در اندازه‌ی بزرگ کم‌رنگ دیده می‌شود
        CardLine(1, null, null, 8_000_000, now - 34 * day, List(7) { 8_000_000L }),
        CardLine(12, "8802", null, 12_000_000, now - 9 * day, List(7) { 12_000_000L }),
    )

    /** اندازه‌ی نمونه‌ی هر چیدمان روی صفحه (dp) */
    private fun dims(size: WidgetSize) = when (size) {
        WidgetSize.SLIM -> 300 to 76
        WidgetSize.SMALL -> 160 to 160
        WidgetSize.MEDIUM -> 300 to 184
        WidgetSize.LARGE -> 300 to 320
    }

    private fun capture(name: String, size: WidgetSize, dark: Boolean, build: () -> RemoteViews) {
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        val views = build()
        val (w, h) = dims(size)
        captureRoboImage("src/test/screenshots/${name}_${if (dark) "dark" else "light"}.png", roborazziOptions = options) {
            Box(Modifier.padding(12.dp)) {
                AndroidView(factory = { ctx -> views.apply(ctx, FrameLayout(ctx)) }, modifier = Modifier.size(w.dp, h.dp))
            }
        }
    }

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun spendSizes() {
        for (dark in listOf(false, true)) for (size in WidgetSize.entries) {
            capture("widget_spend_${size.name.lowercase()}", size, dark) { SpendViews.build(context, spend(850_000, 7_000_000, 30_000_000), now, size) }
        }
        RuntimeEnvironment.setQualifiers("+notnight")
    }

    /** حال بودجه (اندازه‌ی متوسط): بدون بودجه، نزدیک سقف، رد شده؛ امروز بی‌خرج؛ قفل اپ */
    @Test
    fun spendStates() {
        val cases = listOf(
            "nobudget" to spend(850_000, 10_120_000, null),
            "warn" to spend(850_000, 25_500_000, 30_000_000),
            "over" to spend(850_000, 33_000_000, 30_000_000),
            "zero" to spend(0, 7_000_000, null),
            "locked" to null,
        )
        for (dark in listOf(false, true)) for ((name, n) in cases) {
            capture("widget_spend_$name", WidgetSize.MEDIUM, dark) { SpendViews.build(context, n, now, WidgetSize.MEDIUM) }
        }
        capture("widget_spend_nobudget_slim", WidgetSize.SLIM, dark = false) {
            SpendViews.build(context, spend(850_000, 10_120_000, null), now, WidgetSize.SLIM)
        }
        RuntimeEnvironment.setQualifiers("+notnight")
    }

    @Test
    fun cardsSizes() {
        for (dark in listOf(false, true)) for (size in WidgetSize.entries) {
            capture("widget_cards_${size.name.lowercase()}", size, dark) { CardsViews.build(context, cards, hidden = false, now, size) }
        }
        RuntimeEnvironment.setQualifiers("+notnight")
    }

    /** مبلغ‌های پنهان (دکمه‌ی چشم)، قفل اپ، هنوز موجودی‌ای نیامده */
    @Test
    fun cardsStates() {
        capture("widget_cards_hidden", WidgetSize.MEDIUM, dark = false) { CardsViews.build(context, cards, hidden = true, now, WidgetSize.MEDIUM) }
        capture("widget_cards_locked", WidgetSize.MEDIUM, dark = true) { CardsViews.build(context, null, hidden = false, now, WidgetSize.MEDIUM) }
        capture("widget_cards_empty", WidgetSize.MEDIUM, dark = false) { CardsViews.build(context, emptyList(), hidden = false, now, WidgetSize.MEDIUM) }
        RuntimeEnvironment.setQualifiers("+notnight")
    }
}
