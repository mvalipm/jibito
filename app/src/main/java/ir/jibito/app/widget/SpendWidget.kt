package ir.jibito.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import ir.jibito.app.JibitoApplication
import ir.jibito.app.MainActivity
import ir.jibito.app.R
import ir.jibito.app.data.category.SpendRollup
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.util.ErrorLog
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ویجت صفحه‌ی اصلی: «امروز چقدر خرج کردم» + خرج این ماه + باقی‌مانده‌ی بودجه‌ی کل ماه.
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
    data class Numbers(val todayRial: Long, val monthRial: Long, val overallBudgetRial: Long?)

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
            val views = RemoteViews(context.packageName, R.layout.widget_spend)
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            if (container.appLockSettings.enabled.value) {
                views.setTextViewText(R.id.widget_label, context.getString(R.string.widget_locked_label))
                views.setTextViewText(R.id.widget_today, "🔒")
                views.setTextViewText(R.id.widget_month, context.getString(R.string.widget_locked_hint))
                views.setTextViewText(R.id.widget_left, "")
                return views
            }

            val n = numbers(container.database, System.currentTimeMillis())
            views.setTextViewText(R.id.widget_label, context.getString(R.string.widget_today))
            views.setTextViewText(R.id.widget_today, context.getString(R.string.widget_amount, Money.compact(n.todayRial)))
            views.setTextViewText(R.id.widget_month, context.getString(R.string.widget_month, Money.compact(n.monthRial)))
            val left = n.overallBudgetRial?.let { it - n.monthRial }
            when {
                left == null -> views.setTextViewText(R.id.widget_left, "")
                left >= 0 -> {
                    views.setTextViewText(R.id.widget_left, context.getString(R.string.widget_left, Money.compact(left)))
                    views.setTextColor(R.id.widget_left, ContextCompat.getColor(context, R.color.widget_accent))
                }
                else -> {
                    views.setTextViewText(R.id.widget_left, context.getString(R.string.widget_over, Money.compact(-left)))
                    views.setTextColor(R.id.widget_left, ContextCompat.getColor(context, R.color.widget_over))
                }
            }
            return views
        }

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
