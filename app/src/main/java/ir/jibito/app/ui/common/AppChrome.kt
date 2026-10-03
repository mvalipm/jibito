package ir.jibito.app.ui.common

import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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

/**
 * false یعنی انیمیشن‌های بی‌پایان (تپیدن «؟»، بالا و پایین رفتن جیبی، هاله…) اجرا نشوند و همان حالت اول بمانند.
 * تست‌های اسکرین‌شات خاموشش می‌کنند (وگرنه صفحه هیچ‌وقت «آرام» نمی‌شود و تست منتظر می‌ماند).
 */
val LocalLoopingMotion = staticCompositionLocalOf { true }

/**
 * یک عدد که بی‌پایان بین [from] و [to] می‌رود (و اگر [reverse]، برمی‌گردد).
 * اگر انیمیشن‌های گوشی خاموش باشد یا [LocalLoopingMotion] false باشد، همیشه [from] است.
 */
@Composable
fun loopingValue(
    from: Float,
    to: Float,
    millis: Int,
    label: String,
    reverse: Boolean = true,
    easing: Easing = FastOutSlowInEasing,
): Float {
    val context = LocalContext.current
    val systemMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    if (!LocalLoopingMotion.current || !systemMotion) return from
    val transition = rememberInfiniteTransition(label = label)
    val value by transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(tween(millis, easing = easing), if (reverse) RepeatMode.Reverse else RepeatMode.Restart),
        label = label,
    )
    return value
}
