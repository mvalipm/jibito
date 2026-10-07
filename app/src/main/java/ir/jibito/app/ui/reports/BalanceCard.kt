package ir.jibito.app.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.wallet.AccountRef
import ir.jibito.app.data.wallet.AccountTrend
import ir.jibito.app.data.wallet.BalanceHistory
import ir.jibito.app.data.wallet.BalanceOverview
import ir.jibito.app.data.wallet.BalanceSeries
import ir.jibito.app.ui.common.LocalHideAmounts
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.smslist.accountLabel
import ir.jibito.app.ui.smslist.bankColor
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import java.util.Calendar

/**
 * «موجودی حساب‌ها» در بازه‌ی انتخاب‌شده‌ی تب «گزارش‌ها» (ولی تا امروز):
 * جمع حساب‌هایی که در کیف پول حساب می‌شوند (نمودار پله‌ای) و زیرش هر حساب با خط روند کوچکش.
 * لمس هر حساب ← صفحه‌ی جزئیات همان حساب؛ «جزئیات همه‌ی حساب‌ها» ← همان صفحه برای جمع.
 * وقتی مبلغ‌ها پنهان‌اند کل روند پنهان است (از شکل نمودار مبلغ‌ها حدس زده می‌شوند) و دلیلش گفته می‌شود.
 *
 * @param overview null یعنی «هنوز در حال بارگذاری»
 * @param onOpen حسابی که باید جزئیاتش باز شود؛ null یعنی همه‌ی حساب‌ها
 */
@Composable
fun BalanceCard(
    overview: BalanceOverview?,
    onOpen: (AccountRef?) -> Unit,
    nowMillis: Long = System.currentTimeMillis(),
) {
    Column(Modifier.padding(top = 16.dp)) {
        ChartCard { BalanceContent(overview, onOpen, nowMillis) }
    }
}

/**
 * محتوای «موجودی حساب‌ها» بدون قاب (داخل کارت تاشوی تب «گزارش‌ها» یا [BalanceCard]).
 * @param showTitle عنوان کوچک حالت‌های خالی؛ کارت تاشو خودش عنوان دارد
 */
@Composable
internal fun BalanceContent(
    overview: BalanceOverview?,
    onOpen: (AccountRef?) -> Unit,
    nowMillis: Long = System.currentTimeMillis(),
    showTitle: Boolean = true,
) {
    when {
        LocalHideAmounts.current -> {
            if (showTitle) CardTitle(stringResource(R.string.balance_title))
            EmptyNote(stringResource(R.string.balance_hidden))
        }
        overview == null -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        overview.accounts.none { BankDirectory.byId(it.ref.bankId) != null } -> {
            if (showTitle) CardTitle(stringResource(R.string.balance_title))
            EmptyNote(stringResource(R.string.balance_empty))
        }
        // همه‌ی حساب‌ها از جمع بیرون‌اند: جمعی نیست، فقط حساب‌ها
        overview.total.firstKnown == null -> {
            if (showTitle) CardTitle(stringResource(R.string.balance_title))
            Spacer(Modifier.height(8.dp))
            AccountRows(overview.accounts, onOpen, nowMillis)
        }
        else -> {
            BalanceChart(
                series = overview.total,
                label = stringResource(R.string.balance_title),
                lineColor = JibitoTheme.colors.btnBg,
            )
            if (overview.total.hasTrend) {
                Text(
                    stringResource(R.string.balance_total_note),
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    color = JibitoTheme.colors.faint,
                )
            }
            Spacer(Modifier.height(8.dp))
            AccountRows(overview.accounts, onOpen, nowMillis)
            OpenAllRow(onClick = { onOpen(null) })
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, fontSize = 13.sp, color = JibitoTheme.colors.muted, maxLines = 1)
}

/**
 * عدد درشت + نمودار پله‌ای لمسی + برچسب‌های محور یک سری مانده (کارت گزارش‌ها و صفحه‌ی جزئیات).
 * سری بدون روند (کمتر از دو روز معلوم) فقط عدد امروز و یک یادداشت دارد.
 */
@Composable
internal fun BalanceChart(series: BalanceSeries, label: String, lineColor: Color) {
    val t = JibitoTheme.colors
    val stats = series.stats()
    if (stats == null) {
        CardTitle(label)
        EmptyNote(stringResource(R.string.balance_need_days))
        return
    }
    val from = series.firstKnown ?: 0
    val known = series.known
    val days = series.grid.starts.drop(from)
    var selected by remember(series) { mutableStateOf<Int?>(null) }
    val active = selected ?: known.lastIndex
    val value = known[active]
    val headLabel = if (selected == null) label else dateLabel(days[active])
    val startLabel = dateLabel(stats.startDay)
    val change = value - stats.startRial
    val chip = if (!series.hasTrend || active == 0) null else when {
        change > 0 -> stringResource(R.string.balance_up, Money.compact(change), startLabel)
        change < 0 -> stringResource(R.string.balance_down, Money.compact(-change), startLabel)
        else -> stringResource(R.string.balance_same, startLabel)
    }
    Headline(headLabel, value, chip?.let { amount(it) }, good = change >= 0)

    if (!series.hasTrend) {
        EmptyNote(stringResource(R.string.balance_need_days))
        return
    }
    val data = remember(series) { LineChartData(slots = known.size, values = known, fromZero = false, step = true) }
    SpendLineChart(
        data = data,
        selected = selected,
        onSelect = { selected = it },
        lineColor = lineColor,
        ghostColor = t.faint.copy(alpha = 0.6f),
        referenceColor = t.amber,
        description = stringResource(R.string.cd_balance_chart),
        modifier = Modifier.padding(top = 4.dp),
        // فقط برای نقطه‌ی زیر انگشت ساخته می‌شود (یک سال = ۳۶۵ نقطه)
        tooltip = { i -> Tooltip(dateLabel(days[i]) + " — " + amount(Money.compact(known[i])), null) },
    )
    AxisLabels(dateLabel(days.first()), dateLabel(days[days.size / 2]), stringResource(R.string.balance_today))
    if (from > 0) {
        Text(
            stringResource(R.string.balance_since, startLabel),
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 11.sp,
            color = t.faint,
        )
    }
}

/** ردیف هر حساب: نقطه‌ی رنگ بانک، اسم، خط روند کوچک، مانده‌ی امروز و تغییرش در بازه */
@Composable
internal fun AccountRows(accounts: List<AccountTrend>, onOpen: (AccountRef) -> Unit, nowMillis: Long) {
    val t = JibitoTheme.colors
    val shown = accounts.mapNotNull { a -> BankDirectory.byId(a.ref.bankId)?.let { a to it } }
    shown.forEachIndexed { i, (a, bank) ->
        if (i > 0) HorizontalDivider(color = t.border, thickness = 1.dp)
        val name = accountLabel(bank.name, a.ref.account, a.name)
        val color = bankColor(a.ref.bankId, t.dark)
        val stats = a.series.stats()
        val stale = BalanceHistory.isStale(a.lastSmsMillis, nowMillis)
        val openLabel = stringResource(R.string.cd_balance_open, name)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClickLabel = openLabel) { onOpen(a.ref) }
                .padding(vertical = 8.dp)
                .alpha(if (a.excluded) 0.55f else if (stale) 0.75f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                val note = when {
                    a.excluded -> stringResource(R.string.balance_excluded)
                    stale -> stringResource(R.string.balance_stale)
                    else -> null
                }
                if (note != null) Text(note, fontSize = 11.sp, color = t.muted, maxLines = 1)
            }
            if (a.series.hasTrend) {
                Sparkline(a.series.known, color, Modifier.padding(horizontal = 10.dp).size(56.dp, 22.dp))
            }
            if (stats != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        amount(Money.compact(stats.endRial)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    val change = stats.changeRial
                    if (a.series.hasTrend && change != 0L) {
                        Text(
                            amount((if (change > 0) "▲ " else "▼ ") + Money.compact(kotlin.math.abs(change))),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (change > 0) t.income else t.amber,
                            maxLines = 1,
                        )
                    }
                }
            }
            Icon(
                JibitoIcons.ChevronForward,
                contentDescription = null,
                tint = t.faint,
                modifier = Modifier.padding(start = 6.dp).size(18.dp),
            )
        }
    }
}

@Composable
private fun OpenAllRow(onClick: () -> Unit) {
    val t = JibitoTheme.colors
    Row(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(t.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.balance_open_all), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Icon(JibitoIcons.ChevronForward, contentDescription = null, tint = t.muted, modifier = Modifier.size(18.dp))
    }
}

/** خط روند کوچک و بی‌محور (پله‌ای)، زمان از چپ به راست مثل نمودار بزرگ */
@Composable
internal fun Sparkline(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(modifier.clearAndSetSemantics { }) {
            if (values.size < 2) return@Canvas
            val lo = values.min().toFloat()
            val hi = values.max().toFloat()
            val span = (hi - lo).takeIf { it > 0f } ?: 1f
            val pad = 2.dp.toPx()
            fun x(i: Int) = i * size.width / (values.size - 1)
            fun y(v: Long) = if (hi == lo) size.height / 2 else pad + (1f - (v - lo) / span) * (size.height - 2 * pad)
            val path = Path().apply {
                moveTo(x(0), y(values[0]))
                for (i in 1 until values.size) {
                    lineTo(x(i), y(values[i - 1]))
                    lineTo(x(i), y(values[i]))
                }
            }
            drawPath(path, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color, radius = 2.4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x(values.lastIndex), y(values.last())))
        }
    }
}

/** «۱۷ تیر» برای شروع یک روز */
@Composable
internal fun dateLabel(dayStart: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
    val (_, month, day) = Jalali.fromGregorian(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    return Jalali.toPersianDigits(stringResource(R.string.reports_day, day, Jalali.MONTH_NAMES[month - 1]))
}
