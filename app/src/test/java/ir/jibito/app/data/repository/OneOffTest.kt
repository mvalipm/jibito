package ir.jibito.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.data.sms.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** علامت «خرج یک‌باره» (خرید خانه، ماشین…) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OneOffTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepositoryImpl

    @Before
    fun setUp() {
        context.deleteDatabase(AppDatabase.NAME)
        db = AppDatabase.build(context)
        repo = TransactionRepositoryImpl(db, SmsReader(context), SyncState(context))
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(AppDatabase.NAME)
    }

    @Test
    fun markAndUnmark() = runBlocking {
        val id = db.transactionFlowDao().insert(tx(smsId = 1))
        assertEquals(TransactionFlowEntity.ONE_OFF_NONE, db.transactionFlowDao().byId(id)!!.oneOffState)

        repo.setOneOff(id, true)
        assertEquals(TransactionFlowEntity.ONE_OFF_YES, db.transactionFlowDao().byId(id)!!.oneOffState)
        // (فهرست مشترک تراکنش‌ها یک بار خوانده می‌شود؛ بار دوم ممکن است هنوز مقدار قبلی را داشته باشد)
        assertTrue(repo.observeTransactions().first().single { it.id == id }.isOneOff)

        // برداشتن علامت = «یک‌باره نیست» (بعداً دوباره پیشنهاد نمی‌شود)
        repo.setOneOff(id, false)
        assertEquals(TransactionFlowEntity.ONE_OFF_REJECTED, db.transactionFlowDao().byId(id)!!.oneOffState)
    }

    @Test
    fun oneOffIsPartOfSyncKeysSoRescanKeepsIt() = runBlocking {
        val id = db.transactionFlowDao().insert(tx(smsId = 1))
        repo.setOneOff(id, true)
        assertEquals(TransactionFlowEntity.ONE_OFF_YES, db.transactionFlowDao().smsKeys().single { it.id == id }.oneOffState)
    }

    private fun tx(smsId: Long) = TransactionFlowEntity(
        smsId = smsId,
        bankId = 1,
        flowType = 2,
        amount = 10_000_000_000,
        remainAfter = null,
        dateEpoch = 1_000,
        merchant = null,
        suggestedCategory = null,
        isFailedPurchase = false,
        categoryId = null,
        description = null,
        smsContent = "sms $smsId",
        source = SOURCE_SMS_AUTO,
    )
}
