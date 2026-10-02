package ir.jibito.app.ui.welcome

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.common.JibitoLogo
import ir.jibito.app.ui.theme.LocalJibitoColors
import ir.jibito.app.util.Jalali

/** فقط یک بار، بعد از اولین خواندن پیامک‌ها روی این گوشی */
class FirstRunFlag(context: Context) {
    private val prefs = context.getSharedPreferences("first_run", Context.MODE_PRIVATE)

    val done: Boolean get() = prefs.getBoolean(KEY_DONE, false)

    fun markDone() {
        prefs.edit().putBoolean(KEY_DONE, true).apply()
    }

    private companion object {
        const val KEY_DONE = "reveal_done"
    }
}

/**
 * لحظه‌ی «آهان!»: بعد از دادن اجازه و اولین خواندن پیامک‌ها، با یک شمارش نرم نشان می‌دهد
 * چند تراکنش از چند ماه گذشته پیدا شد. روی همه‌چیز می‌نشیند تا کاربر «بزن بریم» را بزند.
 */
@Composable
fun FirstRunReveal(stats: RevealStats, onDone: () -> Unit) {
    val extras = LocalJibitoColors.current
    BackHandler(onBack = onDone)
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val shownCount by animateIntAsState(
        targetValue = if (started) stats.count else 0,
        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        label = "count",
    )
    val detailsAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 900),
        label = "details",
    )
    val buttonAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 1500),
        label = "button",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(extras.heroStart, extras.heroEnd)))
            // لمس‌ها به صفحه‌ی زیرش نرسد
            .pointerInput(Unit) { detectTapGestures { } }
            .safeDrawingPadding()
            .padding(28.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            JibitoLogo(size = 84.dp)
            Spacer(Modifier.height(32.dp))
            Text(
                Jalali.toPersianDigits(shownCount.toString()),
                color = Color.White,
                fontSize = 68.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                stringResource(R.string.reveal_found),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Column(Modifier.alpha(detailsAlpha), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Jalali.toPersianDigits(
                        if (stats.banks > 0) stringResource(R.string.reveal_span_banks, stats.months, stats.banks)
                        else stringResource(R.string.reveal_span, stats.months)
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.reveal_next),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Button(
            onClick = onDone,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp)
                .alpha(buttonAlpha),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = extras.heroStart),
        ) {
            Text(stringResource(R.string.reveal_go), fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}
