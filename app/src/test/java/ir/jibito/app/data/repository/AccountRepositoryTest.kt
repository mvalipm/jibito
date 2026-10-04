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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** دو حساب در یک بانک: مانده‌ی جدا، سؤال «یکی‌اند یا جدا؟»، و «برگردان» جواب */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var transactions: TransactionRepositoryImpl
    private lateinit var accounts: AccountRepository

    @Before
    fun setUp() {
        context.deleteDatabase(AppDatabase.NAME)
        db = AppDatabase.build(context)
        transactions = TransactionRepositoryImpl(db, SmsReader(context), SyncState(context))
        accounts = AccountRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(AppDatabase.NAME)
    }

    @Test
    fun twoAccountsInOneBankShowSeparatelyUntilMerged() = runBlocking {
        val dao = db.transactionFlowDao()
        dao.insert(tx(smsId = 1, date = 1_000, account = "5678", balance = 125_000_000))
        dao.insert(tx(smsId = 2, date = 2_000, account = "1234", balance = 214_000_000))
        dao.insert(tx(smsId = 3, date = 3_000, account = "1234", balance = 200_000_000))

        val before = transactions.observeBankBalances().first()
        assertEquals(listOf("1234", "5678"), before.map { it.account })
        assertEquals(325_000_000L, before.sumOf { it.balanceRial })

        val q = accounts.observeQuestion().first()
        assertNotNull(q)
        val undo = accounts.answer(q!!, same = true)
        assertNull(accounts.observeQuestion().first())
        assertEquals(listOf(200_000_000L), transactions.observeBankBalances().first().map { it.balanceRial })

        accounts.undo(undo)
        assertNotNull(accounts.observeQuestion().first())
        assertEquals(2, transactions.observeBankBalances().first().size)
    }

    @Test
    fun renameShowsOnTheBalanceAndCanBeCleared() = runBlocking {
        db.transactionFlowDao().insert(tx(smsId = 1, date = 1_000, account = "1234", balance = 5_000))
        accounts.rename(11, "1234", "حقوق")
        assertEquals("حقوق", transactions.observeBankBalances().first().single().name)
        accounts.rename(11, "1234", "")
        assertNull(transactions.observeBankBalances().first().single().name)
    }

    @Test
    fun bankWithoutAccountNumbersKeepsOneBalance() = runBlocking {
        db.transactionFlowDao().insert(tx(smsId = 1, date = 1_000, account = null, balance = 5_000))
        db.transactionFlowDao().insert(tx(smsId = 2, date = 2_000, account = null, balance = 7_000))
        val b = transactions.observeBankBalances().first().single()
        assertNull(b.account)
        assertEquals(7_000L, b.balanceRial)
        assertNull(accounts.observeQuestion().first())
    }

    private fun tx(smsId: Long, date: Long, account: String?, balance: Long) = TransactionFlowEntity(
        smsId = smsId,
        bankId = 11,
        flowType = 2,
        amount = 1_000,
        remainAfter = balance,
        dateEpoch = date,
        merchant = null,
        suggestedCategory = null,
        isFailedPurchase = false,
        categoryId = null,
        description = null,
        smsContent = "sms $smsId",
        source = SOURCE_SMS_AUTO,
        account = account,
    )
}
