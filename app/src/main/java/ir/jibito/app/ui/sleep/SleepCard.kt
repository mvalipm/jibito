package ir.jibito.app.ui.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.sms.SleepAlert
import ir.jibito.app.ui.common.Mascot
import ir.jibito.app.ui.common.MascotFace
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali

/** «گوشی منو خوابونده؟» لازم است؟ null یعنی نه. با هر خرج دیرپیداشده، «فعلاً نه» یا «انجام دادم» دوباره خوانده می‌شود */
@Composable
fun rememberSleepAlert(): SleepAlert? {
    val watch = (LocalContext.current.applicationContext as JibitoApplication).container.sleepWatch
    val version by watch.version.collectAsState()
    return remember(version) { watch.alert() }
}

/** متن کارت: چند خرج این هفته دیر پیدا شد و یک نمونه («مثلاً اسنپ رو ۲ ساعت بعد») */
@Composable
fun sleepAlertText(alert: SleepAlert): String {
    val lag = lagText(alert.example.lagMillis)
    val label = alert.example.label
    return Jalali.toPersianDigits(
        if (label != null) stringResource(R.string.sleep_body, alert.count, label, lag)
        else stringResource(R.string.sleep_body_plain, alert.count, lag)
    )
}

/** «۴۵ دقیقه»، «۲ ساعت»، «۳ روز» (گرد به پایین) */
@Composable
private fun lagText(lagMillis: Long): String {
    val (unit, value) = lagUnit(lagMillis)
    return when (unit) {
        LagUnit.MINUTES -> stringResource(R.string.sleep_lag_minutes, value)
        LagUnit.HOURS -> stringResource(R.string.sleep_lag_hours, value)
        LagUnit.DAYS -> stringResource(R.string.sleep_lag_days, value)
    }
}

internal enum class LagUnit { MINUTES, HOURS, DAYS }

internal fun lagUnit(lagMillis: Long): Pair<LagUnit, Int> {
    val minutes = lagMillis / 60_000
    return when {
        minutes < 60 -> LagUnit.MINUTES to minutes.toInt().coerceAtLeast(1)
        minutes < 24 * 60 -> LagUnit.HOURS to (minutes / 60).toInt()
        else -> LagUnit.DAYS to (minutes / (24 * 60)).toInt()
    }
}

/** کارت بالای «خلاصه»: جیبیِ خواب‌آلود، چند خرج دیر و یک نمونه؛ «درستش کنیم» برگه‌ی «بیدار نگهم دار» را باز می‌کند */
@Composable
fun SleepCard(alert: SleepAlert, onFix: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.amberTint)
            .border(1.dp, t.amber.copy(alpha = 0.25f), shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(44.dp, face = MascotFace.SLEEPY)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.sleep_title),
                modifier = Modifier.semantics { heading() },
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
            )
        }
        Text(
            sleepAlertText(alert),
            modifier = Modifier.padding(top = 8.dp),
            fontSize = 14.sp,
            lineHeight = 24.sp,
            color = colors.onBackground,
        )
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onFix, modifier = Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(50)) {
                Text(stringResource(R.string.sleep_fix), fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onLater, modifier = Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(50)) {
                Text(stringResource(R.string.sleep_later), fontWeight = FontWeight.Bold, color = colors.onBackground)
            }
        }
    }
}
