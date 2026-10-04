package ir.jibito.app.ui.screenshot

import androidx.work.testing.WorkManagerTestInitHelper
import ir.jibito.app.JibitoApplication

/**
 * خود اپ، برای تست‌هایی که MainActivity را باز می‌کنند.
 * در Robolectric راه‌انداز خودکار WorkManager اجرا نمی‌شود؛ پس قبل از زمان‌بندی همگام‌سازی پیامک،
 * یک WorkManager آزمایشی (که کاری را خودش اجرا نمی‌کند) راه می‌افتد.
 */
class ScreenshotApplication : JibitoApplication() {
    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(this)
        super.onCreate()
    }
}
