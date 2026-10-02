package ir.jibito.app

import android.app.Application
import android.content.Context
import ir.jibito.app.data.backup.BackupManager
import ir.jibito.app.data.sms.SmsSyncWorker
import ir.jibito.app.di.AppContainer
import ir.jibito.app.util.ErrorLog

/** کلاس اصلی اپ؛ فقط یک AppContainer برای کل اپ نگه می‌دارد. */
class JibitoApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // بازگردانی پشتیبانی که کاربر انتخاب کرده، قبل از این‌که دیتابیس یا تنظیمات باز شوند.
        // این‌جا (نه onCreate) چون قبل از راه افتادن WorkManager و هر کار پس‌زمینه‌ای اجرا می‌شود.
        BackupManager.applyPendingRestore(this)
    }

    override fun onCreate() {
        super.onCreate()
        // خطاهایی که اپ را می‌بندند، روی همین گوشی ثبت شوند (تنظیمات ← گزارش خطا)
        ErrorLog.installCrashHandler(this)
        // موسسه‌هایی که کاربر اضافه کرده، قبل از هر چیز شناخته شوند
        container.customInstitutions.load()
        // همگام‌سازی دوره‌ای پیامک‌ها (هر ۱۵ دقیقه) — اگر قبلاً زمان‌بندی شده باشد، تکرار نمی‌شود
        SmsSyncWorker.schedulePeriodic(this)
    }
}
