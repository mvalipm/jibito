package ir.jibito.app.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.jibito.app.JibitoApplication
import ir.jibito.app.data.bank.SenderClassifier
import ir.jibito.app.data.bank.SenderType
import ir.jibito.app.notify.TransactionNotifier
import java.util.concurrent.TimeUnit

/**
 * پیامک که می‌رسد، اندروید این را صدا می‌زند — حتی وقتی اپ بسته است. (سند معماری بخش ۹.۱)
 * کار سنگینی این‌جا انجام نمی‌شود: فقط اگر فرستنده بانک بود، یک کار پس‌زمینه (WorkManager) زمان‌بندی می‌شود.
 */
class SmsReceivedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val fromBank = messages.any { SenderClassifier.classify(it.originatingAddress) is SenderType.BankSender }
        if (fromBank) SmsSyncWorker.enqueue(context)
    }
}

/**
 * همگام‌سازی پیامک‌ها با دیتابیس + نشان دادن نوتیفیکیشن برای برداشت‌های تازه.
 * چند ثانیه صبر می‌کند تا اپ پیامک گوشی، پیامک را در صندوق ذخیره کند؛
 * اگر چند پیامک پشت سر هم بیاید (مثلاً رمز دوم و بعد برداشت)، فقط یک بار اجرا می‌شود.
 */
class SmsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as JibitoApplication).container
        return try {
            container.transactionRepository.syncFromSms()
            TransactionNotifier(applicationContext, container.database).processRecent()
            container.budgetAlerter.check()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "sms-sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SmsSyncWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
