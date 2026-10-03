package ir.jibito.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/** «پنهان کردن مبلغ‌ها» (دکمه‌ی چشم): مبلغ‌ها به‌جای عدد «••••» نشان داده می‌شوند */
val LocalHideAmounts = compositionLocalOf { false }

/** متن جایگزین مبلغ وقتی پنهان است */
const val HIDDEN_AMOUNT = "••••"

/** مبلغ (یا هر متنی که عدد پول دارد)، یا «••••» وقتی کاربر مبلغ‌ها را پنهان کرده */
@Composable
@ReadOnlyComposable
fun amount(text: String): String = if (LocalHideAmounts.current) HIDDEN_AMOUNT else text

/**
 * true یعنی بالای صفحه رنگی است (سرصفحه‌ی «خلاصه») و آیکون‌های نوار وضعیت باید سفید باشند.
 * MainActivity آن را می‌خواند و رنگ آیکون‌های نوار وضعیت را تنظیم می‌کند.
 */
val LocalStatusBarOnColor = staticCompositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }

/** تا وقتی این صفحه دیده می‌شود، آیکون‌های نوار وضعیت سفید باشند (روی سرصفحه‌ی رنگی) */
@Composable
fun StatusBarOnColor() {
    val state = LocalStatusBarOnColor.current
    DisposableEffect(state) {
        state.value = true
        onDispose { state.value = false }
    }
}
