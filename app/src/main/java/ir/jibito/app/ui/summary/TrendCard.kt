package ir.jibito.app.ui.summary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.repository.SpendTrend
import ir.jibito.app.ui.common.LocalHideAmounts
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import kotlin.math.abs
import kotlin.math.roundToInt

/** کمتر از این درصد اختلاف = «تقریباً همون اندازه» */
private const val SAME_PERCENT = 3

/** بلندترین ستون (بقیه نسبت به آن) */
private val MAX_BAR = 104.dp

/** جای ستاره‌ی «خرج یک‌باره» بالای عدد ستون */
private val STAR_SPACE = 14.dp

/**
 * «۶ ماه اخیر» (طرح «جیبی»): ستون‌های گرد، عدد هر ماه بالایش و اسم ماه زیرش.
 * ماه انتخاب‌شده به رنگ حال جیب ([highlight]) و پررنگ؛ بقیه کم‌رنگ. ستون‌ها موقع آمدن یکی‌یکی قد می‌کشند.
 * همه‌ی ستون‌ها روی یک خط پایه‌اند: جای عدد بالای ستون جدا و ثابت است و قد ستون را کم نمی‌کند.
 * خط نازک افقی: میانگین ماه‌های تمام‌شده. ماه جاری «تا امروز» است و قاب خط‌چینش پیش‌بینی آخر ماه را نشان می‌دهد.
 * کنار عنوان: چند درصد کمتر/بیشتر از ماه قبل؛ برای ماه جاری فقط «تا همین موقعِ ماه قبل» (نه ماه کامل).
 * ستون‌ها بدون خرج یک‌باره‌اند (خرید خانه‌ای ۱۰ میلیاردی بقیه‌ی ماه‌ها را صاف نکند)؛ ماهی که داشته ستاره می‌خورد
 * و مبلغش زیر نمودار گفته می‌شود.
 */
@Composable
fun TrendCard(trend: SpendTrend, highlight: Color = JibitoTheme.colors.moodCalm) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val months = trend.months
    if (months.isEmpty()) return
    val hidden = LocalHideAmounts.current
    // قد ستون‌ها و قاب پیش‌بینی بدون خرج یک‌باره
    val currentOneOff = months.last().spentRial - months.last().routineRial
    val projected = trend.projectedRial?.let { it - currentOneOff }?.takeIf { trend.isCurrent && it > months.last().routineRial }
    val max = maxOf(months.maxOf { it.routineRial }, projected ?: 0L).coerceAtLeast(1L)
    val oneOffMonths = months.filter { it.spentRial > it.routineRial }
    // یک واحد برای همه‌ی ستون‌ها: میلیون، یا هزار اگر همه کمتر از یک میلیون‌اند
    val unit = if (max / 10 >= 1_000_000L) 1_000_000L else 1_000L
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val toman = stringResource(R.string.unit_toman)
    val oneOffName = stringResource(R.string.sheet_one_off)
    val unitName = stringResource(if (unit == 1_000_000L) R.string.trend_unit_million else R.string.trend_unit_thousand)
    val average = trend.averageRial?.takeIf { months.size > 2 }
    // جای ثابت عدد بالای ستون (با فونت بزرگ، بیشتر)
    val valueSpace = (if (largeText) 40.dp else 26.dp) + (if (oneOffMonths.isNotEmpty() && !hidden) STAR_SPACE else 0.dp)
    fun barHeight(rial: Long) = MAX_BAR * (rial.toFloat() / max).coerceIn(0.05f, 1f)

    // مقایسه با ماه قبل: ماه جاری فقط با «همین موقعِ ماه قبل»؛ ماه تمام‌شده با ماه کامل قبل
    val previous = months.getOrNull(months.lastIndex - 1)
    val percent: Int? = if (trend.isCurrent) {
        trend.vsLastMonthPercent
    } else {
        // بدون خرج‌های یک‌باره (خرید خانه…)، تا مقایسه معنی داشته باشد
        previous?.takeIf { it.routineRial > 0 }?.let {
            ((months.last().routineRial - it.routineRial) * 100.0 / it.routineRial).roundToInt()
        }
    }

    Column(Modifier.padding(top = 26.dp)) {
        SectionHeader(
            title = Jalali.toPersianDigits(stringResource(R.string.trend_title, months.size)),
            note = if (percent != null && previous != null) {
                val name = Jalali.MONTH_NAMES[previous.month.month - 1]
                when {
                    abs(percent) < SAME_PERCENT -> stringResource(if (trend.isCurrent) R.string.trend_same_sofar else R.string.trend_same, name)
                    percent < 0 -> Jalali.toPersianDigits(stringResource(if (trend.isCurrent) R.string.trend_less_sofar else R.string.trend_less, -percent, name))
                    else -> Jalali.toPersianDigits(stringResource(if (trend.isCurrent) R.string.trend_more_sofar else R.string.trend_more, percent, name))
                }
            } else null,
            noteColor = if (percent != null && percent >= SAME_PERCENT) t.alert else t.teal,
            noteBold = true,
        )
        if (!hidden) {
            Text(
                unitName,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp),
                fontSize = 11.sp,
                color = t.muted,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp)
        ) {
            // خط میانگین ماه‌های تمام‌شده (زیر ستون‌ها و عددها کشیده می‌شود)
            if (average != null && !hidden) {
                val y = barHeight(average)
                val lineColor = t.faint
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = y)
                        .fillMaxWidth()
                        .height(1.dp)
                        // خط پر و نازک (خط‌چین مال پیش‌بینی ماه جاری است)
                        .background(lineColor)
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(MAX_BAR + valueSpace)
                    .drawBehind {
                        // خط پایه
                        drawLine(t.border, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                months.forEachIndexed { i, m ->
                    val current = i == months.lastIndex
                    val grow = remember(trend) { Animatable(0f) }
                    LaunchedEffect(trend) {
                        grow.animateTo(1f, tween(800, delayMillis = i * 70, easing = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)))
                    }
                    val h = if (m.routineRial == 0L) 4.dp else barHeight(m.routineRial)
                    val oneOff = m.spentRial - m.routineRial
                    val frame = if (current && projected != null) barHeight(projected) else null
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clearAndSetSemantics {
                                contentDescription = when {
                                    hidden -> m.month.title
                                    oneOff > 0 -> "${m.month.title}: ${Money.compact(m.routineRial)} $toman + ${Money.compact(oneOff)} $toman $oneOffName"
                                    else -> "${m.month.title}: ${Money.compact(m.spentRial)} $toman"
                                }
                            },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        // قاب خط‌چین پیش‌بینی آخر ماه (فقط ماه جاری)
                        if (frame != null) {
                            val frameColor = highlight
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(frame)
                                    .drawBehind {
                                        val stroke = 1.5.dp.toPx()
                                        drawRoundRect(
                                            frameColor.copy(alpha = 0.7f),
                                            topLeft = Offset(stroke / 2, stroke / 2),
                                            size = Size(size.width - stroke, size.height - stroke),
                                            cornerRadius = CornerRadius(10.dp.toPx()),
                                            style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                                        )
                                    }
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(h)
                                .graphicsLayer {
                                    scaleY = grow.value
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                }
                                .background(
                                    if (current) highlight else t.trendOff,
                                    RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
                                )
                        )
                        // عدد ستون: همیشه بالای بلندترِ ستون و قاب، در جای ثابت خودش؛ ستاره یعنی این ماه خرج یک‌باره هم داشت
                        Column(
                            Modifier.padding(bottom = maxOf(h, frame ?: 0.dp) + 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (oneOff > 0 && !hidden) {
                                Icon(DesignIcons.Star, contentDescription = null, tint = t.sugFg, modifier = Modifier.size(12.dp))
                            }
                            Text(
                                if (hidden) "••" else Money.inUnit(m.routineRial, unit),
                                modifier = Modifier
                                    // زمینه‌ی هم‌رنگ صفحه تا خط میانگین از روی عدد رد نشود
                                    .background(colors.background)
                                    .padding(horizontal = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (current) (if (t.dark) colors.onBackground else highlight) else t.faint,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        // اسم ماه‌ها، جدا از ستون‌ها (تا طول اسم خط پایه را جابه‌جا نکند)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            months.forEachIndexed { i, m ->
                val current = i == months.lastIndex
                Column(Modifier.weight(1f).clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        // فونت بزرگ گوشی: شماره‌ی ماه به جای اسم
                        if (largeText) Jalali.toPersianDigits(m.month.month.toString()) else Jalali.MONTH_NAMES[m.month.month - 1],
                        fontSize = 12.sp,
                        fontWeight = if (current) FontWeight.Black else FontWeight.Medium,
                        color = if (current) colors.onBackground else t.muted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (current && trend.isCurrent) {
                        Text(stringResource(R.string.trend_so_far), fontSize = 10.sp, color = t.muted, maxLines = 1)
                    }
                }
            }
        }
        if (average != null && !hidden) {
            Text(
                Jalali.toPersianDigits(stringResource(R.string.trend_average, Money.inUnit(average, unit), unitName)),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                fontSize = 11.sp,
                color = t.muted,
            )
        }
        if (oneOffMonths.isNotEmpty() && !hidden) {
            val list = oneOffMonths.joinToString("، ") {
                Jalali.MONTH_NAMES[it.month.month - 1] + " " + Money.compact(it.spentRial - it.routineRial)
            }
            Text(
                Jalali.toPersianDigits(stringResource(R.string.trend_one_off_note, list)),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
                fontSize = 11.sp,
                color = t.sugFg,
            )
        }
    }
}
