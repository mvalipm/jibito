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
 * ویجت صفحه‌ی اصلی: «امروز چقدر خرج کردم» + خرج این ماه + باقی‌مانده و نوار بودجه‌ی کل ماه، و «سهم امروز» از بودجه.
 * دو طرح: معمولی و کوچک (برای ویجت کوتاه یا باریک).
 * رنگ زمینه «حال جیب» است (آرام / نزدیک سقف / رد شده، مثل صفحه‌ی خلاصه) و جیبی کوچک کنار عنوان، رد شدن از بودجه نگران می‌شود.
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

    /** با تغییر اندازه‌ی ویجت، طرح کوچک یا معمولی (برای اندروید قبل از ۱۲؛ بعد از آن خود لانچر عوض می‌کند) */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        onUpdate(context, manager, intArrayOf(appWidgetId))
    }

    /** اعداد ویجت (به ریال) */
    data class Numbers(val todayRial: Long, val monthRial: Long, val overallBudgetRial: Long?)

    companion object {

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

        /** از این اندازه (dp) به بالا طرح معمولی (با تاریخ، خط راهنما و نوار بودجه)؛ کوچک‌تر، طرح کوچک */
        private const val REGULAR_MIN_WIDTH = 160f
        private const val REGULAR_MIN_HEIGHT = 136f

        /**
         * اندروید ۱۲ به بعد: هر دو طرح را می‌دهد و خود لانچر با هر تغییر اندازه، طرح مناسب را می‌کشد.
         * قبل از آن: از روی اندازه‌ی فعلی (در حالت عمودی، عرض کمینه و ارتفاع بیشینه است).
         */
        private fun responsive(context: Context, n: Numbers?, now: Long, options: Bundle?): RemoteViews {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return RemoteViews(
                    mapOf(
                        SizeF(0f, 0f) to views(context, n, now, small = true),
                        SizeF(REGULAR_MIN_WIDTH, REGULAR_MIN_HEIGHT) to views(context, n, now, small = false),
                    ),
                )
            }
            val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
            val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
            val small = (width in 1 until REGULAR_MIN_WIDTH.toInt()) || (height in 1 until REGULAR_MIN_HEIGHT.toInt())
            return views(context, n, now, small)
        }

        /** حال جیب، مثل بالای صفحه‌ی خلاصه */
        internal enum class Mood { NONE, CALM, WARN, OVER }

        /** کمتر از این فاصله بین «خرج» و «زمان» یعنی «طبق برنامه» (مثل صفحه‌ی خلاصه) */
        private const val ON_TRACK_MARGIN = 0.05

        internal fun moodOf(n: Numbers, now: Long): Mood {
            val budget = n.overallBudgetRial?.takeIf { it > 0 } ?: return Mood.NONE
            val month = JalaliMonth.of(now)
            val time = (now - month.startMillis()).toDouble() / (month.endMillis() - month.startMillis())
            val spent = n.monthRial.toDouble() / budget
            return when {
                n.monthRial >= budget -> Mood.OVER
                spent >= 0.8 || spent - time >= ON_TRACK_MARGIN -> Mood.WARN
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

        /**
         * ظاهر ویجت از روی عددها (بدون دیتابیس؛ تست اسکرین‌شات هم همین را می‌کشد).
         * @param n null یعنی قفل اپ روشن است: مبلغ‌ها نشان داده نمی‌شوند
         * @param small طرح کوچک (widget_spend_small): بدون تاریخ، خط راهنما و نوار
         */
        internal fun views(context: Context, n: Numbers?, now: Long, small: Boolean = false): RemoteViews {
            val views = RemoteViews(context.packageName, if (small) R.layout.widget_spend_small else R.layout.widget_spend)
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            if (n == null) {
                views.setTextViewText(R.id.widget_label, context.getString(R.string.widget_locked_label))
                views.setTextViewText(R.id.widget_today, "🔒 " + context.getString(R.string.widget_locked_title))
                views.setTextViewTextSize(R.id.widget_today, TypedValue.COMPLEX_UNIT_SP, if (small) 16f else 20f)
                views.setViewVisibility(R.id.widget_unit, View.GONE)
                views.setTextViewText(R.id.widget_month, context.getString(R.string.widget_locked_hint))
                views.setTextColor(R.id.widget_month, ContextCompat.getColor(context, R.color.widget_muted))
                return views
            }

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
            views.setTextColor(R.id.widget_label, muted)
            if (!small) {
                views.setTextViewText(R.id.widget_date, Jalali.weekdayDate(now))
                views.setTextColor(R.id.widget_date, muted)
            }

            // خرج امروز: «۶۰۵ هزار» ← عدد درشت + «هزار تومان» کوچک کنارش.
            // صفر یک جمله است، نه «۰»: صفر فارسی تنها روی ویجت فقط یک نقطه دیده می‌شود.
            if (n.todayRial <= 0) {
                views.setTextViewText(R.id.widget_today, context.getString(R.string.widget_zero))
                views.setTextViewTextSize(R.id.widget_today, TypedValue.COMPLEX_UNIT_SP, if (small) 18f else 22f)
                views.setViewVisibility(R.id.widget_unit, View.GONE)
            } else {
                val (number, unit) = Money.compactParts(n.todayRial)
                views.setTextViewText(R.id.widget_today, number)
                views.setTextViewText(
                    R.id.widget_unit,
                    listOf(unit, context.getString(R.string.unit_toman)).filter { it.isNotEmpty() }.joinToString(" "),
                )
                views.setTextColor(R.id.widget_unit, muted)
            }
            views.setTextColor(R.id.widget_today, text)

            val budget = n.overallBudgetRial?.takeIf { it > 0 }
            if (budget == null) {
                views.setTextViewText(R.id.widget_month, context.getString(R.string.widget_month, Money.compact(n.monthRial)))
                views.setTextColor(R.id.widget_month, muted)
                return views
            }

            val left = budget - n.monthRial
            val percent = (n.monthRial * 100 / budget).toInt()
            val percentText = Jalali.toPersianDigits("${percent.coerceAtMost(999)}٪")
            if (small) {
                views.setTextViewText(
                    R.id.widget_month,
                    if (left >= 0) context.getString(R.string.widget_left_small, Money.compact(left), percentText)
                    else context.getString(R.string.widget_over_small, Money.compact(-left), percentText),
                )
                views.setTextColor(R.id.widget_month, text)
                return views
            }

            // خط راهنما زیر عدد: امروز نسبت به سهم روزانه کجاست
            val share = dailyShare(n, now)
            val days = daysLeft(now)
            val hint = when {
                share == null -> if (days == 1) context.getString(R.string.widget_last_day)
                else context.getString(R.string.widget_days_left, Jalali.toPersianDigits(days.toString()))
                // «هنوز خرجی نکردی» + «هنوز X جا داری» تکراری است
                n.todayRial <= 0 -> context.getString(R.string.widget_share_full, Money.compact(share))
                n.todayRial <= share -> context.getString(R.string.widget_share_left, Money.compact(share - n.todayRial))
                else -> context.getString(R.string.widget_share_over, Money.compact(n.todayRial - share))
            }
            views.setTextViewText(R.id.widget_sub, hint)
            views.setTextColor(R.id.widget_sub, muted)
            views.setViewVisibility(R.id.widget_sub, View.VISIBLE)

            views.setViewVisibility(R.id.widget_bar, View.VISIBLE)
            views.setProgressBar(R.id.widget_bar, 100, percent.coerceIn(0, 100), false)
            views.setContentDescription(R.id.widget_bar, context.getString(R.string.widget_bar_cd, percentText))
            views.setTextViewText(
                R.id.widget_month,
                if (left >= 0) context.getString(R.string.widget_left, Money.compact(left))
                else context.getString(R.string.widget_over, Money.compact(-left)),
            )
            views.setTextColor(R.id.widget_month, text)
            views.setViewVisibility(R.id.widget_percent, View.VISIBLE)
            views.setTextViewText(R.id.widget_percent, percentText)
            views.setTextColor(R.id.widget_percent, text)
            return views
        }

        /** متن کم‌رنگ روی زمینه‌ی رنگی: سفید ۸۵٪ */
        private const val MUTED_ON_MOOD = 0xD9FFFFFF.toInt()

        /** خرج امروز و این ماه با همان قانون‌های صفحه‌ی خلاصه (انتقال به خودم و دسته‌های «خرج نیست» حساب نمی‌شوند) */
        suspend fun numbers(db: ir.jibito.app.data.local.AppDatabase, now: Long): Numbers {
            val dao = db.summaryDao()
            val categories = db.categoryDao().all().filter { it.flowType == FlowType.WITHDRAWAL.code }
            val dayStart = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayEnd = (dayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
            val month = JalaliMonth.of(now)
            val today = SpendRollup.rollup(categories, dao.sums(FlowType.WITHDRAWAL.code, dayStart.timeInMillis, dayEnd.timeInMillis)).total
            val monthTotal = SpendRollup.rollup(categories, dao.sums(FlowType.WITHDRAWAL.code, month.startMillis(), month.endMillis())).total
            return Numbers(today, monthTotal, dao.overallBudget()?.monthlyLimitRial)
        }
    }
}
