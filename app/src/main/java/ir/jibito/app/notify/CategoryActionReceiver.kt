package ir.jibito.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.jibito.app.JibitoApplication
import ir.jibito.app.data.repository.UndoSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** وقتی کاربر روی یکی از دکمه‌های دسته (یا «برگردون») در نوتیفیکیشن می‌زند. */
class CategoryActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1)
        if (transactionId < 0) return
        val undo = intent.action == ACTION_UNDO
        val categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1)
        if (!undo && categoryId < 0) return

        val pending = goAsync()
        val container = (context.applicationContext as JibitoApplication).container
        val repository = container.transactionRepository
        val notifier = TransactionNotifier(context, container.database)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (undo) {
                    // حالت قبل از دکمه (شامل تراکنش‌های هم‌فروشنده‌ای که با یادگیری دسته گرفتند)؛
                    // اگر اپ در این فاصله بسته شده بود، دست‌کم دسته‌ی همین تراکنش برداشته شود
                    val snapshot = undoSnapshots.remove(transactionId)
                    if (snapshot != null) repository.restore(snapshot) else repository.setCategory(transactionId, null)
                    notifier.askAgain(transactionId)
                } else {
                    undoSnapshots[transactionId] = repository.snapshotForUndo(transactionId)
                    repository.setCategory(transactionId, categoryId)
                    notifier.showChosen(transactionId, categoryId)
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

        /** حالت تراکنش قبل از زدن دکمه، برای «برگردون» (فقط چند ثانیه لازم است؛ در حافظه کافی است) */
        private val undoSnapshots = ConcurrentHashMap<Long, UndoSnapshot>()

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

        fun undoIntent(context: Context, transactionId: Long): PendingIntent {
            val intent = Intent(context, CategoryActionReceiver::class.java)
                .setAction(ACTION_UNDO)
                .putExtra(EXTRA_TRANSACTION_ID, transactionId)
            return PendingIntent.getBroadcast(
                context,
                TransactionNotifier.notificationId(transactionId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
