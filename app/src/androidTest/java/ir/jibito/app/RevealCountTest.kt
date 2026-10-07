package ir.jibito.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.jibito.app.ui.common.LocalLoopingMotion
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.welcome.COUNT_TAG
import ir.jibito.app.ui.welcome.FirstRunReveal
import ir.jibito.app.ui.welcome.RevealStats
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * عدد بزرگ صفحه‌ی «جیبت رو شناختم» بعد از شمارش، روی گوشی واقعی کامل کشیده شود.
 * روی گوشی ۱٬۴۰۸ به شکل «۱٬۴۰» دیده می‌شد: رقم آخر به خط دوم رفته بود و maxLines = 1 آن را انداخته بود.
 */
@RunWith(AndroidJUnit4::class)
class RevealCountTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fourDigitCountIsDrawnWhole() = check(1_408, "۱٬۴۰۸")

    @Test
    fun sixDigitCountIsDrawnWhole() = check(128_400, "۱۲۸٬۴۰۰")

    private fun check(count: Int, expected: String) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            JibitoTheme(darkTheme = true) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalLoopingMotion provides false,
                ) {
                    FirstRunReveal(RevealStats(count = count, months = 22, banks = 6, topCategory = "سوپرمارکت", topSharePercent = 18), onDone = {})
                }
            }
        }
        // شمارش ۲٫۸ ثانیه است
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()

        val node = compose.onNodeWithTag(COUNT_TAG).fetchSemanticsNode()
        val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
        assertEquals(expected, text)

        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertEquals("عدد باید در یک خط باشد", 1, layout.lineCount)
        assertEquals("همه‌ی رقم‌ها باید در خط اول کشیده شوند", expected.length, layout.getLineEnd(0, visibleEnd = true))
    }
}
