package ir.jibito.app.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.repository.Insight
import ir.jibito.app.data.repository.MonthReport
import ir.jibito.app.data.repository.SpendCurve
import ir.jibito.app.data.repository.SpendTrend
import ir.jibito.app.data.wallet.AccountRef
import ir.jibito.app.data.wallet.BalanceOverview
import ir.jibito.app.ui.common.CategoryIconTile
import ir.jibito.app.ui.common.LocalHideAmounts
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.ui.summary.SectionHeader
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
import kotlin.math.abs
import kotlin.math.roundToInt

/** کمتر از این درصد اختلاف = «تقریباً همون اندازه» (مثل کارت روند خلاصه) */
private const val SAME_PERCENT = 3

/** کمتر از ۱۰۰ هزار تومان: پایه‌ی مقایسه نیست */
private const val MIN_COMPARABLE_RIAL = 1_000_000L

/**
 * تب «گزارش‌ها»: «در طول زمان چه الگویی دارم؟»
 * ۱) نمودار خرج: این ماه (تجمعی، در برابر ماه قبل، با بودجه و پیش‌بینی آخر ماه) یا ۶/۱۲ ماه اخیر.
 * ۲) «موجودی حساب‌ها»: روند موجودی از روی مانده‌ی پیامک‌ها، در همان بازه (BalanceCard).
 * ۳) «جیبی چی فهمید؟»: نکته‌های کوتاه از الگوی خرج (دسته‌های بیشتر/کمتر، پرخرج‌ترین روز، الگوی هفته).
 * «خلاصه» به «الان وضعم چطوره؟» جواب می‌دهد؛ این تب به روند و مقایسه.
 */
@Composable
fun ReportsScreen(onOpenAccount: (AccountRef?) -> Unit = {}) {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val viewModel: ReportsViewModel = viewModel(
        factory = ReportsViewModel.factory(app.container.reportRepository, app.container.budgetRepository, app.container.balanceRepository)
    )
    val range by viewModel.range.collectAsState()
    val report by viewModel.report.collectAsState()
    val months by viewModel.months.collectAsState()
    val balances by viewModel.balances.collectAsState()
    ReportsContent(
        month = viewModel.month,
        range = range,
        report = report,
        months = months,
        onRange = viewModel::setRange,
        balances = balances,
        onOpenAccount = onOpenAccount,
    )
}

/** محتوای تب، جدا از ViewModel (برای اسکرین‌شات‌ها) */
@Composable
fun ReportsContent(
    month: JalaliMonth,
    range: ReportRange,
    report: MonthReport?,
    months: SpendTrend?,
    onRange: (ReportRange) -> Unit,
    modifier: Modifier = Modifier,
    /** «روند موجودی» در همین بازه؛ null یعنی «هنوز در حال بارگذاری» */
    balances: BalanceOverview? = null,
    /** جزئیات یک حساب (null = همه‌ی حساب‌ها) */
    onOpenAccount: (AccountRef?) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 16.dp + LocalBottomBarSpace.current),
    ) {
        item(key = "header") { Header(month) }
        item(key = "range") { RangeSelector(range, onRange) }
        item(key = "chart") {
            ChartCard {
                when {
                    range == ReportRange.MONTH && report != null -> MonthChart(report.curve)
                    range != ReportRange.MONTH && months != null -> MonthsChart(months)
                    else -> Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
        item(key = "balance") { BalanceCard(balances, onOpenAccount) }
        val insights = report?.insights.orEmpty()
        if (insights.isNotEmpty()) {
            item(key = "insights") { InsightsSection(insights, month) }
        }
    }
}

@Composable
private fun Header(month: JalaliMonth) {
    val t = JibitoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.reports_title),
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            month.title,
            modifier = Modifier
                .clip(CircleShape)
                .background(t.chip)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun RangeSelector(range: ReportRange, onRange: (ReportRange) -> Unit) {
    SegmentedTabs(
        labels = ReportRange.entries.map {
            stringResource(
                when (it) {
                    ReportRange.MONTH -> R.string.reports_range_month
                    ReportRange.HALF_YEAR -> R.string.reports_range_half
                    ReportRange.YEAR -> R.string.reports_range_year
                }
            )
        },
        selected = range.ordinal,
        onSelect = { onRange(ReportRange.entries[it]) },
    )
}

/** انتخاب بازه: ریل خاکستری با گزینه‌ی انتخاب‌شده‌ی روشن (تب «گزارش‌ها» و صفحه‌ی جزئیات حساب) */
@Composable
internal fun SegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
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
private fun ReferenceLabel(text: String) {
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

private enum class LegendStyle { SOLID, DASHED, DOTTED }

@Composable
private fun Legend(items: List<Pair<LegendStyle, String>>) {
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

private fun monthName(month: JalaliMonth) = Jalali.MONTH_NAMES[month.month - 1]

@Composable
private fun dayLabel(day: Int, month: JalaliMonth) =
    Jalali.toPersianDigits(stringResource(R.string.reports_day, day, monthName(month)))

/** «۱۲٪ کمتر از شهریور» و اینکه خوب است یا نه؛ null اگر پایه‌ی مقایسه کوچک است */
@Composable
private fun compareChip(value: Long, base: Long?, baseName: String): Pair<String, Boolean>? {
    if (base == null || base < MIN_COMPARABLE_RIAL) return null
    val percent = ((value - base) * 100.0 / base).roundToInt()
    return when {
        abs(percent) < SAME_PERCENT -> stringResource(R.string.trend_same, baseName) to true
        percent < 0 -> Jalali.toPersianDigits(stringResource(R.string.trend_less, -percent, baseName)) to true
        else -> Jalali.toPersianDigits(stringResource(R.string.trend_more, percent, baseName)) to false
    }
}

/** این ماه: خرج تجمعی روزبه‌روز در برابر ماه قبل، با خط بودجه و پیش‌بینی آخر ماه */
@Composable
private fun MonthChart(curve: SpendCurve) {
    val t = JibitoTheme.colors
    if (curve.spentRial == 0L && (curve.previous.lastOrNull() ?: 0L) == 0L) {
        EmptyNote(stringResource(R.string.reports_empty))
        return
    }
    var selected by remember(curve.month) { mutableStateOf<Int?>(null) }
    val previousName = monthName(curve.month.plus(-1))
    val name = monthName(curve.month)
    val active = selected ?: curve.todayIndex
    val value = curve.valueAt(active) ?: curve.spentRial
    val future = active > curve.todayIndex
    val label = when {
        selected == null -> stringResource(if (curve.isCurrent) R.string.reports_spent_so_far else R.string.reports_spent_month, name)
        future -> stringResource(R.string.reports_forecast_label, dayLabel(active + 1, curve.month))
        else -> dayLabel(active + 1, curve.month)
    }
    // مقایسه بدون خرج‌های یک‌باره (منحنی ماه قبل هم بدون آن‌هاست)
    val chip = compareChip(curve.routineAt(active) ?: value, curve.previousAt(active), previousName)
    Headline(label, value, chip?.first, chip?.second ?: true)

    val data = remember(curve) {
        LineChartData(
            slots = curve.days,
            values = curve.cumulative,
            ghost = curve.previous,
            projection = curve.projection,
            reference = curve.budgetRial,
        )
    }
    val budgetLabel = curve.budgetRial?.let { amount(stringResource(R.string.reports_budget_line, Money.compact(it))) }
    val tipForecast = stringResource(R.string.reports_tip_forecast)
    val tipTitles = (0..data.lastIndex).associateWith { i ->
        val v = curve.valueAt(i) ?: 0L
        dayLabel(i + 1, curve.month) + " — " + amount(Money.compact(v)) + if (i > curve.todayIndex) " ($tipForecast)" else ""
    }
    val tipSubs = (0..data.lastIndex).associateWith { i ->
        curve.previousAt(i)?.let { stringResource(R.string.reports_tip_previous, previousName, amount(Money.compact(it))) }
    }
    SpendLineChart(
        data = data,
        selected = selected,
        onSelect = { selected = it },
        lineColor = t.teal,
        ghostColor = t.faint.copy(alpha = 0.6f),
        referenceColor = t.amber,
        description = stringResource(R.string.cd_reports_chart),
        modifier = Modifier.padding(top = 4.dp),
        referenceLabel = budgetLabel?.let { text -> @Composable { ReferenceLabel(text) } },
        tooltip = { i -> Tooltip(tipTitles[i].orEmpty(), tipSubs[i]) },
    )
    AxisLabels(
        dayLabel(1, curve.month),
        dayLabel((curve.days + 1) / 2, curve.month),
        dayLabel(curve.days, curve.month),
    )
    Legend(
        buildList {
            add(LegendStyle.SOLID to stringResource(R.string.reports_legend_month, name))
            add(LegendStyle.DASHED to previousName)
            if (curve.projection.size > 1) add(LegendStyle.DOTTED to stringResource(R.string.reports_legend_forecast))
        }
    )

    val end = curve.projectedEndRial
    if (curve.isCurrent && end != null && curve.projection.size > 1) {
        val first = stringResource(
            if (curve.projectionFromPattern) R.string.reports_forecast_pattern else R.string.reports_forecast_rhythm,
            name,
            amount(Money.compact(end)),
        )
        val budget = curve.budgetRial
        // خرج یک‌باره از بودجه کم نمی‌شود
        val budgetEnd = end - curve.oneOffRial
        val second = when {
            budget == null -> null
            budgetEnd <= budget -> stringResource(R.string.reports_forecast_under, amount(Money.compact(budget - budgetEnd)))
            else -> stringResource(R.string.reports_forecast_over, amount(Money.compact(budgetEnd - budget)))
        }
        val over = budget != null && budgetEnd > budget
        Note(
            text = if (second == null) first else "$first $second",
            bg = if (over) t.amberTint else t.sugBg,
            fg = if (over) t.amberTintFg else t.sugFg,
        )
    }
    if (curve.oneOffRial > 0) {
        Note(
            text = stringResource(R.string.reports_one_off_note, amount(Money.compact(curve.oneOffRial))),
            bg = t.sugBg,
            fg = t.sugFg,
        )
    }
}

/** ۶ یا ۱۲ ماه اخیر: خرج هر ماهِ تمام‌شده، با خط میانگین */
@Composable
private fun MonthsChart(trend: SpendTrend) {
    val t = JibitoTheme.colors
    // ماه‌های قبل از اولین خرج ثبت‌شده (قبل از نصب اپ) نشان داده نمی‌شوند
    val months = trend.months.dropWhile { it.spentRial == 0L }
    val average = trend.averageRial
    if (months.size < 2 || average == null) {
        EmptyNote(stringResource(R.string.reports_months_empty))
        return
    }
    var selected by remember(months) { mutableStateOf<Int?>(null) }
    val pick = selected
    val (label, value) = if (pick == null) {
        stringResource(R.string.reports_monthly_average) to average
    } else {
        months[pick].month.title to months[pick].spentRial
    }
    val chip = if (pick == null) {
        // آخرین ماه نسبت به میانگین
        // میانگین بدون خرج‌های یک‌باره است؛ ماه هم همین‌طور مقایسه می‌شود
        val last = months.last()
        val percent = ((last.routineRial - average) * 100.0 / average).roundToInt()
        monthName(last.month) + " · " + averageChip(percent) to (percent < SAME_PERCENT)
    } else {
        val percent = ((months[pick].routineRial - average) * 100.0 / average).roundToInt()
        averageChip(percent) to (percent < SAME_PERCENT)
    }
    Headline(label, value, chip.first, chip.second)

    val data = remember(months, average) {
        LineChartData(slots = months.size, values = months.map { it.spentRial }, reference = average, fromZero = false)
    }
    val averageLabel = amount(stringResource(R.string.reports_average_line, Money.compact(average)))
    SpendLineChart(
        data = data,
        selected = selected,
        onSelect = { selected = it },
        lineColor = t.teal,
        ghostColor = t.faint.copy(alpha = 0.6f),
        referenceColor = t.amber,
        description = stringResource(R.string.cd_reports_chart),
        modifier = Modifier.padding(top = 4.dp),
        referenceLabel = { ReferenceLabel(averageLabel) },
        tooltip = { i -> Tooltip(months[i].month.title + " — " + amount(Money.compact(months[i].spentRial)), null) },
    )
    AxisLabels(monthName(months.first().month), monthName(months.last().month))
    Legend(listOf(LegendStyle.SOLID to stringResource(R.string.reports_legend_monthly)))
}

@Composable
private fun averageChip(percent: Int): String = when {
    abs(percent) < SAME_PERCENT -> stringResource(R.string.reports_near_average)
    percent < 0 -> Jalali.toPersianDigits(stringResource(R.string.reports_below_average, -percent))
    else -> Jalali.toPersianDigits(stringResource(R.string.reports_above_average, percent))
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

// ── «جیبی چی فهمید؟» ──

@Composable
private fun InsightsSection(insights: List<Insight>, month: JalaliMonth) {
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
