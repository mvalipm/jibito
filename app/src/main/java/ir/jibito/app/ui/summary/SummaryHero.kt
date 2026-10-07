package ir.jibito.app.ui.summary

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.wallet.BalanceForecast
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.ui.common.LocalHideAmounts
import ir.jibito.app.ui.main.NavIcons
import ir.jibito.app.ui.common.HIDDEN_AMOUNT
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.Vazirmatn
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money

/** حال جیب این ماه: رنگ سرصفحه و جمله‌ی زیرش از همین می‌آید */
enum class Mood { CALM, WARN, OVER }

/** حال جیب و جمله‌اش (منطق جدا از ظاهر، برای تست) */
data class MoodLine(val mood: Mood, val sentence: Int, val args: List<Any> = emptyList())

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** روزهای مانده تا آخر ماه (با امروز)؛ برای ماه‌های گذشته ۰ */
fun daysLeft(s: MonthSummary, nowMillis: Long): Int {
    val end = s.month.endMillis()
    if (nowMillis < s.month.startMillis() || nowMillis >= end) return 0
    return ((end - nowMillis + DAY_MILLIS - 1) / DAY_MILLIS).toInt().coerceAtLeast(1)
}

/**
 * - بیشتر از بودجه ← «بیرون زد» (قرمز)
 * - ۸۰٪ بودجه رفته، یا با همین ریتم آخر ماه بیرون می‌زند ← «یواش‌تر» (کهربایی)
 * - بقیه ← «آروم» (فیروزه‌ای)؛ بدون بودجه هم آروم است و پیشنهاد سقف می‌دهد.
 */
fun moodOf(s: MonthSummary, nowMillis: Long): MoodLine {
    val budget = s.overallBudgetRial?.takeIf { it > 0 }
    // خرج یک‌باره (خرید خانه…) از بودجه کم نمی‌شود
    val spent = if (budget == null) s.totalSpentRial else s.budgetSpentRial
    if (budget == null) {
        return MoodLine(Mood.CALM, if (spent == 0L) R.string.hero_no_spend else R.string.hero_no_budget)
    }
    if (spent > budget) return MoodLine(Mood.OVER, R.string.hero_over, listOf(Money.compact(spent - budget)))
    val time = s.timeFraction(nowMillis)
        ?: return MoodLine(Mood.CALM, R.string.hero_past_calm, listOf(Money.compact(budget - spent)))
    val percent = (spent * 100 / budget).toInt()
    val days = daysLeft(s, nowMillis)
    if (percent >= 80) {
        return if (days <= 1) MoodLine(Mood.WARN, R.string.hero_warn_last_day, listOf(percent))
        else MoodLine(Mood.WARN, R.string.hero_warn, listOf(percent, days))
    }
    // چند روز اول ماه، پیش‌بینی معنی ندارد
    if (time < 0.1f) return MoodLine(Mood.CALM, R.string.hero_calm_start)
    val projected = (spent / time.toDouble()).toLong()
    return when {
        projected > budget * 105 / 100 -> MoodLine(Mood.WARN, R.string.hero_warn_pace, listOf(Money.compact(projected - budget)))
        budget - projected >= budget * 3 / 100 -> MoodLine(Mood.CALM, R.string.hero_calm, listOf(Money.compact(budget - projected)))
        else -> MoodLine(Mood.CALM, R.string.hero_calm_on_track)
    }
}

/** خمیدگی پایین سرصفحه */
private val CURVE = 76.dp

/**
 * سرصفحه‌ی رنگی «خلاصه» (طرح «جیبی»): رنگش حال جیب است (آروم/یواش‌تر/بیرون زد).
 * دکمه‌ی ماه، روشن/تیره، پنهان کردن مبلغ‌ها و تنظیمات؛ عدد درشت خرج ماه که از صفر بالا می‌آید؛
 * نوار بودجه با خط «امروز»؛ و یک جمله که حال جیب را می‌گوید. لمس عدد ← تعیین بودجه‌ی کل.
 */
@Composable
fun SummaryHero(
    s: MonthSummary,
    onPickMonth: (JalaliMonth) -> Unit,
    onEditBudget: () -> Unit,
    dark: Boolean,
    onToggleDark: () -> Unit,
    onToggleHidden: () -> Unit,
    onOpenSettings: () -> Unit = {},
    nowMillis: Long = System.currentTimeMillis(),
    /** «پولت تا حقوق می‌رسه؟»؛ فقط ماه جاری، و وقتی مبلغ‌ها پنهان نیستند */
    forecast: BalanceForecast? = null,
    onOpenForecast: () -> Unit = {},
) {
    val t = JibitoTheme.colors
    val hidden = LocalHideAmounts.current
    val line = moodOf(s, nowMillis)
    val moodColor by animateColorAsState(
        when (line.mood) {
            Mood.CALM -> t.moodCalm
            Mood.WARN -> t.moodWarn
            Mood.OVER -> t.moodOver
        },
        animationSpec = tween(700),
        label = "mood",
    )
    val isCurrent = s.month == JalaliMonth.of(nowMillis)
    val budget = s.overallBudgetRial?.takeIf { it > 0 }

    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val w = size.width
                val h = size.height
                val d = CURVE.toPx()
                val sx = w / 390f
                val body = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h - d)
                    cubicTo(w, h - d + 34.dp.toPx(), 300 * sx, h - 14.dp.toPx(), w / 2, h)
                    cubicTo(90 * sx, h - 14.dp.toPx(), 0f, h - d + 34.dp.toPx(), 0f, h - d)
                    close()
                }
                drawPath(body, moodColor)
                // خط‌چین تزئینی موازی لبه‌ی پایین
                val dash = Path().apply {
                    moveTo(18 * sx, h - 82.dp.toPx())
                    cubicTo(18 * sx, h - 54.dp.toPx(), 100 * sx, h - 32.dp.toPx(), w / 2, h - 20.dp.toPx())
                    cubicTo(290 * sx, h - 32.dp.toPx(), 372 * sx, h - 54.dp.toPx(), 372 * sx, h - 82.dp.toPx())
                }
                drawPath(
                    dash,
                    Color.White.copy(alpha = 0.4f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 8.dp.toPx())),
                    ),
                )
                // دو دایره‌ی کم‌رنگ (در راست‌به‌چپ هم همان‌جای طرح)
                drawCircle(Color.White.copy(alpha = 0.08f), radius = 40.dp.toPx(), center = Offset(330 * sx, (-6).dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.06f), radius = 70.dp.toPx(), center = Offset(52 * sx, 250.dp.toPx()))
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = CURVE + 4.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ماه + روشن/تیره + چشم + تنظیمات
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MonthPill(s.month, onPickMonth)
                Spacer(Modifier.weight(1f))
                HeroButton(
                    if (dark) DesignIcons.Sun else DesignIcons.Moon,
                    stringResource(R.string.cd_toggle_dark),
                    onToggleDark,
                )
                Spacer(Modifier.width(8.dp))
                HeroButton(
                    if (hidden) DesignIcons.EyeOff else DesignIcons.Eye,
                    stringResource(if (hidden) R.string.cd_show_amounts else R.string.cd_hide_amounts),
                    onToggleHidden,
                )
                // تنظیمات دیگر تب نیست: گوشه‌ی بالا-چپ (در راست‌به‌چپ، ته ردیف)
                Spacer(Modifier.width(8.dp))
                HeroButton(NavIcons.Settings.normal, stringResource(R.string.settings_title), onOpenSettings)
            }

            // «خرج این ماه» و عدد درشت، وسط سرصفحه
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (isCurrent) stringResource(R.string.hero_label_current) else stringResource(R.string.summary_spent, s.month.title),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                BigNumber(s.totalSpentRial, hidden, onEditBudget)
            }

            // نوار بودجه
            if (budget != null) {
                BudgetBar(
                    spent = (s.budgetSpentRial.toFloat() / budget).coerceIn(0f, 1f),
                    today = s.timeFraction(nowMillis),
                    marker = moodColor,
                )
                Row(Modifier.fillMaxWidth().padding(top = 0.dp)) {
                    Text(
                        Jalali.toPersianDigits(
                            stringResource(
                                R.string.hero_of_budget,
                                "${(s.budgetSpentRial * 100 / budget).coerceAtMost(999)}٪",
                                if (hidden) HIDDEN_AMOUNT else Money.compactAdjective(budget),
                            )
                        ),
                        modifier = Modifier.weight(1f),
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    val days = daysLeft(s, nowMillis)
                    Text(
                        when {
                            !isCurrent -> stringResource(R.string.hero_month_done)
                            days <= 1 -> stringResource(R.string.hero_last_day)
                            else -> Jalali.toPersianDigits(stringResource(R.string.hero_days_left, days))
                        },
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                // خرج یک‌باره جدا از بودجه (عدد درشت بالا آن را دارد، نوار بودجه نه)
                if (s.oneOffRial > 0) {
                    Text(
                        Jalali.toPersianDigits(
                            stringResource(R.string.hero_one_off, if (hidden) HIDDEN_AMOUNT else Money.compact(s.oneOffRial))
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.overall_set_budget),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(onClick = onEditBudget)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // جمله‌ی حال جیب
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (line.mood) {
                        Mood.CALM -> DesignIcons.Calm
                        Mood.WARN -> DesignIcons.Warn
                        Mood.OVER -> DesignIcons.Over
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                val args = line.args.map { if (hidden && it is String) HIDDEN_AMOUNT else it }.toTypedArray()
                Text(
                    Jalali.toPersianDigits(stringResource(line.sentence, *args)),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp,
                )
            }
            if (forecast != null && isCurrent && !hidden) HeroForecastLine(forecast, onOpenForecast)
        }
    }
}

@Composable
private fun MonthPill(month: JalaliMonth, onPick: (JalaliMonth) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .clickable { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(month.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Icon(DesignIcons.ChevronDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val current = JalaliMonth.current()
            (0 until 12).map { current.plus(-it) }.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.title, fontWeight = if (m == month) FontWeight.Black else FontWeight.Normal) },
                    onClick = {
                        open = false
                        onPick(m)
                    },
                )
            }
        }
    }
}

@Composable
private fun HeroButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

/**
 * عدد درشت خرج ماه؛ با هر بار باز شدن یا عوض شدن، نرم از عدد قبلی به عدد تازه می‌رسد.
 * اگر عدد و واحدش در عرض جا نشوند (عدد بلند، صفحه‌ی باریک، فونت بزرگ گوشی)، هر دو با هم کوچک می‌شوند
 * تا هیچ رقمی از لبه بیرون نزند.
 */
@Composable
private fun BigNumber(spentRial: Long, hidden: Boolean, onClick: () -> Unit) {
    val (_, unit) = Money.compactParts(spentRial)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(spentRial) {
        anim.animateTo(spentRial.toFloat(), tween(900, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)))
    }
    val v = anim.value.toLong()
    val number = when {
        spentRial / 10 >= 1_000_000_000L -> Money.inUnit(v, 1_000_000_000L)
        spentRial / 10 >= 1_000_000L -> Money.inUnit(v, 1_000_000L)
        spentRial / 10 >= 1_000L -> Jalali.toPersianDigits((v / 10 / 1_000).toString())
        else -> Jalali.toPersianDigits((v / 10).toString())
    }
    val shown = if (hidden) HIDDEN_AMOUNT else number
    val unitText = if (hidden || unit.isEmpty()) stringResource(R.string.unit_toman) else "$unit ${stringResource(R.string.unit_toman)}"
    // فونت خیلی بزرگ گوشی: عدد درشت کمی کوچک‌تر، تا از صفحه بیرون نزند
    val scale = LocalDensity.current.fontScale
    val base = if (scale > 1.15f) 84f * 1.15f / scale else 84f
    // عرض را با عدد نهایی می‌سنجیم (نه عدد در حال شمارش) تا اندازه وسط انیمیشن نپرد؛
    // اعشار هم حساب می‌شود چون عدد در حال شمارش (مثلاً «۶۹٫۹» پیش از «۷۰») اعشار دارد
    val final = when {
        hidden -> HIDDEN_AMOUNT
        spentRial / 10 >= 1_000_000L -> Money.compactParts(spentRial).first.substringBefore('٫') + "٫۸"
        else -> Money.compactParts(spentRial).first
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val fit = remember(final, unitText, base, maxWidth) {
            val numberW = measurer.measure(final, bigNumberStyle(base.sp), softWrap = false).size.width
            val unitW = measurer.measure(unitText, unitStyle(19.sp), softWrap = false).size.width
            val needed = numberW + unitW + with(density) { 10.dp.toPx() }
            val room = with(density) { maxWidth.toPx() }
            if (needed > room) (room / needed).coerceAtLeast(0.4f) else 1f
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClickLabel = stringResource(R.string.overall_dialog_title), onClick = onClick),
        ) {
            Text(
                shown,
                style = bigNumberStyle((base * fit).sp),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(10.dp * fit))
            Text(
                unitText,
                style = unitStyle((19f * fit).sp),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/**
 * ارقام همیشه چپ‌به‌راست؛ ارتفاع خط آن‌قدر هست که ممیز «٫» (که زیر خط پایین می‌آید) بریده نشود.
 */
private fun bigNumberStyle(size: TextUnit) = TextStyle(
    color = Color.White,
    fontFamily = Vazirmatn,
    fontSize = size,
    fontWeight = FontWeight.Black,
    lineHeight = size * 1.3f,
    textDirection = TextDirection.Ltr,
)

private fun unitStyle(size: TextUnit) = TextStyle(
    color = Color.White.copy(alpha = 0.9f),
    fontFamily = Vazirmatn,
    fontSize = size,
    fontWeight = FontWeight.Bold,
)

/** نوار سفید مصرف بودجه با خط «امروز» (کجای ماه هستیم) */
@Composable
private fun BudgetBar(spent: Float, today: Float?, marker: Color) {
    val fill by animateFloatAsState(spent, tween(900, easing = CubicBezierEasing(0.34f, 1.3f, 0.64f, 1f)), label = "budgetFill")
    BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.24f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fill.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
            )
        }
        if (today != null) {
            // خط ۳ پیکسلی به رنگ حال جیب با حلقه‌ی سفید دورش
            val x: Dp = (maxWidth * today.coerceIn(0f, 1f) - 3.5.dp).coerceAtLeast(0.dp)
            Box(
                Modifier
                    .offset(x = x)
                    .width(7.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.5.dp))
                    .background(Color.White)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(marker)
            )
        }
    }
}
