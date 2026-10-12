package ir.jibito.app.ui.screenshot

import ir.jibito.app.ui.permission.SmsPermissionScreen
import ir.jibito.app.ui.summary.SummaryHero
import ir.jibito.app.ui.summary.WhereSection
import ir.jibito.app.ui.theme.AppThemeStyle
import ir.jibito.app.ui.welcome.WelcomeScreen
import ir.jibito.app.ui.welcome.NotificationIntroScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ir.jibito.app.ui.summary.TrendCard
import ir.jibito.app.ui.welcome.FirstRunReveal
import ir.jibito.app.ui.welcome.RevealStats

/** اسکرین‌شات‌های خلاصه‌ی ماه، کارهای لازم و خوش‌آمد (پایه‌ی مشترک: ScreenshotTestBase؛ ویجت‌ها: WidgetScreenshotTest) */
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

    /** هنوز هیچ تراکنشی نیست: فقط ردیف بالای سرصفحه، بی «۰ تومان» */
    @Test
    fun heroEmpty() {
        shot("hero_empty", AppThemeStyle.DEFAULT, dark = false, padded = false) {
            SummaryHero(summary(0), onPickMonth = {}, onEditBudget = {}, onToggleHidden = {}, nowMillis = now, compact = true)
        }
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
            shot("permission", AppThemeStyle.DEFAULT, dark, padded = false) { SmsPermissionScreen(wasDenied = false, onAllowClick = {}, onManualClick = {}) }
            shot("notification_intro", AppThemeStyle.DEFAULT, dark, padded = false) { NotificationIntroScreen(onAllow = {}, onLater = {}) }
            // بعد از لمس یک دسته: همان «رفت تو غذا»ی نوتیف واقعی و «ثبت شد!»
            shot("notification_intro_picked", AppThemeStyle.DEFAULT, dark, padded = false) {
                NotificationIntroScreen(onAllow = {}, onLater = {}, initialPick = 0)
            }
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
}
