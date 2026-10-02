package ir.jibito.app.ui.summary

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.data.repository.CategorySpend
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.notify.BudgetLevel
import ir.jibito.app.ui.theme.LocalJibitoColors
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import ir.jibito.app.ui.theme.JibitoIcons


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

/** یک مورد «کار لازم» */
data class AttentionItem(val icon: ImageVector, val text: String, val tone: Tone, val onClick: () -> Unit) {
    enum class Tone { NORMAL, WARN, DANGER }
}

/** ۳) چه کاری لازم است؟ — فقط وقتی چیزی هست؛ هر مورد با یک لمس به همان‌جا می‌برد */
@Composable
fun AttentionCard(items: List<AttentionItem>) {
    if (items.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(22.dp))
            .padding(vertical = 6.dp)
    ) {
        items.forEach { item ->
            val tint = when (item.tone) {
                AttentionItem.Tone.DANGER -> colors.error
                AttentionItem.Tone.WARN -> JibitoTheme.colors.warning
                AttentionItem.Tone.NORMAL -> colors.primary
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = item.onClick)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.size(10.dp))
                Text(
                    item.text,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(JibitoIcons.ChevronForward, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** حداکثر چند دسته در نمای «یک نگاه» */
private const val GLANCE_ROWS = 5

/**
 * «خرج‌ها کجا رفته؟» + بودجه‌ها، در یک کارت:
 * نوار سهم (۵ دسته‌ی بزرگ + بقیه) و زیرش هر دسته در یک خط: رنگ، اسم، مبلغ، درصد بودجه (یا سهم).
 * دسته‌ای که بودجه دارد ولی هنوز خرج ندارد هم می‌آید. «همه‌ی دسته‌ها و بودجه‌ها ›» فهرست کامل را باز می‌کند.
 */
@Composable
fun WhereCard(s: MonthSummary, onOpenCategory: (Long) -> Unit, onShowAll: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val total = s.totalSpentRial
    val spent = s.categories.filter { it.spentRial > 0 }.take(GLANCE_ROWS)
    val planned = s.categories.filter { it.spentRial == 0L && it.budgetRial != null }
    val rows = (spent + planned).take(GLANCE_ROWS + 1)
    val rest = total - spent.sumOf { it.spentRial }
    var highlighted by rememberSaveable(s.month.key) { mutableStateOf<Long?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(
            stringResource(R.string.chart_where_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(10.dp))

        if (total > 0) {
            // نوار سهم‌ها: فاصله‌ی ۲ پیکسلی بین تکه‌ها همان رنگ کارت است
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .semantics { contentDescription = spent.joinToString("، ") { "${it.name} ${share(it.spentRial, total)}" } },
            ) {
                val segments = spent.map { it.categoryId to (it.spentRial to ChartColors.forCategory(it.colorHex, dark)) } +
                    if (rest > 0) listOf(-1L to (rest to ChartColors.neutral(dark))) else emptyList()
                segments.forEachIndexed { i, (key, value) ->
                    if (i > 0) Spacer(Modifier.width(2.dp).fillMaxHeight().background(colors.surface))
                    val a by animateFloatAsState(if (highlighted == null || highlighted == key) 1f else 0.3f, label = "seg")
                    Box(
                        Modifier
                            .weight(maxOf(value.first.toFloat() / total, 0.02f))
                            .fillMaxHeight()
                            .alpha(a)
                            .background(value.second)
                            .clickable { highlighted = if (highlighted == key) null else key },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Text(
                stringResource(R.string.glance_no_spend),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }

        rows.forEach { c ->
            GlanceRow(c, total, dark, highlighted == c.categoryId, onClick = { onOpenCategory(c.categoryId) })
        }
        if (rest > 0) {
            GlanceRestRow(rest, total, dark, highlighted == -1L)
        }

        Text(
            stringResource(R.string.glance_show_all),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onShowAll)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
        )
    }
}

@Composable
private fun GlanceRow(c: CategorySpend, total: Long, dark: Boolean, highlighted: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val base = ChartColors.forCategory(c.colorHex, dark)
    val budget = c.budgetRial?.takeIf { it > 0 }
    val level = if (budget != null) BudgetLevel.of(c.spentRial, budget) else 0
    val status = when (level) {
        100 -> colors.error
        80 -> JibitoTheme.colors.warning
        else -> null
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) colors.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(base))
            Spacer(Modifier.size(8.dp))
            Text(
                listOfNotNull(c.icon, c.name).joinToString(" "),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (budget != null && c.spentRial == 0L) stringResource(R.string.glance_of_budget, Money.compact(budget))
                else Money.compact(c.spentRial),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                Jalali.toPersianDigits(
                    if (budget != null) "${(c.spentRial * 100 / budget).coerceAtMost(999)}٪" else share(c.spentRial, total)
                ),
                modifier = Modifier.width(40.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (status != null) FontWeight.Black else FontWeight.Normal,
                color = status ?: colors.onSurfaceVariant,
            )
        }
        if (budget != null) {
            // نوار باریک بودجه زیر همان خط
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background((status ?: base).copy(alpha = 0.15f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((c.spentRial.toFloat() / budget).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(status ?: base)
                )
            }
        }
    }
}

@Composable
private fun GlanceRestRow(rest: Long, total: Long, dark: Boolean, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) colors.surfaceVariant else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(ChartColors.neutral(dark)))
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.chart_rest),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Text(Money.compact(rest), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Spacer(Modifier.size(8.dp))
        Text(
            Jalali.toPersianDigits(share(rest, total)),
            modifier = Modifier.width(40.dp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

private fun share(amount: Long, total: Long): String {
    if (total <= 0) return "۰٪"
    val p = amount * 100.0 / total
    return if (p > 0 && p < 1) "<۱٪" else "${p.toInt()}٪"
}
