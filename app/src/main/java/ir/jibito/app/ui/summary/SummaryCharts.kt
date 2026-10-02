package ir.jibito.app.ui.summary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.category.CategoryPalette
import ir.jibito.app.data.repository.CategorySpend
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.notify.BudgetLevel
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/**
 * رنگ‌های نمودار در حالت روشن/تیره. هر دسته‌ی اصلی یک رنگ ثابت از پالت اعتبارسنجی‌شده دارد
 * (CategoryPalette)؛ در حالت تیره همان رنگ، پله‌ی تیره‌اش را می‌گیرد.
 */
object ChartColors {
    private val LIGHT = CategoryPalette.LIGHT.map { it.toColor() }
    private val DARK = CategoryPalette.DARK.map { it.toColor() }
    private val NEUTRAL_LIGHT = Color(0xFFA3A29C)
    private val NEUTRAL_DARK = Color(0xFF6E6D68)

    fun forCategory(hex: String?, dark: Boolean): Color {
        val i = CategoryPalette.LIGHT.indexOfFirst { it.equals(hex, ignoreCase = true) }
        return when {
            i >= 0 -> if (dark) DARK[i] else LIGHT[i]
            hex == null || hex.equals(CategoryPalette.NEUTRAL, ignoreCase = true) -> neutral(dark)
            else -> runCatching { hex.toColor() }.getOrElse { neutral(dark) }
        }
    }

    fun neutral(dark: Boolean): Color = if (dark) NEUTRAL_DARK else NEUTRAL_LIGHT

    private fun String.toColor(): Color = Color(android.graphics.Color.parseColor(this))
}

/** یک تکه از نوار «خرج‌ها کجا رفته» */
private data class Segment(val key: Long, val label: String, val icon: String?, val amount: Long, val color: Color, val category: CategorySpend?)

/** حداکثر چند دسته جدا نشان داده شود؛ بقیه یک تکه‌ی خاکستری می‌شوند (نمودار سهم: حداکثر ۶ تکه) */
private const val MAX_NAMED_SEGMENTS = 5

/**
 * «خرج‌ها کجا رفته»: یک نوار افقیِ تکه‌تکه (سهم هر دسته‌ی اصلی از کل خرج ماه).
 * - حداکثر ۵ دسته‌ی بزرگ + «بقیه» (خاکستری).
 * - بین تکه‌ها ۲ پیکسل فاصله؛ زیرش راهنما با اسم، درصد و مبلغ (هویت هیچ‌وقت فقط با رنگ نیست).
 * - لمس یک تکه یا ردیف راهنما، همان را برجسته می‌کند؛ لمس دوباره‌ی ردیف، جزئیات دسته را باز می‌کند.
 */
@Composable
fun SpendBreakdownCard(s: MonthSummary, onOpenCategory: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val total = s.totalSpentRial
    if (total <= 0) return

    val named = s.categories.filter { it.spentRial > 0 }.take(MAX_NAMED_SEGMENTS)
    val rest = total - named.sumOf { it.spentRial }
    val segments = named.map {
        Segment(it.categoryId, it.name, it.icon, it.spentRial, ChartColors.forCategory(it.colorHex, dark), it)
    } + if (rest > 0) {
        listOf(Segment(-1, stringResource(R.string.chart_rest), "•", rest, ChartColors.neutral(dark), null))
    } else {
        emptyList()
    }
    var highlighted by rememberSaveable(s.month.key) { mutableStateOf<Long?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.chart_where_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(12.dp))

        // نوار سهم‌ها؛ فاصله‌ی ۲ پیکسلی بین تکه‌ها همان رنگ کارت است
        Row(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .semantics { contentDescription = segments.joinToString("، ") { "${it.label} ${percentOf(it.amount, total)}" } },
        ) {
            segments.forEachIndexed { i, seg ->
                if (i > 0) Spacer(Modifier.width(2.dp).fillMaxHeight().background(colors.surface))
                val dim by animateFloatAsState(
                    if (highlighted == null || highlighted == seg.key) 1f else 0.3f,
                    label = "segment",
                )
                Box(
                    Modifier
                        // تکه‌های خیلی کوچک هم دیده شوند
                        .weight(maxOf(seg.amount.toFloat() / total, 0.02f))
                        .fillMaxHeight()
                        .alpha(dim)
                        .background(seg.color)
                        .clickable { highlighted = if (highlighted == seg.key) null else seg.key },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // راهنما: اسم، درصد، مبلغ (رنگ فقط روی نقطه است؛ متن با رنگ متن)
        segments.forEach { seg ->
            val selected = highlighted == seg.key
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) colors.surfaceVariant else Color.Transparent)
                    .clickable {
                        if (selected && seg.category != null) onOpenCategory(seg.key)
                        highlighted = if (selected) null else seg.key
                    }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(seg.color)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    listOfNotNull(seg.icon, seg.label).joinToString(" "),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = colors.onSurface,
                )
                Text(
                    Jalali.toPersianDigits(percentOf(seg.amount, total)),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    Money.toman(seg.amount),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
            }
        }
        if (highlighted != null && segments.any { it.key == highlighted && it.category != null }) {
            Text(
                stringResource(R.string.chart_tap_again),
                modifier = Modifier.padding(top = 4.dp, start = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private fun percentOf(amount: Long, total: Long): String {
    if (total <= 0) return "۰٪"
    val p = amount * 100.0 / total
    return if (p > 0 && p < 1) "<۱٪" else "${p.toInt()}٪"
}

/**
 * جزئیات یک دسته‌ی اصلی در این ماه: خرج، بودجه و باقی‌مانده، و اینکه داخل خودش کجا خرج شده (زیردسته‌ها).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailSheet(
    c: CategorySpend,
    monthTitle: String,
    onEditBudget: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val base = ChartColors.forCategory(c.colorHex, dark)
    val budget = c.budgetRial
    val level = if (budget != null) BudgetLevel.of(c.spentRial, budget) else 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(base.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(c.icon ?: "•", fontSize = 20.sp) }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = colors.onSurface)
                        Text(
                            stringResource(R.string.summary_spent, monthTitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Text(Money.toman(c.spentRial), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = colors.onSurface)
                }

                // بودجه
                Spacer(Modifier.height(18.dp))
                if (budget != null && budget > 0) {
                    val fraction = (c.spentRial.toFloat() / budget).coerceIn(0f, 1f)
                    val barColor = when (level) {
                        100 -> colors.error
                        80 -> Color(0xFFF2A541)
                        else -> base
                    }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = barColor,
                        trackColor = barColor.copy(alpha = 0.15f),
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (level == 100) stringResource(R.string.summary_over_budget, Money.toman(c.spentRial - budget))
                        else stringResource(R.string.overall_remaining, Money.toman(budget - c.spentRial), Money.toman(budget)),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (level == 100) colors.error else colors.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onEditBudget, shape = RoundedCornerShape(14.dp)) {
                        Text(stringResource(R.string.detail_edit_budget))
                    }
                } else {
                    Button(onClick = onEditBudget, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.detail_set_budget), fontWeight = FontWeight.Bold)
                    }
                }

                // زیردسته‌ها
                Spacer(Modifier.height(22.dp))
                Text(
                    stringResource(R.string.detail_inside_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                if (c.children.isEmpty()) {
                    Text(
                        stringResource(R.string.detail_inside_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                val max = c.children.maxOfOrNull { it.spentRial } ?: 0L
                c.children.forEach { sub ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                sub.name ?: stringResource(R.string.detail_direct),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurface,
                            )
                            Text(
                                Jalali.toPersianDigits(percentOf(sub.spentRial, c.spentRial)),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                Money.toman(sub.spentRial),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        // طول نوار نسبت به بزرگ‌ترین زیردسته؛ همه از یک خط پایه (راست) شروع می‌شوند
                        Box(Modifier.fillMaxWidth().height(6.dp)) {
                            Box(
                                Modifier
                                    .fillMaxWidth(if (max > 0) (sub.spentRial.toFloat() / max).coerceIn(0.02f, 1f) else 0f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(base),
                            )
                        }
                    }
                }
            }
        }
    }
}
