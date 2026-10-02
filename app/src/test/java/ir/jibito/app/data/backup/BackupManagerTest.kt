package ir.jibito.app.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** پشتیبان کامل ← تغییر داده‌ها ← بازگردانی ← همه‌چیز دقیقاً مثل لحظه‌ی پشتیبان‌گیری. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val password = "my-secret".toCharArray()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(AppDatabase.NAME)
        File(context.filesDir, "pending_restore").deleteRecursively()
        db = AppDatabase.build(context)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(AppDatabase.NAME)
    }

    @Test
    fun exportThenRestoreBringsBackEverything() = runBlocking {
        val categoryId = db.categoryDao().insert(CategoryEntity(name = "کافه", icon = "☕", colorHex = "#000000", flowType = 2))
        db.transactionFlowDao().insert(tx(smsId = 10, amount = 1_500_000, categoryId = categoryId))
        db.transactionFlowDao().insert(tx(smsId = 11, amount = 700_000, categoryId = null))
        prefs("ui_prefs").edit().putString("theme", "COOL").commit()
        prefs("custom_institutions").edit().putStringSet("institutions", setOf("1001|کارگزاری")).commit()
        prefs("sms_sync_state").edit().putLong("last_sms_id", 999).commit()
        prefs("security").edit().putBoolean("app_lock_enabled", true).commit()

        val manager = BackupManager(context, db)
        val file = ByteArrayOutputStream().also { manager.export(it, password) }.toByteArray()

        // بعد از پشتیبان: داده‌ها عوض می‌شوند
        db.transactionFlowDao().insert(tx(smsId = 12, amount = 5, categoryId = null))
        prefs("ui_prefs").edit().putString("theme", "WARM").commit()

        val summary = manager.stageRestore(ByteArrayInputStream(file), password)
        assertEquals(2, summary.transactionCount)
        assertTrue(summary.createdAt != null)

        // مثل شروع دوباره‌ی اپ
        db.close()
        assertTrue(BackupManager.applyPendingRestore(context))
        db = AppDatabase.build(context)

        val amounts = rawLongs("SELECT amount FROM transaction_flows ORDER BY smsId")
        assertEquals(listOf(1_500_000L, 700_000L), amounts)
        val restoredCategory = rawLongs("SELECT categoryId FROM transaction_flows WHERE smsId = 10").single()
        assertEquals(categoryId, restoredCategory)
        assertEquals("COOL", prefs("ui_prefs").getString("theme", null))
        assertEquals(setOf("1001|کارگزاری"), prefs("custom_institutions").getStringSet("institutions", null))
        // وضعیت همگام‌سازی پاک شد تا پیامک‌ها از نو خوانده شوند
        assertEquals(0L, prefs("sms_sync_state").getLong("last_sms_id", 0L))
        // قفل اپ مال همین گوشی است و با بازگردانی عوض نمی‌شود
        assertTrue(prefs("security").getBoolean("app_lock_enabled", false))
        // فقط یک بار اعمال می‌شود
        db.close()
        assertFalse(BackupManager.applyPendingRestore(context))
        db = AppDatabase.build(context)
    }

    @Test
    fun wrongPasswordChangesNothing() = runBlocking {
        db.transactionFlowDao().insert(tx(smsId = 10, amount = 1, categoryId = null))
        val manager = BackupManager(context, db)
        val file = ByteArrayOutputStream().also { manager.export(it, password) }.toByteArray()
        try {
            manager.stageRestore(ByteArrayInputStream(file), "nope-nope".toCharArray())
            throw AssertionError("expected WrongPasswordException")
        } catch (e: BackupCrypto.WrongPasswordException) {
            // درست
        }
        db.close()
        assertFalse(BackupManager.applyPendingRestore(context))
        db = AppDatabase.build(context)
    }

    @Test
    fun backupFromNewerAppIsRejected() {
        val file = File(context.cacheDir, "newer.db")
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { d ->
            d.execSQL("CREATE TABLE transaction_flows (id INTEGER PRIMARY KEY, isDeleted INTEGER NOT NULL)")
            d.version = AppDatabase.VERSION + 1
        }
        try {
            BackupManager.inspectDatabase(file)
            throw AssertionError("expected TooNewException")
        } catch (e: BackupManager.TooNewException) {
            // درست
        }
    }

    @Test
    fun garbageDatabaseIsDamaged() {
        val file = File(context.cacheDir, "garbage.db")
        file.writeText("this is not sqlite at all")
        try {
            BackupManager.inspectDatabase(file)
            throw AssertionError("expected DamagedException")
        } catch (e: BackupManager.DamagedException) {
            // درست
        }
    }

    @Test
    fun noPendingRestoreDoesNothing() {
        db.close()
        assertFalse(BackupManager.applyPendingRestore(context))
        assertNull(File(context.filesDir, "pending_restore").listFiles())
        db = AppDatabase.build(context)
    }

    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun rawLongs(sql: String): List<Long> =
        db.openHelper.readableDatabase.query(sql).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }

    private fun tx(smsId: Long, amount: Long, categoryId: Long?) = TransactionFlowEntity(
        smsId = smsId,
        bankId = 1,
        flowType = 2,
        amount = amount,
        remainAfter = null,
        dateEpoch = 1_700_000_000_000 + smsId,
        merchant = null,
        suggestedCategory = null,
        isFailedPurchase = false,
        categoryId = categoryId,
        description = null,
        smsContent = "sms $smsId",
        source = "SMS_AUTO",
    )
}
