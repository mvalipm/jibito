package ir.jibito.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import ir.jibito.app.JibitoApplication
import ir.jibito.app.util.ErrorLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** وقتی کاربر روی یکی از دکمه‌های دسته در نوتیفیکیشن می‌زند، یا در جعبه‌ی «بنویس» چیزی می‌نویسد. */
class CategoryActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1)
        if (intent.action == ACTION_REPLY) {
            if (transactionId >= 0) onReply(context, transactionId, intent)
            return
        }
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
                    if (name != null) {
                        notifier.showPicked(transactionId, name, merchant)
                        // اگر اندروید «رفت تو …» را دور ریخت، سؤال با دکمه‌هایش می‌ماند و کاربر فکر می‌کند ثبت نشد
                        delay(PICKED_RECHECK_MILLIS)
                        // (مگر این‌که همین حالا «برگردون» را زده باشد)
                        val stillPicked = container.database.transactionFlowDao().byId(transactionId)?.categoryId == categoryId
                        if (stillPicked && notifier.isQuestionStillShown(transactionId)) notifier.showPicked(transactionId, name, merchant)
                    } else {
                        NotificationManagerCompat.from(context).cancel(TransactionNotifier.notificationId(transactionId))
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * نوشته‌ی «بنویس»: همیشه یادداشت می‌شود؛ اگر با دسته‌ای جور شد «رفت تو …» (با «برگردون»)، وگرنه «📝 یادداشت شد».
     * نوتیفیکیشن حتماً باید عوض شود، وگرنه اندروید آن را در حالت «در حال ارسال» نگه می‌دارد.
     */
    private fun onReply(context: Context, transactionId: Long, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString().orEmpty()
        val pending = goAsync()
        val container = (context.applicationContext as JibitoApplication).container
        val notifier = TransactionNotifier(context, container.database)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val reply = container.transactionRepository.applyNoteReply(transactionId, text, container.categoryDisplay.depth.value)
                when {
                    // متن خالی ← همان سؤال دوباره (بی‌صدا)
                    reply == null -> notifier.askAgainAnyway(transactionId)
                    reply.categoryName != null -> {
                        val merchant = container.database.transactionFlowDao().byId(transactionId)?.merchant
                        notifier.showPicked(transactionId, reply.categoryName, merchant, note = reply.note)
                    }
                    else -> notifier.showNoted(transactionId, reply.note)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ErrorLog.record(context, "notification reply", e)
                NotificationManagerCompat.from(context).cancel(TransactionNotifier.notificationId(transactionId))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** کلید متن جعبه‌ی «بنویس» در RemoteInput */
        const val KEY_REPLY = "reply_text"
        /** فاصله‌ی دوباره نگاه کردن به «رفت تو …»؛ بیشتر از سقف حدود ۵ به‌روزرسانی در ثانیه‌ی اندروید */
        private const val PICKED_RECHECK_MILLIS = 1_000L
        private const val ACTION = "ir.jibito.app.SET_CATEGORY"
        private const val ACTION_UNDO = "ir.jibito.app.UNDO_CATEGORY"
        private const val ACTION_REPLY = "ir.jibito.app.REPLY_NOTE"
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
    
        /**
         * دکمه‌ی «بنویس». باید MUTABLE باشد تا اندروید متن نوشته‌شده را داخل Intent بگذارد
         * (Intent صریح است، پس فقط همین Receiver آن را می‌گیرد).
         */
        fun replyIntent(context: Context, transactionId: Long): PendingIntent {
            val intent = Intent(context, CategoryActionReceiver::class.java)
                .setAction(ACTION_REPLY)
                .putExtra(EXTRA_TRANSACTION_ID, transactionId)
            val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getBroadcast(
                context,
                (transactionId * 31 - 13).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
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
