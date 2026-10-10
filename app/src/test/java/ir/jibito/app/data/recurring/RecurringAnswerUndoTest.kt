package ir.jibito.app.data.recurring

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.repository.RecurringRepository
import ir.jibito.app.data.repository.TransactionRepositoryImpl
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.data.sms.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «برگردون» بعد از «یادم بنداز» (یادآور پاک می‌شود) و «نه» (دوباره پیشنهاد می‌شود) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecurringAnswerUndoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var suggestions: RecurringSuggestions

    private val suggestion = RecurringSuggestion(key = "شارژ", title = "شارژ ساختمان", amountRial = 5_000_000, dayOfMonth = 10, months = 4)

    @Before
    fun setUp() {
        context.deleteDatabase(AppDatabase.NAME)
        context.getSharedPreferences(RecurringSuggestions.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        db = AppDatabase.build(context)
        val transactions = TransactionRepositoryImpl(db, SmsReader(context), SyncState(context))
        suggestions = RecurringSuggestions(context, transactions, RecurringRepository(db))
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(AppDatabase.NAME)
    }

    private fun dismissedKeys() =
        context.getSharedPreferences(RecurringSuggestions.PREFS, Context.MODE_PRIVATE)
            .getStringSet(RecurringSuggestions.KEY_DISMISSED, emptySet()).orEmpty()

    @Test
    fun undoYesDeletesTheReminderItCreated() = runBlocking {
        val id = suggestions.accept(suggestion)
        assertEquals(listOf("شارژ ساختمان"), db.recurringDao().all().map { it.title })
        suggestions.undoAccept(id)
        assertTrue(db.recurringDao().all().isEmpty())
    }

    @Test
    fun undoNoAsksAboutThatPayeeAgain() {
        suggestions.dismiss(suggestion)
        assertEquals(setOf("شارژ"), dismissedKeys())
        suggestions.undoDismiss(suggestion)
        assertTrue(dismissedKeys().isEmpty())
    }
}
