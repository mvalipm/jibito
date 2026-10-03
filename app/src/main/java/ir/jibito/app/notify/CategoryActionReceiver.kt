package ir.jibito.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import ir.jibito.app.JibitoApplication
import ir.jibito.app.data.repository.UndoSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * دکمه‌های نوتیفیکیشن «مال چی بود؟»:
 * - یک دسته ← ثبت، و همان نوتیفیکیشن چند ثانیه «✓ رفت تو …» با «برگردون» می‌شود.
 * - «برگردون» ← حالت قبل برمی‌گردد (با یادگیری‌اش) و سؤال دوباره نشان داده می‌شود.
 */
class CategoryActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1)
        if (transactionId < 0) return
        val container = (context.applicationContext as JibitoApplication).container
        val repository = container.transactionRepository
        val db = container.database
        val notifier = TransactionNotifier(context, db)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_UNDO -> {
                        val snapshot = undoSnapshots.remove(transactionId)
                        if (snapshot != null) repository.restore(snapshot) else repository.setCategory(transactionId, null)
                        val flow = db.transactionFlowDao().byId(transactionId)
                        if (flow != null && flow.categoryId == null) notifier.showQuestion(flow)
                        else NotificationManagerCompat.from(context).cancel(TransactionNotifier.notificationId(transactionId))
                    }
                    else -> {
                        val categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1)
                        if (categoryId < 0) return@launch
                        undoSnapshots[transactionId] = repository.snapshotForUndo(transactionId)
                        repository.setCategory(transactionId, categoryId)
                        val flow = db.transactionFlowDao().byId(transactionId)
                        val category = db.categoryDao().byId(categoryId)
                        if (flow != null && category != null) notifier.showConfirmed(flow, category)
                        else NotificationManagerCompat.from(context).cancel(TransactionNotifier.notificationId(transactionId))
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_PICK = "ir.jibito.app.SET_CATEGORY"
        private const val ACTION_UNDO = "ir.jibito.app.UNDO_CATEGORY"
        private const val EXTRA_TRANSACTION_ID = "transactionId"
        private const val EXTRA_CATEGORY_ID = "categoryId"

        /**
         * حالت قبل از ثبت، برای «برگردون». فقط در حافظه (دکمه چند ثانیه بیشتر دیده نمی‌شود)؛
         * اگر در این فاصله اپ بسته شده باشد، فقط دسته‌ی همین تراکنش پاک می‌شود.
         */
        private val undoSnapshots = ConcurrentHashMap<Long, UndoSnapshot>()

        fun pickIntent(context: Context, transactionId: Long, categoryId: Long): PendingIntent {
            val intent = Intent(context, CategoryActionReceiver::class.java)
                .setAction(ACTION_PICK)
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
                (transactionId * 31 - 1).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
