package ir.jibito.app.data.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStateTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test
    fun firstRunScansEverything() {
        assertTrue(SyncState.needsFullScan(lastSmsId = 0, lastFullScanAt = now, lastFullScanVersion = 42, appVersion = 42, now = now))
    }

    @Test
    fun withinAWeekOnSameVersionIsIncremental() {
        assertFalse(SyncState.needsFullScan(lastSmsId = 5, lastFullScanAt = now - 6 * day, lastFullScanVersion = 42, appVersion = 42, now = now))
    }

    @Test
    fun afterAWeekScansEverything() {
        assertTrue(SyncState.needsFullScan(lastSmsId = 5, lastFullScanAt = now - 7 * day, lastFullScanVersion = 42, appVersion = 42, now = now))
    }

    @Test
    fun appUpdateScansEverything() {
        // پارسرهای نسخه‌ی تازه باید روی پیامک‌های قدیمی هم اجرا شوند
        assertTrue(SyncState.needsFullScan(lastSmsId = 5, lastFullScanAt = now - day, lastFullScanVersion = 41, appVersion = 42, now = now))
        // کاربری که از نسخه‌ی قبل از این تغییر می‌آید (نسخه ذخیره نشده)
        assertTrue(SyncState.needsFullScan(lastSmsId = 5, lastFullScanAt = now - day, lastFullScanVersion = -1, appVersion = 42, now = now))
    }

    @Test
    fun clockMovedBackScansEverything() {
        assertTrue(SyncState.needsFullScan(lastSmsId = 5, lastFullScanAt = now + day, lastFullScanVersion = 42, appVersion = 42, now = now))
    }
}
