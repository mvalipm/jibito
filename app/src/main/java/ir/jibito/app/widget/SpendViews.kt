package ir.jibito.app.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import ir.jibito.app.MainActivity
import ir.jibito.app.R
import ir.jibito.app.ui.theme.CategoryStyle
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
import ir.jibito.app.widget.SpendWidget.Companion.Mood
import java.util.Calendar
import kotlin.math.roundToLong

/**
 * ظاهر ویجت «خرج امروز» از روی عددها (بدون دیتابیس؛ تست اسکرین‌شات هم همین را می‌کشد).
 * همه‌ی اندازه‌ها شناسه‌های مشترک دارند؛ کاری که روی شناسه‌ی نبودِ یک چیدمان انجام شود، نادیده گرفته می‌شود.
 */
internal object SpendViews {

    /** حرف اول روزهای هفته به ترتیب Calendar.DAY_OF_WEEK (اولی یکشنبه) */
    private val DAY_LETTERS = listOf("ی", "د", "س", "چ", "پ", "ج", "ش")

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** @param n null یعنی قفل اپ روشن است: مبلغ‌ها نشان داده نمی‌شوند */
    fun build(context: Context, n: SpendWidget.Numbers?, now: Long, size: WidgetSize): RemoteViews {
        if (n == null) {
            return WidgetKit.message(
                context, R.drawable.widget_mascot_happy, R.string.widget_locked_label, R.string.widget_locked_title, R.string.widget_locked_hint,
            )
        }
        val layout = when (size) {
            WidgetSize.SLIM -> R.layout.widget_spend_slim
            WidgetSize.SMALL -> R.layout.widget_spend_small
            WidgetSize.MEDIUM -> R.layout.widget_spend_medium
            WidgetSize.LARGE -> R.layout.widget_spend_large
        }
        val views = RemoteViews(context.packageName, layout)
        views.setOnClickPendingIntent(R.id.widget_root, WidgetKit.openApp(context))
        val mood = SpendWidget.moodOf(n, now)
        views.setImageViewResource(R.id.widget_mascot, if (mood == Mood.OVER) R.drawable.widget_mascot_worried else R.drawable.widget_mascot_happy)
        views.setTextViewText(R.id.widget_date, context.getString(R.string.widget_date, dateTitle(now)))
        chip(context, views, n, mood)
        today(context, views, n, size)
        footer(context, views, n, now, mood, size)
        if (size == WidgetSize.MEDIUM || size == WidgetSize.LARGE) chart(context, views, n)
        if (size == WidgetSize.LARGE) recent(context, views, n, now)
        return views
    }

    /** «دوشنبه ۲۰ مهر» */
    private fun dateTitle(now: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val (_, jm, jd) = Jalali.fromGregorian(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        val weekday = Jalali.WEEKDAY_NAMES[cal.get(Calendar.DAY_OF_WEEK) - 1]
        return Jalali.toPersianDigits("$weekday $jd ${Jalali.MONTH_NAMES[jm - 1]}")
    }

    private fun chip(context: Context, views: RemoteViews, n: SpendWidget.Numbers, mood: Mood) {
        val (text, background, color) = when (mood) {
            Mood.NONE -> {
                WidgetKit.show(views, R.id.widget_chip, false)
                return
            }
            Mood.CALM -> Triple(context.getString(R.string.widget_mood_calm), R.drawable.widget_chip_calm, R.color.widget_calm)
            Mood.WARN -> Triple(context.getString(R.string.widget_mood_warn), R.drawable.widget_chip_warn, R.color.widget_warn)
            Mood.OVER -> {
                val over = n.budgetSpentRial - (n.overallBudgetRial ?: 0L)
                Triple(context.getString(R.string.widget_mood_over, Money.compact(over)), R.drawable.widget_chip_over, R.color.widget_over)
            }
        }
        WidgetKit.show(views, R.id.widget_chip, true)
        views.setTextViewText(R.id.widget_chip, text)
        views.setInt(R.id.widget_chip, "setBackgroundResource", background)
        WidgetKit.textColor(context, views, R.id.widget_chip, color)
    }

    /** عدد درشت امروز، یا «امروز هنوز خرجی نکردی»؛ و زیرش مقایسه با یک روز معمولی */
    private fun today(context: Context, views: RemoteViews, n: SpendWidget.Numbers, size: WidgetSize) {
        val zero = n.todayRial <= 0
        for (id in listOf(R.id.widget_num_row, R.id.widget_today, R.id.widget_unit)) WidgetKit.show(views, id, !zero)
        WidgetKit.show(views, R.id.widget_zero, zero)
        if (!zero) {
            val (number, unit) = WidgetKit.amountParts(context, n.todayRial)
            views.setTextViewText(R.id.widget_today, number)
            views.setTextViewText(R.id.widget_unit, unit)
        }
        val short = size == WidgetSize.SMALL || size == WidgetSize.SLIM
        val typical = n.typicalRial
        val typicalText = context.getString(if (short) R.string.widget_typical_short else R.string.widget_typical)
        // (بیشتر، کمتر، بقیه‌ی جمله)
        val (up, down, rest) = when {
            typical == null -> Triple(null, null, null)
            zero -> Triple(null, context.getString(R.string.widget_typical_label), Money.compact(typical))
            else -> when (val v = SpendDays.versus(n.todayRial, typical)) {
                is SpendDays.Versus.More -> Triple(context.getString(R.string.widget_times, tenths(v.tenths)), null, typicalText)
                is SpendDays.Versus.Less ->
                    Triple(null, context.getString(R.string.widget_less, Jalali.toPersianDigits(v.percent.toString())), typicalText)
                SpendDays.Versus.About -> Triple(null, null, context.getString(R.string.widget_about_typical))
            }
        }
        // اندازه‌ی یک‌ردیفه فقط یک خط کم‌رنگ دارد و با بودجه جایش به نوار ماه می‌رسد
        val slimRest = listOfNotNull(up ?: down, rest).joinToString(" ").takeIf { it.isNotEmpty() && n.overallBudgetRial == null }
        WidgetKit.show(views, R.id.widget_vs, rest != null)
        WidgetKit.show(views, R.id.widget_vs_up, up != null)
        WidgetKit.show(views, R.id.widget_vs_down, down != null)
        up?.let { views.setTextViewText(R.id.widget_vs_up, it) }
        down?.let { views.setTextViewText(R.id.widget_vs_down, it) }
        val restText = if (size == WidgetSize.SLIM) slimRest else rest
        WidgetKit.show(views, R.id.widget_vs_rest, restText != null)
        restText?.let { views.setTextViewText(R.id.widget_vs_rest, it) }
    }

    /** ۱۷ ← «۱٫۷»؛ از ۱۰ برابر به بالا بی‌اعشار */
    private fun tenths(value: Long): String {
        val text = if (value >= 100 || value % 10 == 0L) (value / 10).toString() else "${value / 10}٫${value % 10}"
        return Jalali.toPersianDigits(text)
    }

    /** بدون بودجه: «این ماه ۱۰۱٫۲ میلیون · ۲۰ روز از مهر»؛ با بودجه: «۴۸٫۸ میلیون مونده · ۱۱ روز مونده» و نوار ماه */
    private fun footer(context: Context, views: RemoteViews, n: SpendWidget.Numbers, now: Long, mood: Mood, size: WidgetSize) {
        val month = JalaliMonth.of(now)
        val todayStart = Jalali.startOfDay(now)
        val budget = n.overallBudgetRial?.takeIf { it > 0 }
        val bars = mapOf(Mood.CALM to R.id.widget_progress_calm, Mood.WARN to R.id.widget_progress_warn, Mood.OVER to R.id.widget_progress_over)
        if (budget == null) {
            WidgetKit.show(views, R.id.widget_foot_lead, true)
            views.setTextViewText(R.id.widget_foot_value, Money.compact(n.monthRial))
            val dayOfMonth = ((todayStart - month.startMillis()) / DAY_MILLIS.toDouble()).roundToLong() + 1
            views.setTextViewText(
                R.id.widget_foot_tail,
                context.getString(R.string.widget_month_day, Jalali.toPersianDigits(dayOfMonth.toString()), Jalali.MONTH_NAMES[month.month - 1]),
            )
            WidgetKit.show(views, R.id.widget_progress_box, false)
            return
        }
        val left = budget - n.budgetSpentRial
        WidgetKit.show(views, R.id.widget_foot_lead, false)
        views.setTextViewText(
            R.id.widget_foot_value,
            if (left >= 0) context.getString(R.string.widget_left, Money.compact(left))
            else context.getString(R.string.widget_over_budget, Money.compact(-left)),
        )
        val daysLeft = ((month.endMillis() - todayStart) / DAY_MILLIS.toDouble()).roundToLong() - 1
        views.setTextViewText(
            R.id.widget_foot_tail,
            if (daysLeft <= 0) context.getString(R.string.widget_last_day)
            else context.getString(R.string.widget_days_left, Jalali.toPersianDigits(daysLeft.toString())),
        )
        val percent = (n.budgetSpentRial * 100 / budget).toInt()
        val time = ((now - month.startMillis()) * 100 / (month.endMillis() - month.startMillis())).toInt()
        WidgetKit.show(views, R.id.widget_progress_box, true)
        views.setContentDescription(
            R.id.widget_progress_box,
            context.getString(R.string.widget_progress_cd, Jalali.toPersianDigits("${percent.coerceAtMost(999)}٪")),
        )
        for ((m, id) in bars) {
            WidgetKit.show(views, id, m == mood)
            if (m == mood) {
                views.setProgressBar(id, 100, percent.coerceIn(0, 100), false)
                views.setInt(id, "setSecondaryProgress", time.coerceIn(0, 100))
            }
        }
        // ویجت کوچک فقط «… مونده» را دارد؛ با بودجه‌ی رد شده همان مبلغ بیرون‌زده کافی است
        if (size == WidgetSize.SMALL || size == WidgetSize.SLIM) {
            views.setTextViewText(
                R.id.widget_foot_value,
                if (left >= 0) context.getString(R.string.widget_left, Money.compact(left))
                else context.getString(R.string.widget_mood_over, Money.compact(-left)),
            )
        }
    }

    private fun chart(context: Context, views: RemoteViews, n: SpendWidget.Numbers) {
        if (n.weekRial.isEmpty()) {
            WidgetKit.show(views, R.id.widget_chart, false)
            return
        }
        val letters = n.weekStarts.map { start ->
            DAY_LETTERS[Calendar.getInstance().apply { timeInMillis = start }.get(Calendar.DAY_OF_WEEK) - 1]
        }
        views.setImageViewBitmap(R.id.widget_chart, WidgetCharts.week(context, n.weekRial, letters, n.typicalRial, CHART_W, CHART_H))
        views.setContentDescription(R.id.widget_chart, context.getString(R.string.widget_chart_cd, Money.compact(n.todayRial)))
    }

    /** «آخرین خرج‌ها»: لمس هر ردیف همان تراکنش را در اپ باز می‌کند */
    private fun recent(context: Context, views: RemoteViews, n: SpendWidget.Numbers, now: Long) {
        views.removeAllViews(R.id.widget_recent)
        WidgetKit.show(views, R.id.widget_recent_box, n.recent.isNotEmpty())
        for (r in n.recent) {
            val row = RemoteViews(context.packageName, R.layout.widget_recent_row)
            row.setTextViewText(R.id.recent_name, r.label ?: context.getString(R.string.widget_spend_fallback))
            row.setTextViewText(
                R.id.recent_time,
                if (Jalali.startOfDay(r.dateMillis) == Jalali.startOfDay(now)) Jalali.time(r.dateMillis) else WidgetKit.ago(context, r.dateMillis, now),
            )
            row.setTextViewText(R.id.recent_amount, Money.compact(r.amountRial))
            WidgetKit.tint(
                context, row, R.id.recent_dot,
                CategoryStyle.color(r.colorHex, dark = false).toArgb(), CategoryStyle.color(r.colorHex, dark = true).toArgb(),
            )
            row.setOnClickPendingIntent(
                R.id.recent_row,
                WidgetKit.activity(context, (RECENT_REQUEST + Math.floorMod(r.id, RECENT_REQUEST)).toInt(), MainActivity.openTransactionIntent(context, r.id)),
            )
            views.addView(R.id.widget_recent, row)
        }
    }

    /** اندازه‌ی نمودار (dp)، همان ImageView چیدمان */
    private const val CHART_W = 112
    private const val CHART_H = 80

    /** کد درخواست PendingIntent ردیف‌ها (جدا از «باز کردن اپ» با کد ۰) */
    private const val RECENT_REQUEST = 100_000L
}
