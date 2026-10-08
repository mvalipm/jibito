package ir.jibito.app.ui.screenshot

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import ir.jibito.app.ui.permission.SmsPermissionScreen
import ir.jibito.app.ui.summary.WhereSection
import ir.jibito.app.ui.theme.AppThemeStyle
import ir.jibito.app.ui.welcome.WelcomeScreen
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.FrameLayout
import ir.jibito.app.widget.SpendWidget
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ir.jibito.app.ui.summary.TrendCard
import ir.jibito.app.ui.welcome.FirstRunReveal
import ir.jibito.app.ui.welcome.RevealStats

/** اسکرین‌شات‌های خلاصه‌ی ماه، کارهای لازم، خوش‌آمد و ویجت (پایه‌ی مشترک: ScreenshotTestBase) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class SummaryScreenshotTest : ScreenshotTestBase() {

    @Test
    fun todo() {
        for ((style, dark) in variants) shot("todo", style, dark, padded = false) { Todo() }
    }

    /** سه حال جیب: آروم، یواش‌تر، بیرون زد */
    @Test
    fun hero() {
        for ((style, dark) in variants) shot("hero", style, dark, padded = false) { Hero(7_200_000) }
        shot("hero_warn", AppThemeStyle.DEFAULT, dark = false, padded = false) { Hero(26_400_000) }
        shot("hero_over", AppThemeStyle.DEFAULT, dark = false, padded = false) { Hero(33_600_000) }
    }

    @Test
    fun where() {
        for ((style, dark) in variants) shot("where", style, dark, padded = false) {
            WhereSection(summary(17_400_000), onOpenCategory = {}, onShowAll = {})
        }
    }

    /** خرید یک‌باره‌ی ۱ میلیارد تومانی در «خانه و خانواده»: سهم‌ها بدونش، و جدا کنار مبلغ و زیر نوار */
    @Test
    fun whereWithOneOff() {
        val oneOff = 10_000_000_000L
        val base = summary(17_400_000)
        val s = base.copy(
            totalSpentRial = base.totalSpentRial + oneOff,
            oneOffRial = oneOff,
            categories = base.categories.map { if (it.categoryId == 4L) it.copy(spentRial = it.spentRial + oneOff, oneOffRial = oneOff) else it },
        )
        shot("where_oneoff", AppThemeStyle.DEFAULT, dark = false, padded = false) {
            WhereSection(s, onOpenCategory = {}, onShowAll = {})
        }
    }

    @Test
    fun trend() {
        for ((style, dark) in variants) shot("trend", style, dark, padded = false) { TrendCard(trendSample) }
    }

    @Test
    fun onboarding() {
        for (dark in listOf(false, true)) {
            shot("welcome", AppThemeStyle.DEFAULT, dark, padded = false) { WelcomeScreen(onStart = {}) }
            shot("permission", AppThemeStyle.DEFAULT, dark, padded = false) { SmsPermissionScreen(wasDenied = false, onAllowClick = {}) }
        }
        for ((style, dark) in variants) {
            shot("reveal", style, dark, padded = false) { FirstRunReveal(RevealStats(count = 342, months = 6, banks = 3, topCategory = "سوپرمارکت", topSharePercent = 28), onDone = {}) }
        }
        // عدد شش‌رقمی با فونت خیلی درشت: باید در یک خط بماند
        shot("reveal_big", AppThemeStyle.DEFAULT, dark = true, padded = false, fontScale = 2f) {
            FirstRunReveal(RevealStats(count = 128_400, months = 22, banks = 6, topCategory = "تاکسی اینترنتی", topSharePercent = 18), onDone = {})
        }
        // چند پیامک مبهم: فقط یک خط («از «کارها» کمکم کن»)، نه باز شدن خودکار «بررسی»
        shot("reveal_unsure", AppThemeStyle.DEFAULT, dark = false, padded = false) {
            FirstRunReveal(RevealStats(count = 342, months = 6, banks = 3, topCategory = "سوپرمارکت", topSharePercent = 28), onDone = {}, unsure = 3)
        }
        // عددی که روی گوشی رقم آخرش افتاده بود («۱٬۴۰» به‌جای ۱٬۴۰۸)
        shot("reveal_1408", AppThemeStyle.DEFAULT, dark = true, padded = false) {
            FirstRunReveal(RevealStats(count = 1_408, months = 22, banks = 6, topCategory = "پس‌انداز و سرمایه‌گذاری", topSharePercent = 0), onDone = {})
        }
    }

    @Test
    fun widget() {
        val cases = listOf(
            "calm" to SpendWidget.Numbers(6_050_000, 60_000_000, 300_000_000),
            "warn" to SpendWidget.Numbers(6_050_000, 255_000_000, 300_000_000),
            "over" to SpendWidget.Numbers(6_050_000, 330_000_000, 300_000_000),
            "nobudget" to SpendWidget.Numbers(6_050_000, 184_000_000, null),
            "locked" to null,
        )
        for (dark in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
            for ((name, n) in cases) {
                val context = RuntimeEnvironment.getApplication()
                val views = SpendWidget.views(context, n, now)
                captureRoboImage(
                    "src/test/screenshots/widget_${name}_${if (dark) "dark" else "light"}.png",
                    roborazziOptions = options,
                ) {
                    Box(Modifier.padding(12.dp)) {
                        AndroidView(
                            factory = { ctx -> views.apply(ctx, FrameLayout(ctx)) },
                            modifier = Modifier.size(width = 260.dp, height = 115.dp),
                        )
                    }
                }
            }
        }
        RuntimeEnvironment.setQualifiers("+notnight")
    }
}
