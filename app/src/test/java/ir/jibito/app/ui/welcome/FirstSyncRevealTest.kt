package ir.jibito.app.ui.welcome

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.domain.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «جیبت رو شناختم» فقط بار اول و فقط وقتی پیش از آن هیچ تراکنشی نبود؛ چه موقع باز شدن اپ، چه بعد از دادن اجازه */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstSyncRevealTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var flag: FirstRunFlag
    private val transactions = MutableStateFlow<List<Transaction>>(emptyList())

    @Before
    fun fresh() {
        context.getSharedPreferences("first_run", Context.MODE_PRIVATE).edit(commit = true) { clear() }
        flag = FirstRunFlag(context)
        transactions.value = emptyList()
    }

    private fun tx(id: Long) = Transaction(
        id = id, bank = BankDirectory.byId(11), body = "", dateMillis = 1_700_000_000_000 + id,
        transaction = ParsedTransaction(FlowType.WITHDRAWAL, 10_000), merchant = null, suggestedCategory = null,
        isFailedPurchase = false, categoryId = null, categoryName = null, categoryIcon = null, isAutoCategorized = false,
    )

    /** خواندن پیامک‌ها که [count] تراکنش تازه پیدا می‌کند */
    private fun syncFinding(count: Int): suspend () -> Int = {
        transactions.value = transactions.value + (1..count).map { tx(transactions.value.size + it.toLong()) }
        count
    }

    @Test
    fun firstSyncWithNoEarlierDataShowsTheReveal() = runBlocking {
        val found = syncWithReveal(flag, transactions, syncFinding(3))
        assertEquals(3, found)
        assertTrue(flag.done)
        assertEquals(3, flag.pending.value?.count)
    }

    @Test
    fun manualEntriesBeforeTheFirstSyncSkipTheReveal() = runBlocking {
        // «فعلاً دستی»: کاربر پیش از دادن اجازه خودش خرج ثبت کرده
        transactions.value = listOf(tx(100))
        syncWithReveal(flag, transactions, syncFinding(2))
        assertTrue(flag.done)
        assertNull(flag.pending.value)
    }

    @Test
    fun laterSyncsNeverShowItAgain() = runBlocking {
        flag.markDone()
        syncWithReveal(flag, transactions, syncFinding(5))
        assertNull(flag.pending.value)
    }

    @Test
    fun nothingFoundMarksDoneWithoutReveal() = runBlocking {
        syncWithReveal(flag, transactions, syncFinding(0))
        assertTrue(flag.done)
        assertNull(flag.pending.value)
    }

    @Test
    fun failedSyncLeavesTheFirstRunForNextTime() {
        val result = runCatching {
            runBlocking { syncWithReveal(flag, transactions) { throw IllegalStateException("no sms") } }
        }
        assertTrue(result.isFailure)
        assertFalse(flag.done)
        assertNull(flag.pending.value)
    }
}
