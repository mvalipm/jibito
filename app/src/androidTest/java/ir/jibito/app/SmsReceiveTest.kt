package ir.jibito.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import ir.jibito.app.TestSupport.app
import ir.jibito.app.data.sms.SmsReceivedReceiver
import ir.jibito.app.data.sms.SmsSyncWorker
import ir.jibito.app.notify.TransactionNotifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * مسیر رسیدن پیامک: گیرنده‌ی SMS_RECEIVED ← کار پس‌زمینه ← خواندن صندوق پیامک ← تراکنش و نوتیفیکیشن.
 *
 * پیامک بانکی صندوق را خود CI قبل از تست‌ها با `adb emu sms send` روی امولاتور می‌فرستد
 * (.github/workflows/build.yml، کار instrumented)؛ برای اجرای دستی همان دستور را بزنید:
 * adb emu sms send 20004000 "بانک ملی ایران\nبرداشت\nمبلغ:1,357,000\nمانده:9,000,000\nحساب:0101XXXX"
 */
@RunWith(AndroidJUnit4::class)
class SmsReceiveTest {

    @get:Rule
    val permissions = TestSupport.permissions(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    private fun smsReceived(sender: String, body: String) =
        Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            .putExtra("pdus", arrayOf<Any>(TestSupport.deliverPdu(sender, body)))
            .putExtra("format", "3gpp")

    private fun syncWork(): List<WorkInfo> =
        WorkManager.getInstance(app).getWorkInfosForUniqueWork(SmsSyncWorker.UNIQUE_NAME).get()

    @Test
    fun bankSmsSchedulesSync() {
        SmsReceivedReceiver().onReceive(app, smsReceived(BANK_SENDER, "برداشت:250,000 مانده:1,000,000"))

        val work = syncWork()
        assertTrue("پیامک بانک باید همگام‌سازی را زمان‌بندی کند", work.any { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun personalSmsIsIgnored() {
        SmsReceivedReceiver().onReceive(app, smsReceived("09121234567", "سلام، فردا می‌بینمت"))

        assertTrue("پیامک شخصی نباید کاری راه بیندازد", syncWork().isEmpty())
    }

    @Test
    fun syncReadsInboxSavesTransactionAndNotifies() = runBlocking {
        val worker = TestListenableWorkerBuilder.from(app, SmsSyncWorker::class.java).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())

        val db = app.container.database
        val id = db.openHelper.readableDatabase
            .query("SELECT id FROM transaction_flows WHERE amount = ? AND isDeleted = 0", arrayOf<Any>(INBOX_AMOUNT))
            .use { if (it.moveToFirst()) it.getLong(0) else null }
        assertNotNull("پیامک بانکی صندوق (فرستاده‌ی CI) باید تراکنش شده باشد", id)

        val row = db.transactionFlowDao().byId(id!!)!!
        assertEquals(1, row.bankId)
        assertEquals(INBOX_BALANCE, row.remainAfter)
        assertNotNull("برای برداشت تازه نوتیفیکیشن «مال چی بود؟» باید آمده باشد", row.notifiedAt)

        val manager = app.getSystemService(NotificationManager::class.java)
        val shown = manager.activeNotifications.any { it.id == TransactionNotifier.notificationId(id) }
        assertTrue("نوتیفیکیشن تراکنش باید نمایش داده شده باشد", shown)
    }

    private companion object {
        /** سرشماره‌ی بانک ملی */
        const val BANK_SENDER = "20004000"
        /** همان پیامکی که CI در صندوق امولاتور می‌گذارد */
        const val INBOX_AMOUNT = 1_357_000L
        const val INBOX_BALANCE = 9_000_000L
    }
}
