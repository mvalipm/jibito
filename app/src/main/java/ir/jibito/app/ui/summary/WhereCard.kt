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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.data.repository.CategorySpend
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.notify.BudgetLevel
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

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
