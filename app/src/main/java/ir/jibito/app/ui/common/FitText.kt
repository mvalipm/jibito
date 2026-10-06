package ir.jibito.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * چقدر باید کوچک شد (۱ = اندازه‌ی خودش) تا چند تکه‌ی یک خط، با فاصله‌ی [gap] بینشان، در [maxWidth] جا شوند.
 * عرض واقعی را با فونت و اندازه‌ی فونت گوشی می‌سنجد؛ برای عدد درشتی که نباید بشکند.
 */
@Composable
fun rememberFitScale(maxWidth: Dp, vararg parts: Pair<String, TextStyle>, gap: Dp = 0.dp, min: Float = 0.4f): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(maxWidth, density, parts.toList(), gap) {
        val needed = parts.sumOf { (text, style) -> measurer.measure(text, style, softWrap = false, maxLines = 1).size.width } +
            with(density) { gap.toPx() } * (parts.size - 1).coerceAtLeast(0)
        // کمی جای خالی، تا گرد کردن اندازه‌ی فونت لبه‌ی عدد را نبرد
        val room = with(density) { maxWidth.toPx() } * 0.98f
        if (needed > room) (room / needed).coerceAtLeast(min) else 1f
    }
}
