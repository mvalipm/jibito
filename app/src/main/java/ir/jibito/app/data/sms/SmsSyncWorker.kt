package ir.jibito.app.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.jibito.app.JibitoApplication
import ir.jibito.app.data.bank.SenderClassifier
import ir.jibito.app.data.bank.SenderType
import ir.jibito.app.notify.TransactionNotifier
import ir.jibito.app.util.ErrorLog
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * پیامک که می‌رسد، اندروید این را صدا می‌زند — حتی وقتی اپ بسته است. (سند معماری بخش ۹.۱)
 * کار سنگینی این‌جا انجام نمی‌شود: فقط اگر فرستنده بانک بود، یک کار پس‌زمینه (WorkManager) زمان‌بندی می‌شود.
 */
class SmsReceivedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // بانک‌های رسمی، و فرستنده‌های ناشناس (شاید کاربر آن‌ها را به یک بانک/موسسه نسبت داده باشد).
        // پیامک شماره‌های شخصی هیچ‌وقت کاری راه نمی‌اندازد.
        // شماره‌ی شبه‌شخصی هم اگر متنش «مانده/موجودی» دارد (مثل بلوبانک)، همان لحظه بررسی شود
        val worthChecking = messages.any {
            SenderClassifier.classify(it.originatingAddress) != SenderType.Personal ||
                it.messageBody.orEmpty().let { b -> b.contains("مانده") || b.contains("موجودی") }
        }
        if (worthChecking) SmsSyncWorker.enqueue(context)
    }
}

/**
 * همگام‌سازی پیامک‌ها با دیتابیس + نشان دادن نوتیفیکیشن برای برداشت‌های تازه.
 * چند ثانیه صبر می‌کند تا اپ پیامک گوشی، پیامک را در صندوق ذخیره کند؛
 * اگر چند پیامک پشت سر هم بیاید (مثلاً رمز دوم و بعد برداشت)، فقط یک بار اجرا می‌شود.
 */
class SmsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // بدون اجازه‌ی خواندن پیامک کاری نمی‌شود کرد (مثلاً کاربر هنوز اجازه نداده)
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }
        val container = (applicationContext as JibitoApplication).container
        return try {
            container.transactionRepository.syncFromSms()
            TransactionNotifier(applicationContext, container.database).processRecent()
            container.onDataChanged() // هشدار بودجه + ویجت
            container.recurringReminder.check()
            container.weeklyDigest.check()
            Result.success()
        } catch (e: CancellationException) {
            // لغو شدن خطا نیست (مثلاً پیامک تازه کار قبلی را جایگزین کرده یا اندروید کار را متوقف کرده)؛
            // ثبت نمی‌شود و باید دوباره پرتاب شود تا WorkManager خودش تصمیم بگیرد
            throw e
        } catch (e: Exception) {
            // قبلاً خطا بی‌صدا تکرار می‌شد و هیچ ردی نمی‌ماند؛ حالا ثبت می‌شود (تنظیمات ← گزارش خطا)
            ErrorLog.record(applicationContext, "sync (attempt ${runAttemptCount + 1})", e)
            // چند بار دوباره؛ بعد رها می‌شود (همگام‌سازی دوره‌ای ۱۵ دقیقه‌ای و پیامک بعدی دوباره امتحان می‌کنند)
            if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "sms-sync"
        private const val PERIODIC_NAME = "sms-sync-periodic"
        private const val MAX_ATTEMPTS = 3

        /**
         * شبکه‌ی ایمنی: هر ۱۵ دقیقه (کمترین فاصله‌ی مجاز اندروید) پیامک‌های تازه خوانده می‌شوند،
         * حتی اگر گیرنده‌ی پیامک اجرا نشده باشد (مثلاً به خاطر بهینه‌سازی باتری).
         * KEEP: اگر قبلاً زمان‌بندی شده، دست نمی‌خورد.
         */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SmsSyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SmsSyncWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
