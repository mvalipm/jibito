package ir.jibito.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import ir.jibito.app.JibitoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * «تا آخر ماه ساکت» روی هشدار بودجه: برای همین دسته (یا بودجه‌ی کل) در همین ماه، دیگر هشداری نمی‌آید.
 * همان قانون «هر سطح یک بار در ماه» است؛ فقط سطح هشدارِ داده‌شده را ۱۰۰ می‌گذارد.
 */
class BudgetMuteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1)
        val monthKey = intent.getIntExtra(EXTRA_MONTH_KEY, -1)
        if (monthKey < 0) return
        val dao = (context.applicationContext as JibitoApplication).container.database.summaryDao()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (categoryId >= 0) dao.markAlerted(categoryId, monthKey, 100) else dao.markOverallAlerted(monthKey, 100)
                NotificationManagerCompat.from(context).cancel(notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION = "ir.jibito.app.MUTE_BUDGET"
        private const val EXTRA_NOTIFICATION_ID = "notificationId"
        private const val EXTRA_CATEGORY_ID = "categoryId"
        private const val EXTRA_MONTH_KEY = "monthKey"

        /** @param categoryId null یعنی بودجه‌ی کل ماه */
        fun intent(context: Context, notificationId: Int, categoryId: Long?, monthKey: Int): PendingIntent {
            val intent = Intent(context, BudgetMuteReceiver::class.java)
                .setAction(ACTION)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(EXTRA_CATEGORY_ID, categoryId ?: -1L)
                .putExtra(EXTRA_MONTH_KEY, monthKey)
            return PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
