package ir.jibito.app.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.data.repository.SpendTrend
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/**
 * روند خرج چند ماه اخیر: ستون‌های باریک با یک رنگ (یک سری داده، پس بدون راهنما).
 * فقط مبلغ ماه انتخاب‌شده نوشته می‌شود (پیش‌فرض: همین ماه)؛ لمس هر ستون آن ماه را انتخاب می‌کند.
 * هر ستون برای صفحه‌خوان «ماه: مبلغ» را می‌گوید.
 */
@Composable
fun TrendCard(trend: SpendTrend) {
    val colors = MaterialTheme.colorScheme
    val months = trend.months
    var selected by remember(trend) { mutableIntStateOf(months.lastIndex) }
    val max = months.maxOf { it.spentRial }.coerceAtLeast(1L)
    val picked = months[selected.coerceIn(0, months.lastIndex)]
    val toman = stringResource(R.string.unit_toman)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .background(colors.surface, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Text(
            Jalali.toPersianDigits(stringResource(R.string.trend_title, months.size)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = colors.onSurface,
        )
        Text(
            "${picked.month.title}: ${Money.compact(picked.spentRial)} $toman",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            months.forEachIndexed { i, m ->
                val label = "${m.month.title}: ${Money.compact(m.spentRial)} $toman"
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selected = i }
                        .semantics {
                            contentDescription = label
                            this.selected = i == selected
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val fraction = if (m.spentRial == 0L) 0.02f else (m.spentRial.toFloat() / max).coerceIn(0.04f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (i == selected) colors.primary else colors.primary.copy(alpha = 0.32f))
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            months.forEachIndexed { i, m ->
                Text(
                    Jalali.MONTH_NAMES[m.month.month - 1],
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == selected) colors.onSurface else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
