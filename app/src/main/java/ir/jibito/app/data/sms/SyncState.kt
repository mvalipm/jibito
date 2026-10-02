package ir.jibito.app.data.sms

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

/**
 * حافظه‌ی کوچک همگام‌سازی (در SharedPreferences):
 * - آخرین پیامکی که پردازش شد (_id و زمانش) ← دفعه‌ی بعد فقط پیامک‌های بعد از آن خوانده می‌شوند.
 * - زمان آخرین «اسکن کامل» ← هفته‌ای یک بار کل صندوق دوباره خوانده می‌شود (برای اطمینان، مثلاً پیامک‌های پاک‌شده)،
 *   و همیشه بعد از نصب نسخه‌ی تازه‌ی اپ (تا پارسرهای بهترشده روی پیامک‌های قدیمی هم اجرا شوند).
 * - سرشماره‌های ناشناسی که «رمز پویا/رمز دوم» فرستاده‌اند ← نشانه‌ی این‌که احتمالاً بانک‌اند.
 */
class SyncState(context: Context) {

    private val prefs = context.getSharedPreferences("sms_sync_state", Context.MODE_PRIVATE)

    /** نسخه‌ی نصب‌شده‌ی اپ (versionCode) */
    private val appVersion: Long = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(0L)

    val lastSmsId: Long get() = prefs.getLong(KEY_LAST_ID, 0L)
    val lastSmsDate: Long get() = prefs.getLong(KEY_LAST_DATE, 0L)
    val lastFullScanAt: Long get() = prefs.getLong(KEY_LAST_FULL, 0L)

    val otpSenders: Set<String> get() = prefs.getStringSet(KEY_OTP_SENDERS, emptySet()).orEmpty()

    /** اسکن کامل لازم است؟ بار اول، بعد از به‌روزرسانی اپ، یا اگر از آخرین اسکن کامل یک هفته گذشته باشد. */
    fun needsFullScan(now: Long): Boolean =
        needsFullScan(lastSmsId, lastFullScanAt, prefs.getLong(KEY_LAST_FULL_VERSION, -1L), appVersion, now)

    fun save(maxId: Long, maxDate: Long, otpSenders: Set<String>, fullScanAt: Long?) {
        prefs.edit().apply {
            if (maxId > lastSmsId) putLong(KEY_LAST_ID, maxId)
            if (maxDate > lastSmsDate) putLong(KEY_LAST_DATE, maxDate)
            if (fullScanAt != null) {
                putLong(KEY_LAST_FULL, fullScanAt)
                putLong(KEY_LAST_FULL_VERSION, appVersion)
            }
            putStringSet(KEY_OTP_SENDERS, HashSet(otpSenders))
        }.apply()
    }

    companion object {
        private const val KEY_LAST_ID = "last_sms_id"
        private const val KEY_LAST_DATE = "last_sms_date"
        private const val KEY_LAST_FULL = "last_full_scan_at"
        private const val KEY_LAST_FULL_VERSION = "last_full_scan_version"
        private const val KEY_OTP_SENDERS = "otp_senders"

        const val FULL_SCAN_INTERVAL_MILLIS = 7L * 24 * 60 * 60 * 1000

        fun needsFullScan(lastSmsId: Long, lastFullScanAt: Long, lastFullScanVersion: Long, appVersion: Long, now: Long): Boolean =
            lastSmsId == 0L ||
                lastFullScanVersion != appVersion ||
                now - lastFullScanAt >= FULL_SCAN_INTERVAL_MILLIS ||
                // ساعت گوشی عقب کشیده شده
                now < lastFullScanAt
    }
}
