package ir.jibito.app.ui.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import ir.jibito.app.ui.main.FloatingNavBar
import ir.jibito.app.ui.main.NavIcons
import ir.jibito.app.ui.main.NavItem
import ir.jibito.app.ui.summary.WhereSection
import ir.jibito.app.ui.theme.AppThemeStyle
import ir.jibito.app.ui.tour.LocalTourAnchors
import ir.jibito.app.ui.tour.ProductTourOverlay
import ir.jibito.app.ui.tour.TourAnchors
import ir.jibito.app.ui.tour.TourStep
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** راهنمای کاربر تازه روی «خلاصه»: یک مرحله‌ی بالای صفحه (متن پایینش)، یکی روی نوار پایین (متن بالایش) و یکی روی دکمه‌ی تنظیمات */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class TourScreenshotTest : ScreenshotTestBase() {

    @Test
    fun tour() {
        for (step in listOf(TourStep.HERO, TourStep.WHERE, TourStep.TODO, TourStep.SETTINGS)) {
            for (dark in listOf(false, true)) {
                shot("tour_${step.name.lowercase()}", AppThemeStyle.DEFAULT, dark, padded = false) { TourScene(step) }
            }
        }
    }

    @Composable
    private fun TourScene(step: TourStep) {
        val haze = remember { HazeState() }
        val anchors = remember { TourAnchors() }
        Box(Modifier.fillMaxWidth().height(860.dp)) {
            CompositionLocalProvider(LocalTourAnchors provides anchors) {
                Column(Modifier.hazeSource(haze).verticalScroll(rememberScrollState())) {
                    Hero(18_400_000)
                    WhereSection(summary(18_400_000), onOpenCategory = {}, onShowAll = {})
                }
                FloatingNavBar(
                    items = listOf(
                        NavItem(NavIcons.Summary, "خلاصه"),
                        NavItem(NavIcons.Transactions, "تراکنش‌ها", tourStep = TourStep.TRANSACTIONS),
                        NavItem(NavIcons.Reports, "گزارش‌ها", tourStep = TourStep.REPORTS),
                        NavItem(NavIcons.Todo, "کارها", badge = 3, tourStep = TourStep.TODO),
                    ),
                    selectedIndex = 0,
                    onSelect = {},
                    hazeState = haze,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            ProductTourOverlay(anchors, enabled = true, onFinish = {}, startAt = step)
        }
    }
}
