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
        val undo = intent.action == ACTION_UNDO
        if (transactionId < 0 || (!undo && categoryId < 0)) return

        val pending = goAsync()
        val container = (context.applicationContext as JibitoApplication).container
        val repository = container.transactionRepository
        val notifier = TransactionNotifier(context, container.database)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (undo) {
                    // «برگردون»: دسته برداشته می‌شود و سؤال دوباره می‌آید
                    repository.setCategory(transactionId, null)
                    notifier.askAgain(transactionId)
                } else {
                    repository.setCategory(transactionId, categoryId)
                    val name = container.database.categoryDao().byId(categoryId)?.name
                    val merchant = container.database.transactionFlowDao().byId(transactionId)?.merchant
                    if (name != null) notifier.showPicked(transactionId, name, merchant)
                    else NotificationManagerCompat.from(context).cancel(TransactionNotifier.notificationId(transactionId))
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION = "ir.jibito.app.SET_CATEGORY"
        private const val ACTION_UNDO = "ir.jibito.app.UNDO_CATEGORY"
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
    
        /** «برگردون» روی نوتیفیکیشن «رفت تو …» */
        fun undoIntent(context: Context, transactionId: Long): PendingIntent {
            val intent = Intent(context, CategoryActionReceiver::class.java)
                .setAction(ACTION_UNDO)
                .putExtra(EXTRA_TRANSACTION_ID, transactionId)
            return PendingIntent.getBroadcast(
                context,
                (transactionId * 31 - 7).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
