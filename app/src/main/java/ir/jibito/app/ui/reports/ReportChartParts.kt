package ir.jibito.app.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.common.LocalHideAmounts
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Money

/** انتخاب بازه: ریل خاکستری با گزینه‌ی انتخاب‌شده‌ی روشن (تب «گزارش‌ها» و صفحه‌ی جزئیات حساب) */
@Composable
internal fun SegmentedTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    outerPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(outerPadding)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(t.chip)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) colors.surface else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) colors.onSurface else t.muted,
                )
            }
        }
    }
}

@Composable
internal fun ChartCard(content: @Composable () -> Unit) {
    val t = JibitoTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, t.border, shape)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp)
    ) { content() }
}

/** عنوان کوچک، عدد درشت و کپسول مقایسه بالای نمودار */
@Composable
internal fun Headline(label: String, rial: Long, chip: String?, good: Boolean) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val (number, unit) = Money.compactParts(rial)
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = t.muted, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(amount(number), fontSize = 34.sp, fontWeight = FontWeight.Black, color = colors.onSurface)
                if (!LocalHideAmounts.current) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (unit.isEmpty()) stringResource(R.string.unit_toman) else "$unit ${stringResource(R.string.unit_toman)}",
                        modifier = Modifier.padding(bottom = 8.dp),
                        fontSize = 14.sp,
                        color = t.muted,
                    )
                }
            }
        }
        if (chip != null) {
            Text(
                chip,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(CircleShape)
                    .background(if (good) t.tealTint else t.amberTint)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (good) t.tealTintFg else t.amberTintFg,
                maxLines = 1,
            )
        }
    }
}

/** حباب بالای نقطه‌ی انتخاب‌شده */
@Composable
internal fun Tooltip(title: String, subtitle: String?) {
    val t = JibitoTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .clip(shape)
            .background(t.sheet)
            .border(1.dp, t.border, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = t.muted, maxLines = 1)
    }
}

@Composable
internal fun ReferenceLabel(text: String) {
    val t = JibitoTheme.colors
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(t.amberTint)
            .padding(horizontal = 6.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = t.amberTintFg,
        maxLines = 1,
    )
}

/** برچسب‌های زیر محور افقی (همیشه از چپ به راست، مثل خود نمودار) */
@Composable
internal fun AxisLabels(vararg labels: String) {
    val t = JibitoTheme.colors
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, fontSize = 11.sp, color = t.faint, maxLines = 1) }
        }
    }
}

internal enum class LegendStyle { SOLID, DASHED, DOTTED }

@Composable
internal fun Legend(items: List<Pair<LegendStyle, String>>) {
    val t = JibitoTheme.colors
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (style, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = if (style == LegendStyle.DASHED) t.faint else t.teal
                when (style) {
                    LegendStyle.SOLID -> Box(Modifier.size(14.dp, 3.dp).clip(CircleShape).background(color))
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(if (style == LegendStyle.DASHED) 3 else 4) {
                            Box(Modifier.size(if (style == LegendStyle.DASHED) 4.dp else 2.dp, 2.dp).clip(CircleShape).background(color))
                        }
                    }
                }
                Spacer(Modifier.width(5.dp))
                Text(label, fontSize = 11.sp, color = t.muted, maxLines = 1)
            }
        }
    }
}


@Composable
internal fun Note(text: String, bg: Color, fg: Color) {
    Text(
        text,
        modifier = Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        fontSize = 13.sp,
        lineHeight = 22.sp,
        color = fg,
    )
}

@Composable
internal fun EmptyNote(text: String) {
    Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 14.sp, lineHeight = 24.sp, color = JibitoTheme.colors.muted)
    }
}
