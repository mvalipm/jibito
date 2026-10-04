package ir.jibito.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import ir.jibito.app.JibitoApplication
import ir.jibito.app.MainActivity
import ir.jibito.app.R
import ir.jibito.app.data.category.SpendRollup
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.util.ErrorLog
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ویجت صفحه‌ی اصلی. عدد اصلی جواب «امروز چقدر می‌تونم خرج کنم؟» است (سهم امروز از بودجه، منهای خرج امروز)؛
 * خرج امروز در برچسب کنارش. بدون بودجه، عدد اصلی خرج امروز است و لمس ویجت پنجره‌ی بودجه را باز می‌کند.
 * چهار طرح بر اساس اندازه (Size): نواری، مربع، متوسط، و بزرگ با نمودار هفت روز اخیر.
 * نوار ماه یک نشانگر زمان دارد (چقدر از ماه گذشته) تا معلوم باشد جلوتر از برنامه‌ای یا عقب‌تر.
 * رنگ زمینه «حال جیب» است (آرام / نزدیک سقف / رد شده، مثل صفحه‌ی خلاصه)؛ رد شدن از بودجه جیبی را نگران می‌کند.
 * بعد از هر همگام‌سازی و هر تغییر دسته به‌روز می‌شود (و هر ۳۰ دقیقه، توسط خود اندروید).
 * اگر قفل اپ روشن باشد، مبلغ‌ها روی صفحه‌ی اصلی نشان داده نمی‌شوند.
 */
class SpendWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                render(context, manager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    /** با تغییر اندازه‌ی ویجت، تصویرها (نمودار و نوار) به اندازه‌ی تازه کشیده می‌شوند و قبل از اندروید ۱۲ طرح هم عوض می‌شود */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        onUpdate(context, manager, intArrayOf(appWidgetId))
    }

    /**
     * اعداد ویجت (به ریال).
     * @param week خرج هفت روز اخیر، قدیمی به جدید (آخری امروز است)
     */
    data class Numbers(
        val todayRial: Long,
        val monthRial: Long,
        val overallBudgetRial: Long?,
        val week: List<Long> = listOf(todayRial),
    )

    /** طرح ویجت بر اساس اندازه */
    internal enum class Size(val layout: Int, val defaultWidth: Float, val defaultHeight: Float) {
        STRIP(R.layout.widget_spend_strip, 356f, 80f),
        SQUARE(R.layout.widget_spend_square, 170f, 172f),
        MEDIUM(R.layout.widget_spend, 356f, 172f),
        LARGE(R.layout.widget_spend_large, 356f, 290f),
    }

    companion object {

        /** کمترین اندازه (dp) هر طرح؛ کوچک‌تر از مربع و متوسط، نواری */
        private const val SQUARE_W = 130f
        private const val SQUARE_H = 160f
        private const val MEDIUM_W = 250f
        private const val MEDIUM_H = 150f
        private const val LARGE_W = 250f
        private const val LARGE_H = 270f

        /** بخش‌های ثابت طرح بزرگ (dp)؛ باقی ارتفاع مال نمودار است */
        private const val LARGE_FIXED_HEIGHT = 232f

        /** همه‌ی ویجت‌های روی صفحه را به‌روز می‌کند؛ اگر ویجتی نیست، کاری نمی‌کند. */
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, SpendWidget::class.java))
            if (ids.isEmpty()) return
            render(context, manager, ids)
        }

        internal suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            try {
                val container = (context.applicationContext as JibitoApplication).container
                val locked = container.appLockSettings.enabled.value
                val now = System.currentTimeMillis()
                val n = if (locked) null else numbers(container.database, now)
                for (id in ids) manager.updateAppWidget(id, responsive(context, n, now, manager.getAppWidgetOptions(id)))
            } catch (e: Exception) {
                ErrorLog.record(context, "widget", e)
            }
        }

        internal fun sizeOf(widthDp: Float, heightDp: Float): Size = when {
            widthDp >= LARGE_W && heightDp >= LARGE_H -> Size.LARGE
            widthDp >= MEDIUM_W && heightDp >= MEDIUM_H -> Size.MEDIUM
            widthDp >= SQUARE_W && heightDp >= SQUARE_H -> Size.SQUARE
            else -> Size.STRIP
        }

        /**
         * اندروید ۱۲ به بعد: هر چهار طرح را می‌دهد و خود لانچر با هر تغییر اندازه، طرح مناسب را می‌کشد.
         * قبل از آن: از روی اندازه‌ی فعلی. (در حالت عمودی، عرض کمینه و ارتفاع بیشینه است.)
         */
        private fun responsive(context: Context, n: Numbers?, now: Long, options: Bundle?): RemoteViews {
            if (n == null) return locked(context)
            val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)?.toFloat() ?: 0f
            val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)?.toFloat() ?: 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return RemoteViews(
                    mapOf(
                        SizeF(0f, 0f) to views(context, n, now, Size.STRIP, width, height),
                        SizeF(SQUARE_W, SQUARE_H) to views(context, n, now, Size.SQUARE, width, height),
                        SizeF(MEDIUM_W, MEDIUM_H) to views(context, n, now, Size.MEDIUM, width, height),
                        SizeF(LARGE_W, LARGE_H) to views(context, n, now, Size.LARGE, width, height),
                    ),
                )
            }
            val size = if (width > 0 && height > 0) sizeOf(width, height) else Size.MEDIUM
            return views(context, n, now, size, width, height)
        }

        /** حال جیب، مثل بالای صفحه‌ی خلاصه */
        internal enum class Mood { NONE, CALM, WARN, OVER }

        /** کمتر از این فاصله بین «خرج» و «زمان» یعنی «طبق برنامه» (مثل صفحه‌ی خلاصه) */
        private const val ON_TRACK_MARGIN = 0.05

        /** چه کسری از ماه گذشته (۰ تا ۱) */
        internal fun monthElapsed(now: Long): Double {
            val month = JalaliMonth.of(now)
            return (now - month.startMillis()).toDouble() / (month.endMillis() - month.startMillis())
        }

        internal fun moodOf(n: Numbers, now: Long): Mood {
            val budget = n.overallBudgetRial?.takeIf { it > 0 } ?: return Mood.NONE
            val spent = n.monthRial.toDouble() / budget
            return when {
                n.monthRial >= budget -> Mood.OVER
                spent >= 0.8 || spent - monthElapsed(now) >= ON_TRACK_MARGIN -> Mood.WARN
                else -> Mood.CALM
            }
        }

        /** چند روز از ماه مانده، با امروز (روز آخر = ۱) */
        internal fun daysLeft(now: Long): Int {
            val day = 24 * 60 * 60 * 1000L
            val left = (JalaliMonth.of(now).endMillis() - Jalali.startOfDay(now) + day / 2) / day
            return left.toInt().coerceAtLeast(1)
        }

        /**
         * سهم امروز از بودجه: آنچه تا دیروز از بودجه مانده، تقسیم بر روزهای باقی‌مانده (با امروز).
         * null یعنی بودجه‌ای نیست یا تا دیروز تمام شده.
         */
        internal fun dailyShare(n: Numbers, now: Long): Long? {
            val budget = n.overallBudgetRial?.takeIf { it > 0 } ?: return null
            val leftBeforeToday = budget - (n.monthRial - n.todayRial)
            if (leftBeforeToday <= 0) return null
            return leftBeforeToday / daysLeft(now)
        }

        /** عدد اصلی ویجت: عنوانش، و مبلغ (یا به‌جای صفر، یک جمله) */
        private class Hero(val label: Int, val shortLabel: Int, val amountRial: Long = 0, val phrase: Int? = null, val more: Boolean = false)

        private fun heroOf(n: Numbers, now: Long): Hero {
            val budget = n.overallBudgetRial?.takeIf { it > 0 }
                ?: return if (n.todayRial > 0) Hero(R.string.widget_spent_today, R.string.widget_spent_today, n.todayRial)
                else Hero(R.string.widget_spent_today, R.string.widget_spent_today, phrase = R.string.widget_zero)
            val left = budget - n.monthRial
            val share = dailyShare(n, now)
            return when {
                left < 0 -> Hero(R.string.widget_month_over, R.string.widget_month_over_short, -left, more = true)
                left == 0L || share == null ->
                    Hero(R.string.widget_month_over, R.string.widget_month_over_short, phrase = R.string.widget_budget_used)
                n.todayRial < share -> Hero(R.string.widget_can_spend, R.string.widget_can_spend_short, share - n.todayRial)
                n.todayRial == share -> Hero(R.string.widget_can_spend, R.string.widget_can_spend_short, phrase = R.string.widget_share_used)
                else -> Hero(R.string.widget_past_share, R.string.widget_past_share_short, n.todayRial - share, more = true)
            }
        }

        /** متن کم‌رنگ روی زمینه‌ی رنگی: سفید ۷۸٪ */
        private const val MUTED_ON_MOOD = 0xC7FFFFFF.toInt()

        private fun openApp(context: Context, budget: Boolean): PendingIntent = PendingIntent.getActivity(
            context,
            if (budget) 1 else 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_BUDGET, budget),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** قفل اپ روشن: فقط «مبلغ‌ها پنهانه» (برای همه‌ی اندازه‌ها یک طرح) */
        internal fun locked(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_spend_locked).apply {
                setOnClickPendingIntent(R.id.widget_root, openApp(context, budget = false))
            }

        /**
         * ظاهر ویجت از روی عددها (بدون دیتابیس؛ تست اسکرین‌شات هم همین را می‌کشد).
         * @param widthDp / heightDp اندازه‌ی واقعی ویجت برای کشیدن نمودار و نوار؛ صفر یعنی نامعلوم (اندازه‌ی معمول آن طرح)
         */
        internal fun views(
            context: Context,
            n: Numbers?,
            now: Long,
            size: Size = Size.MEDIUM,
            widthDp: Float = 0f,
            heightDp: Float = 0f,
        ): RemoteViews {
            if (n == null) return locked(context)
            val width = widthDp.takeIf { it > 0 } ?: size.defaultWidth
            val height = heightDp.takeIf { it > 0 } ?: size.defaultHeight
            val views = RemoteViews(context.packageName, size.layout)
            val budget = n.overallBudgetRial?.takeIf { it > 0 }
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context, budget = budget == null))

            val mood = moodOf(n, now)
            val onMood = mood != Mood.NONE
            views.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                when (mood) {
                    Mood.NONE -> R.drawable.widget_bg
                    Mood.CALM -> R.drawable.widget_bg_calm
                    Mood.WARN -> R.drawable.widget_bg_warn
                    Mood.OVER -> R.drawable.widget_bg_over
                },
            )
            val text = if (onMood) Color.WHITE else ContextCompat.getColor(context, R.color.widget_text)
            val muted = if (onMood) MUTED_ON_MOOD else ContextCompat.getColor(context, R.color.widget_muted)
            views.setImageViewResource(
                R.id.widget_mascot,
                if (mood == Mood.OVER) R.drawable.widget_mascot_worried else R.drawable.widget_mascot_happy,
            )

            // سرتیتر
            when (size) {
                Size.LARGE -> {
                    views.setTextColor(R.id.widget_name, text)
                    views.setTextViewText(R.id.widget_date, Jalali.weekdayDate(now))
                }
                Size.MEDIUM -> views.setTextViewText(R.id.widget_date, Jalali.weekdayDate(now))
                Size.SQUARE -> views.setTextViewText(R.id.widget_date, Jalali.dayMonth(now))
                Size.STRIP -> Unit
            }
            if (size != Size.STRIP) views.setTextColor(R.id.widget_date, muted)

            // عدد اصلی
            val hero = heroOf(n, now)
            val compact = size == Size.SQUARE || size == Size.STRIP
            views.setTextViewText(R.id.widget_label, context.getString(if (compact) hero.shortLabel else hero.label))
            views.setTextColor(R.id.widget_label, if (size == Size.MEDIUM) text else muted)
            views.setTextColor(R.id.widget_amount, text)
            views.setTextColor(R.id.widget_unit, muted)
            if (hero.phrase != null) {
                // صفر یک جمله است، نه «۰»: صفر فارسی تنها روی ویجت فقط یک نقطه دیده می‌شود
                views.setTextViewText(R.id.widget_amount, context.getString(hero.phrase))
                val sp = when (size) {
                    Size.LARGE -> 24f
                    Size.MEDIUM -> 22f
                    Size.SQUARE -> 18f
                    Size.STRIP -> 16f
                }
                views.setTextViewTextSize(R.id.widget_amount, TypedValue.COMPLEX_UNIT_SP, sp)
                views.setViewVisibility(R.id.widget_unit, View.GONE)
            } else {
                val (number, unit) = Money.compactParts(hero.amountRial)
                val toman = listOf(unit, context.getString(R.string.unit_toman)).filter { it.isNotEmpty() }.joinToString(" ")
                views.setTextViewText(R.id.widget_amount, number)
                views.setTextViewText(R.id.widget_unit, if (hero.more) context.getString(R.string.widget_more, toman) else toman)
            }

            // برچسب کنار عدد: خرج امروز (یا بدون بودجه، خرج این ماه)
            if (size == Size.LARGE || size == Size.MEDIUM) {
                val (label, value) = if (budget != null) {
                    context.getString(R.string.widget_chip_today) to
                        if (n.todayRial > 0) Money.compact(n.todayRial) else context.getString(R.string.widget_nothing_yet)
                } else {
                    context.getString(R.string.widget_chip_month) to Money.compact(n.monthRial)
                }
                val chip = SpannableStringBuilder(label).apply {
                    setSpan(ForegroundColorSpan(muted), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    append(" ").append(value)
                }
                views.setTextViewText(R.id.widget_chip, chip)
                views.setTextColor(R.id.widget_chip, text)
                views.setInt(R.id.widget_chip, "setBackgroundResource", if (onMood) R.drawable.widget_chip_on else R.drawable.widget_chip_plain)
            }

            val days = daysLeft(now)
            val left = budget?.let { it - n.monthRial }
            val share = dailyShare(n, now)
            val padding = when (size) {
                Size.LARGE, Size.MEDIUM -> 40f
                Size.SQUARE -> 32f
                Size.STRIP -> 0f
            }

            // نمودار هفت روز اخیر
            if (size == Size.LARGE) {
                val week = List(7) { i -> n.week.getOrNull(n.week.size - 7 + i) ?: 0L }
                val dayStart = Jalali.startOfDay(now)
                val labels = List(7) { i ->
                    if (i == 6) context.getString(R.string.widget_today_short)
                    else Jalali.weekdayInitial(Calendar.getInstance().apply { timeInMillis = dayStart; add(Calendar.DAY_OF_MONTH, i - 6) }.timeInMillis + 12 * 60 * 60 * 1000L)
                }
                val chart = SpendWidgetArt.weekChart(
                    context,
                    widthDp = width - padding,
                    heightDp = (height - LARGE_FIXED_HEIGHT).coerceIn(36f, 96f),
                    week = week,
                    labels = labels,
                    share = share,
                    allHigh = budget != null && share == null,
                    color = text,
                    muted = muted,
                )
                views.setImageViewBitmap(R.id.widget_chart, chart)
                views.setTextColor(R.id.widget_chart_cap, muted)
                views.setTextColor(R.id.widget_chart_hint, muted)
                views.setViewVisibility(R.id.widget_chart_hint, if (share != null) View.VISIBLE else View.GONE)
            }

            // پایین: نوار ماه و «چقدر مونده»
            if (budget == null || left == null) {
                views.setViewVisibility(R.id.widget_bar, View.GONE)
                when (size) {
                    Size.LARGE, Size.MEDIUM -> {
                        views.setViewVisibility(R.id.widget_footer, View.GONE)
                        views.setViewVisibility(R.id.widget_cta, View.VISIBLE)
                    }
                    Size.SQUARE -> {
                        views.setTextViewText(R.id.widget_month, context.getString(R.string.widget_month, Money.compact(n.monthRial)))
                        views.setTextColor(R.id.widget_month, text)
                        views.setViewVisibility(R.id.widget_percent, View.GONE)
                    }
                    Size.STRIP -> views.setViewVisibility(R.id.widget_side, View.GONE)
                }
                return views
            }
            val spent = n.monthRial.toFloat() / budget
            val percent = Jalali.toPersianDigits("${(n.monthRial * 100 / budget).coerceAtMost(999)}٪")
            val barWidth = if (size == Size.STRIP) 88f else width - padding
            views.setImageViewBitmap(R.id.widget_bar, SpendWidgetArt.monthBar(context, barWidth, spent, monthElapsed(now).toFloat(), text))
            views.setContentDescription(R.id.widget_bar, context.getString(R.string.widget_bar_cd, percent))
            val monthText = when {
                size == Size.SQUARE || size == Size.STRIP -> context.getString(R.string.widget_month_short)
                left > 0 -> context.getString(R.string.widget_left, Money.compact(left))
                days == 1 -> context.getString(R.string.widget_last_day)
                else -> context.getString(R.string.widget_days_left, Jalali.toPersianDigits(days.toString()))
            }
            val percentText = when {
                size == Size.LARGE && left > 0 ->
                    context.getString(R.string.widget_percent_days, percent, Jalali.toPersianDigits(days.toString()))
                size == Size.LARGE || size == Size.MEDIUM -> context.getString(R.string.widget_percent, percent)
                else -> percent
            }
            views.setTextViewText(R.id.widget_month, monthText)
            views.setTextColor(R.id.widget_month, text)
            views.setTextViewText(R.id.widget_percent, percentText)
            views.setTextColor(R.id.widget_percent, muted)
            return views
        }

        /** خرج هفت روز اخیر (و امروز و این ماه) با همان قانون‌های صفحه‌ی خلاصه (انتقال به خودم و دسته‌های «خرج نیست» حساب نمی‌شوند) */
        suspend fun numbers(db: ir.jibito.app.data.local.AppDatabase, now: Long): Numbers {
            val dao = db.summaryDao()
            val categories = db.categoryDao().all().filter { it.flowType == FlowType.WITHDRAWAL.code }
            suspend fun spent(from: Long, to: Long) =
                SpendRollup.rollup(categories, dao.sums(FlowType.WITHDRAWAL.code, from, to)).total
            val today = Jalali.startOfDay(now)
            val week = (6 downTo 0).map { back ->
                val start = Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_MONTH, -back) }
                val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
                spent(start.timeInMillis, end.timeInMillis)
            }
            val month = JalaliMonth.of(now)
            val monthTotal = spent(month.startMillis(), month.endMillis())
            return Numbers(week.last(), monthTotal, dao.overallBudget()?.monthlyLimitRial, week)
        }
    }
}
