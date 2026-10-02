package ir.jibito.app

import android.app.Application
import ir.jibito.app.data.sms.SmsSyncWorker
import ir.jibito.app.di.AppContainer

/** کلاس اصلی اپ؛ فقط یک AppContainer برای کل اپ نگه می‌دارد. */
class JibitoApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // موسسه‌هایی که کاربر اضافه کرده، قبل از هر چیز شناخته شوند
        container.customInstitutions.load()
        // همگام‌سازی دوره‌ای پیامک‌ها (هر ۱۵ دقیقه) — اگر قبلاً زمان‌بندی شده باشد، تکرار نمی‌شود
        SmsSyncWorker.schedulePeriodic(this)
    }
}
