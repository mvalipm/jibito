package ir.jibito.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.jibito.app.TestSupport.app
import ir.jibito.app.data.backup.BackupCrypto
import ir.jibito.app.data.backup.BackupManager
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.notify.NotificationStyle
import ir.jibito.app.notify.NotificationStyleSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * بازگردانی پشتیبان روی فایل واقعی دیتابیس اپ، همان دو مرحله‌ی اپ:
 * stageRestore (بررسی و کنار گذاشتن) ← شروع دوباره‌ی اپ ← applyPendingRestore (جایگزینی پیش از باز شدن دیتابیس).
 * «شروع دوباره» با بستن دیتابیس و باز کردن نمونه‌ی تازه شبیه‌سازی می‌شود؛ Orchestrator هر تست را در فرایند جدا اجرا می‌کند.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    private val password = "test-pass-1405".toCharArray()

    @Before
    fun setUp() = TestSupport.stopBackgroundWork()

    private fun merchants(db: AppDatabase): Set<String?> =
        db.openHelper.readableDatabase.query("SELECT merchant FROM transaction_flows WHERE isDeleted = 0").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    @Test
    fun restoreBringsBackDataAndSettings() = runBlocking {
        val db = app.container.database
        db.transactionFlowDao().insert(TestSupport.smsWithdrawal(smsId = 910_001, amountRial = 2_000_000, merchant = "پیش از پشتیبان"))
        NotificationStyleSettings(app).set(NotificationStyle.REPLY)

        val backup = ByteArrayOutputStream().also { app.container.backupManager.export(it, password) }.toByteArray()

        // بعد از پشتیبان: داده و تنظیم عوض می‌شوند
        db.openHelper.writableDatabase.execSQL("DELETE FROM transaction_flows")
        db.transactionFlowDao().insert(TestSupport.smsWithdrawal(smsId = 910_002, amountRial = 3_000_000, merchant = "بعد از پشتیبان"))
        NotificationStyleSettings(app).set(NotificationStyle.BUTTONS)

        val summary = app.container.backupManager.stageRestore(ByteArrayInputStream(backup), password)
        assertEquals(1, summary.transactionCount)

        // «شروع دوباره»: دیتابیس بسته، بازگردانی آماده جایگزین می‌شود، بعد دیتابیس تازه باز می‌شود
        db.close()
        assertTrue("بازگردانی آماده باید اعمال شود", BackupManager.applyPendingRestore(app))
        assertFalse("بازگردانی فقط یک بار اعمال می‌شود", BackupManager.applyPendingRestore(app))

        val restored = AppDatabase.build(app)
        try {
            assertEquals(setOf("پیش از پشتیبان"), merchants(restored))
        } finally {
            restored.close()
        }
        assertEquals(NotificationStyle.REPLY, NotificationStyleSettings(app).style.value)
    }

    @Test
    fun wrongPasswordChangesNothing() = runBlocking {
        val db = app.container.database
        db.transactionFlowDao().insert(TestSupport.smsWithdrawal(smsId = 920_001, amountRial = 1_000_000, merchant = "دست‌نخورده"))
        val backup = ByteArrayOutputStream().also { app.container.backupManager.export(it, password) }.toByteArray()

        try {
            app.container.backupManager.stageRestore(ByteArrayInputStream(backup), "wrong".toCharArray())
            fail("رمز اشتباه باید رد شود")
        } catch (e: BackupCrypto.WrongPasswordException) {
            // درست
        }
        assertFalse("با رمز اشتباه چیزی برای بازگردانی آماده نمی‌شود", BackupManager.applyPendingRestore(app))
        assertEquals(setOf("دست‌نخورده"), merchants(db))
    }
}
