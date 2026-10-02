package ir.jibito.app.ui.summary

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.provider.Settings
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.notify.BudgetLevel
import ir.jibito.app.ui.theme.LocalJibitoColors
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import kotlin.math.abs
import kotlin.math.roundToInt

/** کمتر از این فاصله بین «خرج» و «زمان» یعنی «طبق برنامه» */
private const val ON_TRACK_MARGIN = 0.05f

/**
 * کارت بالا، جمع‌وجور: ۱) چقدر خرج کردم؟ ۲) تا آخر ماه می‌رسم؟
 * نوار بودجه‌ی کل یک خط «امروز» دارد؛ زیرش یک جمله‌ی سرعت (تندتر/طبق برنامه/جلویی) و «روزی چقدر».
 * درآمد و خالص در یک خط. با لمس کارت، بودجه‌ی کل عوض می‌شود.
 */
@Composable
fun GlanceHero(
    s: MonthSummary,
    onEditBudget: () -> Unit,
    /** چند درصد بیشتر/کمتر از همین موقعِ ماه قبل (فقط ماه جاری) */
    vsLastMonthPercent: Int? = null,
) {
    val extras = LocalJibitoColors.current
    val budget = s.overallBudgetRial?.takeIf { it > 0 }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .animatedHeroBackground(extras.heroStart, extras.heroEnd)
            .clickable(onClick = onEditBudget)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(
            stringResource(R.string.summary_spent, s.month.title),
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelLarge,
        )
        if (s.totalSpentRial == 0L) {
            Text(
                stringResource(R.string.summary_no_spend_yet),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Money.compact(s.totalSpentRial), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(R.string.unit_toman),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            vsLastMonthPercent?.let { p ->
                Text(
                    when {
                        abs(p) < SAME_AS_LAST_MONTH -> stringResource(R.string.vs_last_month_same)
                        p > 0 -> Jalali.toPersianDigits(stringResource(R.string.vs_last_month_more, p))
                        else -> Jalali.toPersianDigits(stringResource(R.string.vs_last_month_less, -p))
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (budget == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.overall_set_budget),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f))
                    .clickable(onClick = onEditBudget)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        } else {
            val spentFraction = s.spentFraction() ?: 0f
            val level = BudgetLevel.of(s.totalSpentRial, budget)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PaceBar(
                    spent = spentFraction,
                    today = s.timeFraction(),
                    fill = when (level) {
                        100 -> Color(0xFFFFD0D0)
                        80 -> Color(0xFFFFE08A)
                        else -> Color.White
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    Jalali.toPersianDigits("${(spentFraction * 100).roundToInt().coerceAtMost(999)}٪"),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(6.dp))
            val remaining = budget - s.totalSpentRial
            Text(
                if (remaining >= 0) stringResource(R.string.glance_remaining, Money.compact(remaining), Money.compact(budget))
                else stringResource(R.string.glance_over, Money.compact(-remaining), Money.compact(budget)),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            // سرعت خرج نسبت به زمان + روزی چقدر
            val pace = s.paceDelta()
            val parts = listOfNotNull(
                pace?.let {
                    when {
                        abs(it) < ON_TRACK_MARGIN -> stringResource(R.string.pace_on_track)
                        it > 0 -> Jalali.toPersianDigits(stringResource(R.string.pace_faster, (it * 100).roundToInt()))
                        else -> stringResource(R.string.pace_ahead)
                    }
                },
                s.dailyAllowanceRial()?.let { stringResource(R.string.glance_daily, Money.compact(it)) },
            )
            if (parts.isNotEmpty()) {
                Text(
                    parts.joinToString("  ·  "),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        // درآمد و خالص، در یک خط
        Spacer(Modifier.height(10.dp))
        val net = s.totalIncomeRial - s.totalSpentRial
        val line = listOfNotNull(
            stringResource(R.string.glance_income, Money.compact(s.totalIncomeRial)),
            // علامت +/− کنار عدد فارسی در متن راست‌به‌چپ جابه‌جا دیده می‌شود؛ به‌جایش کلمه
            stringResource(if (net >= 0) R.string.glance_net_plus else R.string.glance_net_minus, Money.compact(abs(net))),
            s.excludedRial.takeIf { it > 0 }?.let { stringResource(R.string.glance_saved, Money.compact(it)) },
        ).joinToString("  ·  ")
        Text(
            line,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.16f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** یک دور کامل حرکت پس‌زمینه‌ی کارت (میلی‌ثانیه) — آرام، تا حواس را پرت نکند */
private const val HERO_MOTION_MILLIS = 14_000

/**
 * پس‌زمینه‌ی متحرک و ملایم کارت بالا:
 * - جهت گرادیان آرام می‌چرخد.
 * - یک هاله‌ی نور نرم (سفید کم‌رنگ) آهسته روی کارت حرکت می‌کند.
 * فقط مرحله‌ی «کشیدن» تکرار می‌شود (نه چیدمان صفحه)، پس سبک است.
 * اگر کاربر در تنظیمات گوشی انیمیشن‌ها را خاموش کرده باشد، ثابت می‌ماند.
 */
@Composable
private fun Modifier.animatedHeroBackground(start: Color, end: Color): Modifier {
    val context = LocalContext.current
    val motionOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val mid = lerp(start, end, 0.5f)
    if (motionOff) return this.background(Brush.linearGradient(listOf(start, end)))

    val transition = rememberInfiniteTransition(label = "hero")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(HERO_MOTION_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "heroPhase",
    )
    return this.drawBehind {
        val w = size.width
        val h = size.height
        val s = sin(phase)
        val c = cos(phase)
        // گرادیان اصلی که جهتش آرام جابه‌جا می‌شود
        drawRect(
            Brush.linearGradient(
                colors = listOf(start, mid, end),
                start = Offset(w * (0.15f * s), h * (0.5f - 0.5f * c)),
                end = Offset(w * (1f - 0.15f * s), h * (0.5f + 0.5f * c)),
            )
        )
        // هاله‌ی نور نرم
        val center = Offset(w * (0.5f + 0.35f * c), h * (0.45f + 0.3f * s))
        val radius = maxOf(w, h) * 0.55f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

/** نوار مصرف بودجه با یک خط تیره‌ی «امروز» (کجای ماه هستیم) */
@Composable
private fun PaceBar(spent: Float, today: Float?, fill: Color, modifier: Modifier) {
    BoxWithConstraints(modifier.height(18.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(spent.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(fill)
            )
        }
        if (today != null) {
            Box(
                Modifier
                    .padding(start = (maxWidth * today.coerceIn(0f, 1f) - 1.dp).coerceAtLeast(0.dp))
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF1C1B22).copy(alpha = 0.75f))
            )
        }
    }
}

/** کمتر از این درصد اختلاف با ماه قبل = «تقریباً همون اندازه» */
private const val SAME_AS_LAST_MONTH = 3
