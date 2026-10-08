package ir.jibito.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
 * ویجت صفحه‌ی اصلی: «امروز چقدر خرج کردم» + خرج این ماه + باقی‌مانده و حلقه‌ی بودجه‌ی کل ماه.
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

    /** اعداد ویجت (به ریال) */
    data class Numbers(
        val todayRial: Long,
        val monthRial: Long,
        val overallBudgetRial: Long?,
        /** چه مقدار از monthRial خرج یک‌باره است (از بودجه کم نمی‌شود) */
        val monthOneOffRial: Long = 0,
    ) {
        /** خرجی که از بودجه‌ی کل کم می‌شود */
        val budgetSpentRial: Long get() = monthRial - monthOneOffRial
    }

    companion object {

        /** همه‌ی ویجت‌های روی صفحه را به‌روز می‌کند؛ اگر ویجتی نیست، کاری نمی‌کند. */
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, SpendWidget::class.java))
            if (ids.isEmpty()) return
            render(context, manager, ids)
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val views = try {
                build(context)
            } catch (e: Exception) {
                ErrorLog.record(context, "widget", e)
                return
            }
            for (id in ids) manager.updateAppWidget(id, views)
        }

        private suspend fun build(context: Context): RemoteViews {
            val container = (context.applicationContext as JibitoApplication).container
            val locked = container.appLockSettings.enabled.value
            val now = System.currentTimeMillis()
            val n = if (locked) null else numbers(container.database, now)
            return views(context, n, now)
        }

        /** حال جیب، مثل بالای صفحه‌ی خلاصه */
        internal enum class Mood { NONE, CALM, WARN, OVER }

        /** کمتر از این فاصله بین «خرج» و «زمان» یعنی «طبق برنامه» (مثل صفحه‌ی خلاصه) */
        private const val ON_TRACK_MARGIN = 0.05

        /** رنگ فقط از بودجه، مثل سرصفحه‌ی خلاصه (موجودی حساب رنگ را عوض نمی‌کند) */
        internal fun moodOf(n: Numbers, now: Long): Mood {
            val budget = n.overallBudgetRial?.takeIf { it > 0 } ?: return Mood.NONE
            val month = JalaliMonth.of(now)
            val time = (now - month.startMillis()).toDouble() / (month.endMillis() - month.startMillis())
            val spent = n.budgetSpentRial.toDouble() / budget
            return when {
                n.budgetSpentRial >= budget -> Mood.OVER
                spent >= 0.8 || spent - time >= ON_TRACK_MARGIN -> Mood.WARN
                else -> Mood.CALM
            }
        }

        /**
         * ظاهر ویجت از روی عددها (بدون دیتابیس؛ تست اسکرین‌شات هم همین را می‌کشد).
         * @param n null یعنی قفل اپ روشن است: مبلغ‌ها نشان داده نمی‌شوند
         */
        internal fun views(context: Context, n: Numbers?, now: Long): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_spend)
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            if (n == null) {
                views.setTextViewText(R.id.widget_label, context.getString(R.string.widget_locked_label))
                views.setTextViewText(R.id.widget_today, "🔒")
                views.setViewVisibility(R.id.widget_unit, View.GONE)
                views.setTextViewText(R.id.widget_month, context.getString(R.string.widget_locked_hint))
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

            // «۶۰۵ هزار» ← عدد درشت + «هزار تومان» کوچک کنارش
            val compact = Money.compact(n.todayRial)
            val split = compact.lastIndexOf(' ')
            val number = if (split > 0) compact.substring(0, split) else compact
            val unit = listOfNotNull(compact.substring(split + 1).takeIf { split > 0 }, context.getString(R.string.unit_toman))
                .joinToString(" ")
            views.setTextViewText(R.id.widget_today, number)
            views.setTextColor(R.id.widget_today, text)
            views.setTextViewText(R.id.widget_unit, unit)
            views.setTextColor(R.id.widget_unit, muted)
            // یک خط زیر عدد (جای ویجت کوچک است): با بودجه «چقدر مونده»، بدون بودجه «خرج این ماه»
            val budget = n.overallBudgetRial?.takeIf { it > 0 }
            if (budget == null) {
                views.setTextViewText(R.id.widget_month, context.getString(R.string.widget_month, Money.compact(n.monthRial)))
                views.setTextColor(R.id.widget_month, muted)
                views.setViewVisibility(R.id.widget_ring_box, View.GONE)
                return views
            }
            val left = budget - n.budgetSpentRial
            views.setTextViewText(
                R.id.widget_month,
                if (left >= 0) context.getString(R.string.widget_left, Money.compact(left))
                else context.getString(R.string.widget_over, Money.compact(-left)),
            )
            views.setTextColor(R.id.widget_month, text)
            val percent = (n.budgetSpentRial * 100 / budget).toInt()
            val percentText = Jalali.toPersianDigits("${percent.coerceAtMost(999)}٪")
            views.setViewVisibility(R.id.widget_ring_box, View.VISIBLE)
            views.setProgressBar(R.id.widget_ring, 100, percent.coerceIn(0, 100), false)
            views.setTextViewText(R.id.widget_percent, percentText)
            views.setContentDescription(R.id.widget_ring_box, context.getString(R.string.widget_ring_cd, percentText))
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
            val monthSpend = SpendRollup.rollup(categories, dao.sums(FlowType.WITHDRAWAL.code, month.startMillis(), month.endMillis()))
            return Numbers(today, monthSpend.total, dao.overallBudget()?.monthlyLimitRial, monthSpend.oneOffTotal)
        }
    }
}
