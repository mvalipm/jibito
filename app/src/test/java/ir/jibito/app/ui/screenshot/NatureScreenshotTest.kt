package ir.jibito.app.ui.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.jibito.app.data.category.Nature
import ir.jibito.app.data.category.NatureBreakdown
import ir.jibito.app.data.category.NatureItem
import ir.jibito.app.data.repository.MonthReport
import ir.jibito.app.data.repository.SpendCurve
import ir.jibito.app.domain.Category
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.ui.reports.ReportRange
import ir.jibito.app.ui.reports.ReportSection
import ir.jibito.app.ui.reports.ReportsContent
import ir.jibito.app.ui.theme.AppThemeStyle
import ir.jibito.app.util.JalaliMonth
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** «خرجت چه‌جور بود؟»: نوار سه‌رنگ اجباری/ضروری/دلخواه، دسته‌های هر ماهیت و مقایسه‌ی دلخواه با ماه قبل */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class NatureScreenshotTest : ScreenshotTestBase() {

    private val mehr = JalaliMonth(1405, 7)

    private fun cat(id: Long, name: String, icon: String, color: String, custom: Boolean = false) =
        Category(id, name, icon, color, flowType = 2, isCustom = custom)

    private val categories = listOf(
        cat(2, "اجاره و مسکن", "🏠", "#EDA100"),
        cat(3, "وام و اقساط", "💳", "#4A3AA7"),
        cat(4, "قبض و شارژ", "💡", "#4A3AA7"),
        cat(5, "سوپرمارکت", "🛒", "#2A78D6"),
        cat(6, "تاکسی اینترنتی", "🚕", "#1BAF7A"),
        cat(7, "دارو و مکمل", "💊", "#008300"),
        cat(8, "رستوران و فست‌فود", "🍽", "#EB6834"),
        cat(9, "سینما و تئاتر", "🎬", "#E34948"),
        cat(10, "لباس", "👕", "#E87BA4"),
        cat(11, "کارگاه", "🔧", "#8B5A2B", custom = true),
    )

    private val breakdown = NatureBreakdown(
        totals = mapOf(Nature.MUST to 186_000_000L, Nature.NEED to 98_000_000L, Nature.WANT to 46_000_000L, null to 9_000_000L),
        items = mapOf(
            Nature.MUST to listOf(NatureItem(2, 170_000_000), NatureItem(3, 12_000_000), NatureItem(4, 4_000_000)),
            Nature.NEED to listOf(NatureItem(5, 52_000_000), NatureItem(6, 28_000_000), NatureItem(7, 18_000_000)),
            Nature.WANT to listOf(NatureItem(8, 30_000_000), NatureItem(9, 9_000_000), NatureItem(10, 7_000_000)),
            null to listOf(NatureItem(11, 9_000_000)),
        ),
        previous = mapOf(Nature.WANT to 60_000_000L),
    )

    /** تب «گزارش‌ها» با کارت «خرجت چه‌جور بود؟» باز */
    @Test
    fun reportsNature() {
        val curve = SpendCurve.compute(emptyList(), mehr, now, budgetRial = null)
        for (dark in listOf(false, true)) shot("reports_nature", AppThemeStyle.DEFAULT, dark, padded = false) {
            Box(Modifier.fillMaxWidth().height(860.dp)) {
                CompositionLocalProvider(LocalBottomBarSpace provides 24.dp) {
                    ReportsContent(
                        month = mehr,
                        range = ReportRange.MONTH,
                        report = MonthReport(curve, emptyList()),
                        months = null,
                        onRange = {},
                        open = ReportSection.NATURE,
                        nature = breakdown,
                        categories = categories,
                    )
                }
            }
        }
    }
}
