package ir.jibito.app.data.sms

import android.content.Context

/**
 * حافظه‌ی کوچک همگام‌سازی (در SharedPreferences):
 * - آخرین پیامکی که پردازش شد (_id و زمانش) ← دفعه‌ی بعد فقط پیامک‌های بعد از آن خوانده می‌شوند.
 * - زمان آخرین «اسکن کامل» ← روزی یک بار کل صندوق دوباره خوانده می‌شود (برای اطمینان).
 * - سرشماره‌های ناشناسی که «رمز پویا/رمز دوم» فرستاده‌اند ← نشانه‌ی این‌که احتمالاً بانک‌اند.
 */
class SyncState(context: Context) {

    private val prefs = context.getSharedPreferences("sms_sync_state", Context.MODE_PRIVATE)

    val lastSmsId: Long get() = prefs.getLong(KEY_LAST_ID, 0L)
    val lastSmsDate: Long get() = prefs.getLong(KEY_LAST_DATE, 0L)
    val lastFullScanAt: Long get() = prefs.getLong(KEY_LAST_FULL, 0L)

    val otpSenders: Set<String> get() = prefs.getStringSet(KEY_OTP_SENDERS, emptySet()).orEmpty()

    /** اسکن کامل لازم است؟ بار اول، یا اگر از آخرین اسکن کامل یک روز گذشته باشد. */
    fun needsFullScan(now: Long): Boolean =
        lastSmsId == 0L || now - lastFullScanAt >= FULL_SCAN_INTERVAL_MILLIS

    fun save(maxId: Long, maxDate: Long, otpSenders: Set<String>, fullScanAt: Long?) {
        prefs.edit().apply {
            if (maxId > lastSmsId) putLong(KEY_LAST_ID, maxId)
            if (maxDate > lastSmsDate) putLong(KEY_LAST_DATE, maxDate)
            if (fullScanAt != null) putLong(KEY_LAST_FULL, fullScanAt)
            putStringSet(KEY_OTP_SENDERS, HashSet(otpSenders))
        }.apply()
    }

    companion object {
        private const val KEY_LAST_ID = "last_sms_id"
        private const val KEY_LAST_DATE = "last_sms_date"
        private const val KEY_LAST_FULL = "last_full_scan_at"
        private const val KEY_OTP_SENDERS = "otp_senders"

        const val FULL_SCAN_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
    }
}
