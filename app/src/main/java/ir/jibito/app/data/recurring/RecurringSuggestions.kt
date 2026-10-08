package ir.jibito.app.data.recurring

import android.content.Context
import androidx.core.content.edit
import ir.jibito.app.data.repository.RecurringRepository
import ir.jibito.app.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/**
 * پیشنهاد «این پرداخت ماهانه است، یادآوری‌اش را بسازم؟» (RecurringDetector) +
 * فهرست پیشنهادهایی که کاربر گفته «نه» (در پشتیبان‌گیری هم ذخیره می‌شود: BackupManager.BACKED_UP_PREFS).
 */
class RecurringSuggestions(
    context: Context,
    private val transactions: TransactionRepository,
    private val recurring: RecurringRepository,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dismissed = MutableStateFlow(prefs.getStringSet(KEY_DISMISSED, emptySet()).orEmpty().toSet())

    fun observe(): Flow<List<RecurringSuggestion>> =
        combine(transactions.observeTransactions(), recurring.observeAll(), dismissed) { all, existing, no ->
            RecurringDetector.detect(all, existing.map { it.title }, no)
        }.flowOn(Dispatchers.Default)

    /** «بله، یادم بنداز» */
    suspend fun accept(s: RecurringSuggestion) {
        recurring.add(s.title, s.amountRial, s.dayOfMonth)
    }

    /** «نه»: این طرف حساب دیگر پیشنهاد نمی‌شود */
    fun dismiss(s: RecurringSuggestion) {
        val next = dismissed.value + s.key
        prefs.edit { putStringSet(KEY_DISMISSED, next) }
        dismissed.value = next
    }

    companion object {
        /** در پشتیبان‌گیری هم ذخیره می‌شود (BackupManager.BACKED_UP_PREFS) */
        const val PREFS = "recurring_suggestions"
        const val KEY_DISMISSED = "dismissed"
    }
}
