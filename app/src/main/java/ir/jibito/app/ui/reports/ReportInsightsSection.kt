package ir.jibito.app.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.repository.Insight
import ir.jibito.app.ui.common.CategoryIconTile
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.summary.SectionHeader
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
import kotlin.math.abs
import kotlin.math.roundToInt

// ── «جیبی چی فهمید؟» ──

@Composable
internal fun InsightsSection(insights: List<Insight>, month: JalaliMonth) {
    Column(Modifier.padding(top = 24.dp)) {
        SectionHeader(title = stringResource(R.string.reports_insights_title))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            items(insights) { InsightCard(it, month) }
        }
    }
}

@Composable
private fun InsightCard(insight: Insight, month: JalaliMonth) {
    val t = JibitoTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .width(200.dp)
            .heightIn(min = 172.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, t.border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (insight) {
            is Insight.CategoryChange -> {
                val up = insight.deltaRial > 0
                val previous = monthName(month.plus(-1))
                CategoryIconTile(categoryTint(insight.colorHex, insight.icon), size = 36.dp, radius = 12.dp, iconSize = 20.dp)
                InsightTexts(
                    title = insight.name,
                    value = Jalali.toPersianDigits(stringResource(if (up) R.string.reports_insight_more else R.string.reports_insight_less, abs(insight.percent))),
                    valueColor = if (up) t.amberTintFg else t.tealTintFg,
                    note = stringResource(
                        if (up) R.string.reports_insight_more_note else R.string.reports_insight_less_note,
                        amount(Money.compact(abs(insight.deltaRial))),
                        previous,
                    ),
                )
            }
            is Insight.BusiestDay -> {
                IconTile(DesignIcons.CALENDAR, t.navInd, t.navOn)
                InsightTexts(
                    title = stringResource(R.string.reports_busiest_title),
                    value = dayLabel(insight.day, insight.month),
                    valueColor = t.navOn,
                    note = Jalali.toPersianDigits(stringResource(R.string.reports_busiest_note, amount(Money.compact(insight.amountRial)), insight.count)),
                )
            }
            is Insight.WeekdayPeak -> {
                val day = Jalali.WEEKDAY_NAMES[insight.weekday - 1]
                val times = formatTimes(insight.times)
                IconTile(DesignIcons.REPEAT, t.amberTint, t.amberTintFg)
                InsightTexts(
                    title = stringResource(R.string.reports_weekday_title, day),
                    value = stringResource(R.string.reports_weekday_value, times),
                    valueColor = t.amberTintFg,
                    note = stringResource(R.string.reports_weekday_note, day, times),
                )
            }
        }
    }
}

@Composable
private fun IconTile(path: String, bg: Color, fg: Color) {
    val icon = remember(path) { DesignIcons.svg("insight", path) }
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun InsightTexts(title: String, value: String, valueColor: Color, note: String) {
    val t = JibitoTheme.colors
    Spacer(Modifier.height(4.dp))
    Text(title, fontSize = 12.sp, color = t.muted, maxLines = 1)
    Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = valueColor, maxLines = 1)
    Text(note, fontSize = 12.sp, lineHeight = 20.sp, color = t.muted)
}

/** ۱٫۸۴ ← «۱٫۸»، ۲٫۰۳ ← «۲» */
private fun formatTimes(times: Double): String {
    val tenths = (times * 10).roundToInt()
    val text = if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}٫${tenths % 10}"
    return Jalali.toPersianDigits(text)
}
