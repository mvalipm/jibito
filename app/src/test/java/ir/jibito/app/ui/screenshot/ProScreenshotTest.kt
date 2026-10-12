package ir.jibito.app.ui.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.data.pro.ProFeature
import ir.jibito.app.data.pro.ProStatus
import ir.jibito.app.data.repository.MonthReport
import ir.jibito.app.data.repository.SpendCurve
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.ui.pro.ProSheetContent
import ir.jibito.app.ui.reports.ReportRange
import ir.jibito.app.ui.reports.ReportSection
import ir.jibito.app.ui.reports.ReportsContent
import ir.jibito.app.ui.settings.SettingsSection
import ir.jibito.app.ui.settings.ThemePicker
import ir.jibito.app.ui.summary.SummaryHero
import ir.jibito.app.ui.theme.AppThemeStyle
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.util.JalaliMonth
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** «جیبیتوی کامل»: برگه‌ی خرید، و جای امکانات بسته بعد از ماه رایگان (گزارش‌ها، سرصفحه‌ی خلاصه، پوسته‌ها) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ProScreenshotTest : ScreenshotTestBase() {

    private val mehr = JalaliMonth(1405, 7)

    /** برگه‌ی خرید: ماه رایگان (از «خرجت چه‌جور بود؟» باز شده)، تمام شدن ماه رایگان با قیمت، و خریده */
    @Test
    fun proSheet() {
        for (dark in listOf(false, true)) shot("pro_sheet_trial", AppThemeStyle.DEFAULT, dark) {
            ProSheetContent(
                status = ProStatus.Trial(12), feature = ProFeature.NATURE, trialEnded = false, price = null,
                busy = false, message = null, onBuy = {}, onRestore = {}, onClose = {},
            )
        }
        shot("pro_sheet_ended", AppThemeStyle.DEFAULT, dark = false) {
            ProSheetContent(
                status = ProStatus.Free, feature = null, trialEnded = true, price = "۹۹٬۰۰۰ تومان",
                busy = false, message = R.string.pro_msg_not_found, onBuy = {}, onRestore = {}, onClose = {},
            )
        }
        shot("pro_sheet_ended", AppThemeStyle.DEFAULT, dark = false, fontScale = 2f) {
            ProSheetContent(
                status = ProStatus.Free, feature = null, trialEnded = true, price = "۹۹٬۰۰۰ تومان",
                busy = false, message = null, onBuy = {}, onRestore = {}, onClose = {},
            )
        }
        shot("pro_sheet_owned", AppThemeStyle.DEFAULT, dark = true) {
            ProSheetContent(
                status = ProStatus.Owned, feature = null, trialEnded = false, price = null,
                busy = false, message = null, onBuy = {}, onRestore = {}, onClose = {},
            )
        }
    }

    /** «گزارش‌ها» بعد از ماه رایگان: موجودی بسته (قفل کنار عنوان و کارت توضیح)، و نمودار ۶ ماهه بسته */
    @Test
    fun reportsLocked() {
        val report = MonthReport(SpendCurve.compute(emptyList(), mehr, now, budgetRial = null), emptyList())
        val cases = listOf(
            Triple("reports_locked_balance", ReportSection.BALANCE, ReportRange.MONTH),
            Triple("reports_locked_months", ReportSection.SPEND, ReportRange.HALF_YEAR),
        )
        for ((name, open, range) in cases) for (dark in listOf(false, true)) shot(name, AppThemeStyle.DEFAULT, dark, padded = false) {
            Box(Modifier.fillMaxWidth().height(860.dp)) {
                CompositionLocalProvider(LocalBottomBarSpace provides 24.dp) {
                    ReportsContent(
                        month = mehr,
                        range = range,
                        report = report,
                        months = null,
                        onRange = {},
                        open = open,
                        unlocked = false,
                    )
                }
            }
        }
    }

    /** سرصفحه‌ی «خلاصه»: به‌جای پیش‌بینی، «پولت تا حقوق می‌رسه؟ در جیبیتوی کامل می‌گم» */
    @Test
    fun heroTeaser() {
        for (dark in listOf(false, true)) shot("hero_forecast_locked", AppThemeStyle.DEFAULT, dark, padded = false) {
            SummaryHero(
                summary(7_200_000),
                onPickMonth = {},
                onEditBudget = {},
                onToggleHidden = {},
                nowMillis = now,
                onForecastLocked = {},
            )
        }
    }

    /** پوسته‌ها بعد از ماه رایگان: چهار پوسته‌ی رنگی قفل دارند */
    @Test
    fun themesLocked() {
        shot("settings_themes_locked", AppThemeStyle.DEFAULT, dark = false) {
            SettingsSection("پوسته", JibitoIcons.Palette) { ThemePicker(AppThemeStyle.DEFAULT, onSelect = {}, proLocked = true) }
        }
    }
}
