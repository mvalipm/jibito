package ir.jibito.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.jibito.app.JibitoApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** دکمه‌های یادآوری پرداخت: «✓ پرداخت کردم» و «بعداً یادم بنداز» */
class RecurringActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val paymentId = intent.getLongExtra(EXTRA_PAYMENT_ID, -1)
        val monthKey = intent.getIntExtra(EXTRA_MONTH_KEY, 0)
        if (paymentId < 0 || monthKey == 0) return

        val pending = goAsync()
        val dao = (context.applicationContext as JibitoApplication).container.database.recurringDao()
        val state = RecurringReminderState(context)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    // این ماه دیگر یادآوری نشود (نه «فردا موعد…» و نه روز موعد)
                    ACTION_PAID -> {
                        dao.markReminded(paymentId, monthKey)
                        state.clearSnooze(paymentId)
                    }
                    ACTION_SNOOZE -> {
                        dao.clearReminded(paymentId)
                        state.snooze(paymentId, RecurringSchedule.snoozeUntil(System.currentTimeMillis()), monthKey)
                    }
                }
                Notify.cancel(context, RecurringReminder.notificationId(paymentId))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_PAID = "ir.jibito.app.RECURRING_PAID"
        private const val ACTION_SNOOZE = "ir.jibito.app.RECURRING_SNOOZE"
        private const val EXTRA_PAYMENT_ID = "paymentId"
        private const val EXTRA_MONTH_KEY = "monthKey"

        fun paidIntent(context: Context, paymentId: Long, monthKey: Int) = intent(context, ACTION_PAID, paymentId, monthKey)

        fun snoozeIntent(context: Context, paymentId: Long, monthKey: Int) = intent(context, ACTION_SNOOZE, paymentId, monthKey)

        private fun intent(context: Context, action: String, paymentId: Long, monthKey: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                RecurringReminder.notificationId(paymentId),
                Intent(context, RecurringActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_PAYMENT_ID, paymentId)
                    .putExtra(EXTRA_MONTH_KEY, monthKey),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
