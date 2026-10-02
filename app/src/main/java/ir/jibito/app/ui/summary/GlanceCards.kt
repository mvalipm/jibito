package ir.jibito.app.ui.summary

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.notify.BudgetLevel
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.LocalJibitoColors
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import kotlin.math.abs
import kotlin.math.roundToInt

/** کمتر از این فاصله بین «خرج» و «زمان» یعنی «طبق برنامه» */
private const val ON_TRACK_MARGIN = 0.05f

/** گودی لبه‌ی پایین «جیب» */
private val POCKET_CURVE = 30.dp

/** حال جیب: رنگ بالای صفحه و جمله‌اش از همین می‌آید */
private enum class Mood { NONE, CALM, WARN, OVER }

/**
 * بالای صفحه‌ی خلاصه، به شکل خود «جیب»: سطحی تمام‌عرض با لبه‌ی پایین گود و یک دوخت خط‌چین.
 * - رنگش حال بودجه را می‌گوید (آرام / نزدیک سقف / رد شده)؛ بدون بودجه، رنگ پوسته.
 * - جابه‌جایی ماه همین‌جاست؛ عدد خرج درشت است و موقع آمدن از صفر می‌شمارد.
 * - نوار بودجه یک خط «امروز» دارد؛ زیرش «چقدر مونده · روزی چقدر» و یک جمله‌ی حال.
 * با لمس، بودجه‌ی کل عوض می‌شود.
 */
@Composable
fun GlanceHero(
    s: MonthSummary,
    onEditBudget: () -> Unit,
    /** چند درصد بیشتر/کمتر از همین موقعِ ماه قبل (فقط ماه جاری) */
    vsLastMonthPercent: Int? = null,
    canGoNext: Boolean = false,
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
) {
    val extras = LocalJibitoColors.current
    val budget = s.overallBudgetRial?.takeIf { it > 0 }
    val level = budget?.let { BudgetLevel.of(s.totalSpentRial, it) } ?: 0
    val pace = if (budget != null) s.paceDelta() else null
    val mood = when {
        budget == null -> Mood.NONE
        level >= 100 -> Mood.OVER
        level >= 80 || (pace != null && pace >= ON_TRACK_MARGIN) -> Mood.WARN
        else -> Mood.CALM
    }
    val background by animateColorAsState(
        when (mood) {
            Mood.NONE -> extras.heroStart
            Mood.CALM -> extras.moodCalm
            Mood.WARN -> extras.moodWarn
            Mood.OVER -> extras.moodOver
        },
        animationSpec = tween(700),
        label = "mood",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .pocketBackground(background)
            .clickable(onClickLabel = stringResource(R.string.cd_edit_overall_budget), onClick = onEditBudget)
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = POCKET_CURVE + 18.dp)
    ) {
        HeroMonthSwitcher(s.month.title, canGoNext, onPreviousMonth, onNextMonth)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.summary_spent_label),
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.titleSmall,
        )
        if (s.totalSpentRial == 0L) {
            Text(
                stringResource(R.string.summary_no_spend_yet),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            CountingAmount(s.totalSpentRial)
            vsLastMonthPercent?.let { p ->
                Text(
                    when {
                        abs(p) < SAME_AS_LAST_MONTH -> stringResource(R.string.vs_last_month_same)
                        p > 0 -> Jalali.toPersianDigits(stringResource(R.string.vs_last_month_more, p))
                        else -> Jalali.toPersianDigits(stringResource(R.string.vs_last_month_less, -p))
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (budget == null) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.overall_set_budget),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f))
                    .clickable(onClick = onEditBudget)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        } else {
            val spentFraction = s.spentFraction() ?: 0f
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PaceBar(spent = spentFraction, today = s.timeFraction(), marker = background, modifier = Modifier.weight(1f))
                Spacer(Modifier.size(10.dp))
                Text(
                    Jalali.toPersianDigits("${(spentFraction * 100).roundToInt().coerceAtMost(999)}٪"),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.height(6.dp))
            val remaining = budget - s.totalSpentRial
            Text(
                listOfNotNull(
                    if (remaining >= 0) stringResource(R.string.glance_remaining, Money.compact(remaining), Money.compact(budget))
                    else stringResource(R.string.glance_over, Money.compact(-remaining), Money.compact(budget)),
                    s.dailyAllowanceRial()?.let { stringResource(R.string.glance_daily, Money.compact(it)) },
                ).joinToString("  ·  "),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            // جمله‌ی حال جیب
            val (icon, sentence) = when (mood) {
                Mood.OVER -> JibitoIcons.Warning to stringResource(R.string.mood_over)
                Mood.WARN -> JibitoIcons.Gauge to
                    if (level >= 80) stringResource(R.string.mood_near)
                    else Jalali.toPersianDigits(stringResource(R.string.mood_fast, ((pace ?: 0f) * 100).roundToInt()))
                else -> JibitoIcons.Check to stringResource(R.string.mood_calm)
            }
            Spacer(Modifier.height(12.dp))
            MoodLine(icon, sentence)
        }

        // درآمد و خالص، در یک خط
        Spacer(Modifier.height(14.dp))
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
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** «‹ مهر ۱۴۰۵ ›» سفید، بالای جیب */
@Composable
private fun HeroMonthSwitcher(title: String, canGoNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    val buttonColors = IconButtonDefaults.iconButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = 0.35f),
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // در چیدمان راست‌به‌چپ این آیکون‌ها خودشان آینه می‌شوند: «ماه قبل» سمت راست، «ماه بعد» سمت چپ
        IconButton(onClick = onPrevious, colors = buttonColors) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.summary_prev_month))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext, enabled = canGoNext, colors = buttonColors) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.summary_next_month))
        }
    }
}

/**
 * عدد خرج، درشت؛ موقع آمدن (یا عوض شدن ماه) از عدد قبلی تا عدد تازه می‌شمارد.
 * «۱۸٫۴ میلیون» دو تکه می‌شود: عدد درشت، واحد کوچک‌تر کنارش.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountingAmount(rial: Long) {
    val context = LocalContext.current
    val motionOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val animated = remember { Animatable(if (motionOff) rial.toFloat() else 0f) }
    LaunchedEffect(rial) {
        if (motionOff) animated.snapTo(rial.toFloat())
        else animated.animateTo(rial.toFloat(), tween(900, easing = FastOutSlowInEasing))
    }
    // در پایان دقیقاً خود عدد (float برای مبلغ‌های بزرگ دقیق نیست)
    val shown = if (animated.isRunning) animated.value.toLong() else rial
    val text = Money.compact(shown)
    val split = text.lastIndexOf(' ')
    val number = if (split > 0) text.substring(0, split) else text
    val unit = listOfNotNull(text.substring(split + 1).takeIf { split > 0 }, stringResource(R.string.unit_toman)).joinToString(" ")
    val spoken = Money.compact(rial) + " " + stringResource(R.string.unit_toman)
    FlowRow(
        Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalArrangement = Arrangement.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(number, color = Color.White, fontSize = 56.sp, lineHeight = 64.sp, fontWeight = FontWeight.Black)
        Text(
            unit,
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Bottom).padding(bottom = 10.dp),
        )
    }
}

@Composable
private fun MoodLine(icon: ImageVector, sentence: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.size(10.dp))
        Text(
            sentence,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * زمینه‌ی «جیب»: رنگ ساده با لبه‌ی پایین گود، دو دایره‌ی محو تزئینی و دوخت خط‌چین موازی لبه.
 */
private fun Modifier.pocketBackground(color: Color): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    val curve = POCKET_CURVE.toPx()
    val body = Path().apply {
        moveTo(0f, 0f)
        lineTo(w, 0f)
        lineTo(w, h - curve)
        cubicTo(w, h - curve * 0.25f, w * 0.72f, h, w / 2f, h)
        cubicTo(w * 0.28f, h, 0f, h - curve * 0.25f, 0f, h - curve)
        close()
    }
    drawPath(body, color)
    // دایره‌های محو: فقط داخل جیب
    clipPath(body) {
        drawCircle(Color.White.copy(alpha = 0.07f), radius = w * 0.22f, center = Offset(w * 0.12f, h * 0.05f))
        drawCircle(Color.White.copy(alpha = 0.05f), radius = w * 0.3f, center = Offset(w * 0.95f, h * 0.7f))
    }
    // دوخت
    val inset = 16.dp.toPx()
    val stitchY = h - curve - 12.dp.toPx()
    val stitch = Path().apply {
        moveTo(inset, stitchY)
        cubicTo(inset, stitchY + curve * 0.75f, w * 0.72f, h - 12.dp.toPx(), w / 2f, h - 12.dp.toPx())
        moveTo(w - inset, stitchY)
        cubicTo(w - inset, stitchY + curve * 0.75f, w * 0.28f, h - 12.dp.toPx(), w / 2f, h - 12.dp.toPx())
    }
    drawPath(
        stitch,
        Color.White.copy(alpha = 0.35f),
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 7.dp.toPx())),
        ),
    )
}

/** نوار مصرف بودجه با یک خط «امروز» (کجای ماه هستیم) */
@Composable
private fun PaceBar(spent: Float, today: Float?, marker: Color, modifier: Modifier) {
    BoxWithConstraints(modifier.height(22.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(spent.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
            )
        }
        if (today != null) {
            // خط «امروز» به رنگ جیب با حاشیه‌ی سفید: روی بخش پر و خالی هر دو دیده می‌شود
            Box(
                Modifier
                    .padding(start = (maxWidth * today.coerceIn(0f, 1f) - 2.dp).coerceAtLeast(0.dp))
                    .width(5.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
                    .padding(1.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(marker)
            )
        }
    }
}

/** کمتر از این درصد اختلاف با ماه قبل = «تقریباً همون اندازه» */
private const val SAME_AS_LAST_MONTH = 3
