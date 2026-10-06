package ir.jibito.app.ui.reports

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.data.repository.SpendCurve
import ir.jibito.app.data.repository.SpendTrend
import ir.jibito.app.ui.common.LocalHideAmounts
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
import kotlin.math.abs
import kotlin.math.roundToInt

/** کمتر از این درصد اختلاف = «تقریباً همون اندازه» (مثل کارت روند خلاصه) */
private const val SAME_PERCENT = 3

/** کمتر از ۱۰۰ هزار تومان: پایه‌ی مقایسه نیست */
private const val MIN_COMPARABLE_RIAL = 1_000_000L

internal fun monthName(month: JalaliMonth) = Jalali.MONTH_NAMES[month.month - 1]

@Composable
internal fun dayLabel(day: Int, month: JalaliMonth) =
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
internal fun MonthChart(curve: SpendCurve) {
    val t = JibitoTheme.colors
    if (curve.spentRial == 0L && (curve.previous.lastOrNull() ?: 0L) == 0L) {
        EmptyNote(stringResource(R.string.reports_empty))
        return
    }
    var selected by remember(curve.month) { mutableStateOf<Int?>(null) }
    val previousName = monthName(curve.month.plus(-1))
    val name = monthName(curve.month)
    val active = selected ?: curve.todayIndex
    // عدد بالا بدون انتخاب: کل خرج (با خرج یک‌باره)؛ روز انتخاب‌شده: همان مقدار خط نمودار
    val value = if (selected == null) curve.spentRial else curve.routineAt(active) ?: curve.spentRial
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
            // خط و پیش‌بینی بدون خرج یک‌باره، هم‌سنگِ ماه قبل و خط بودجه
            values = curve.routineCumulative,
            ghost = curve.previous,
            projection = curve.routineProjection,
            reference = curve.budgetRial,
        )
    }
    val budgetLabel = curve.budgetRial?.let { amount(stringResource(R.string.reports_budget_line, Money.compact(it))) }
    val tipForecast = stringResource(R.string.reports_tip_forecast)
    val tipTitles = (0..data.lastIndex).associateWith { i ->
        val v = curve.routineAt(i) ?: 0L
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
internal fun MonthsChart(trend: SpendTrend) {
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
        months[pick].month.title to months[pick].routineRial
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
        // بدون خرج یک‌باره، هم‌سنگِ خط میانگین (خرید خانه‌ای بقیه‌ی ماه‌ها را صاف نکند)
        LineChartData(slots = months.size, values = months.map { it.routineRial }, reference = average, fromZero = false)
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
        tooltip = { i ->
            val m = months[i]
            val oneOff = m.spentRial - m.routineRial
            Tooltip(
                m.month.title + " — " + amount(Money.compact(m.routineRial)),
                if (oneOff > 0) amount(stringResource(R.string.reports_tip_one_off, Money.compact(oneOff))) else null,
            )
        },
    )
    AxisLabels(monthName(months.first().month), monthName(months.last().month))
    Legend(listOf(LegendStyle.SOLID to stringResource(R.string.reports_legend_monthly)))
    val oneOffMonths = months.filter { it.spentRial > it.routineRial }
    if (oneOffMonths.isNotEmpty() && !LocalHideAmounts.current) {
        val names = oneOffMonths.map { monthName(it.month) }
        val list = oneOffMonths.indices.joinToString("، ") { k ->
            names[k] + " " + Money.compact(oneOffMonths[k].spentRial - oneOffMonths[k].routineRial)
        }
        Note(
            text = Jalali.toPersianDigits(stringResource(R.string.reports_months_one_off_note, list)),
            bg = t.sugBg,
            fg = t.sugFg,
        )
    }
}

@Composable
private fun averageChip(percent: Int): String = when {
    abs(percent) < SAME_PERCENT -> stringResource(R.string.reports_near_average)
    percent < 0 -> Jalali.toPersianDigits(stringResource(R.string.reports_below_average, -percent))
    else -> Jalali.toPersianDigits(stringResource(R.string.reports_above_average, percent))
}
