package ir.jibito.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.jibito.app.ui.common.LocalLoopingMotion
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.welcome.FirstRunReveal
import ir.jibito.app.ui.welcome.RevealStats
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * صفحه‌ی «جیبت رو شناختم» روی گوشی کوچک (۳۶۰×۶۴۰) باید بی‌اسکرول جا شود: جیبی جمع می‌شود نه این‌که
 * کاربر برای رسیدن به آمار و توضیح پایین، اسکرول کند.
 */
@RunWith(AndroidJUnit4::class)
class RevealFitsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fitsSmallPhone() = check(unsure = 0)

    @Test
    fun fitsSmallPhoneWithUnsureNote() = check(unsure = 3)

    private fun check(unsure: Int) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            JibitoTheme(darkTheme = true) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalLoopingMotion provides false,
                ) {
                    Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                        FirstRunReveal(RevealStats(count = 1_409, months = 22, banks = 6), onDone = {}, unsure = unsure)
                    }
                }
            }
        }
        // شمارش ۲٫۸ ثانیه است
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()

        compose.onNodeWithText("بزن بریم").assertExists()
        val scroll = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("محتوا نباید جای اسکرول داشته باشد", 0f, scroll.maxValue(), 0.5f)
    }
}
