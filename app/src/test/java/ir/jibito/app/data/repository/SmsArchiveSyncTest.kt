package ir.jibito.app.data.repository

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.SmsFlowKey
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.data.sms.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * کپی پیامک‌ها در خود اپ (sms_archive): پاک شدن پیامک از گوشی، گوشی تازه یا بازگردانی پشتیبان
 * نباید تاریخچه‌ی تراکنش‌ها (و دسته‌هایی که کاربر گذاشته) را پاک کند.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsArchiveSyncTest {

    /** صندوق پیامک ساختگی (content://sms/inbox)؛ فیلتر اسکن افزایشی را نادیده می‌گیرد، پس تست‌ها اسکن کامل‌اند */
    class FakeSmsProvider : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
            val cursor = MatrixCursor(arrayOf("_id", "address", "body", "date"))
            inbox.sortedByDescending { it.date }.forEach { cursor.addRow(arrayOf<Any>(it.id, it.address, it.body, it.date)) }
            return cursor
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }

    data class Sms(val id: Long, val address: String, val body: String, val date: Long)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepositoryImpl

    private val first = Sms(1, "BankMellat", "بانک ملت\nبرداشت از 1234567890\nمبلغ:250,000\nموجودی:12,345,678\n0423-18:31", 1_700_000_000_000)
    private val second = Sms(2, "BankMellat", "بانک ملت\nبرداشت از 1234567890\nمبلغ:400,000\nموجودی:11,945,678\n0424-09:10", 1_700_000_500_000)

    @Before
    fun setUp() {
        inbox.clear()
        Robolectric.setupContentProvider(FakeSmsProvider::class.java, "sms")
        context.deleteDatabase(AppDatabase.NAME)
        context.getSharedPreferences("sms_sync_state", Context.MODE_PRIVATE).edit().clear().commit()
        db = AppDatabase.build(context)
        repo = TransactionRepositoryImpl(db, SmsReader(context), SyncState(context))
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(AppDatabase.NAME)
        inbox.clear()
    }

    private suspend fun live(): List<SmsFlowKey> = db.transactionFlowDao().smsKeys().filter { !it.isDeleted }.sortedBy { it.dateEpoch }

    @Test
    fun transactionsAreArchived() = runBlocking {
        inbox += listOf(first, second)
        assertEquals(2, repo.syncFromSms(forceFull = true))
        assertEquals(2, db.smsArchiveDao().count())
        // دوباره: نه تراکنش تکراری، نه کپی تکراری
        assertEquals(0, repo.syncFromSms(forceFull = true))
        assertEquals(2, db.smsArchiveDao().count())
        assertEquals(2, live().size)
    }

    @Test
    fun deletingSmsFromPhoneKeepsTransactionAndCategory() = runBlocking {
        inbox += listOf(first, second)
        repo.syncFromSms(forceFull = true)
        val food = db.categoryDao().insert(CategoryEntity(name = "غذای تست", icon = "🍔", colorHex = "#000000", flowType = 2))
        val firstRow = live().first()
        repo.setCategory(firstRow.id, food)

        // کاربر پیامک اول را از گوشی پاک کرد
        inbox.removeAll { it.id == first.id }
        repo.syncFromSms(forceFull = true)

        val rows = live()
        assertEquals(listOf(first.date, second.date), rows.map { it.dateEpoch })
        assertEquals(food, rows.first().categoryId)
        assertEquals(listOf(250_000L, 400_000L), rows.map { db.transactionFlowDao().byId(it.id)!!.amount })
    }

    @Test
    fun newPhoneWithoutOldSmsKeepsEverything() = runBlocking {
        inbox += listOf(first, second)
        repo.syncFromSms(forceFull = true)

        // گوشی تازه / بازگردانی پشتیبان: صندوق خالی و وضعیت همگام‌سازی پاک
        inbox.clear()
        context.getSharedPreferences("sms_sync_state", Context.MODE_PRIVATE).edit().clear().commit()
        repo = TransactionRepositoryImpl(db, SmsReader(context), SyncState(context))
        repo.syncFromSms()

        assertEquals(2, live().size)
        // یک پیامک تازه در گوشی تازه، با شناسه‌ای که قبلاً مال پیامک دیگری بود
        val fresh = Sms(1, "BankMellat", "بانک ملت\nواریز به 1234567890\nمبلغ:1,000,000\nموجودی:12,945,678\n0501-10:00", 1_701_000_000_000)
        inbox += fresh
        repo.syncFromSms(forceFull = true)
        assertEquals(listOf(first.date, second.date, fresh.date), live().map { it.dateEpoch })
    }

    @Test
    fun rowDeletedByOlderVersionComesBackFromArchive() = runBlocking {
        inbox += listOf(first, second)
        repo.syncFromSms(forceFull = true)
        // نسخه‌های قبل، پیامکِ پاک‌شده از گوشی را «حذف نرم» می‌کردند
        val firstRow = live().first()
        db.transactionFlowDao().softDelete(listOf(firstRow.id), 1)
        inbox.clear()

        repo.syncFromSms(forceFull = true)

        val rows = live()
        assertEquals(2, rows.size)
        assertEquals(firstRow.id, rows.first().id)
    }

    companion object {
        val inbox = mutableListOf<Sms>()
    }
}
