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

/** وقتی کاربر روی یکی از دکمه‌های دسته در نوتیفیکیشن می‌زند. */
class CategoryActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1)
        val categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1)
        if (transactionId < 0 || categoryId < 0) return

        val pending = goAsync()
        val repository = (context.applicationContext as JibitoApplication).container.transactionRepository
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.setCategory(transactionId, categoryId)
                NotificationManagerCompat.from(context).cancel(TransactionNotifier.notificationId(transactionId))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION = "ir.jibito.app.SET_CATEGORY"
        private const val EXTRA_TRANSACTION_ID = "transactionId"
        private const val EXTRA_CATEGORY_ID = "categoryId"

        fun pendingIntent(context: Context, transactionId: Long, categoryId: Long): PendingIntent {
            val intent = Intent(context, CategoryActionReceiver::class.java)
                .setAction(ACTION)
                .putExtra(EXTRA_TRANSACTION_ID, transactionId)
                .putExtra(EXTRA_CATEGORY_ID, categoryId)
            // requestCode یکتا برای هر (تراکنش، دسته) تا PendingIntent ها روی هم نیفتند
            val requestCode = (transactionId * 31 + categoryId).hashCode()
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
