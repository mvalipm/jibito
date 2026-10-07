package ir.jibito.app.ui.welcome

import android.content.Context
import androidx.core.content.edit
import ir.jibito.app.di.AppContainer
import ir.jibito.app.domain.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** فقط یک بار، بعد از اولین خواندن پیامک‌ها روی این گوشی */
class FirstRunFlag(context: Context) {
    private val prefs = context.getSharedPreferences("first_run", Context.MODE_PRIVATE)

    val done: Boolean get() = prefs.getBoolean(KEY_DONE, false)

    fun markDone() {
        prefs.edit { putBoolean(KEY_DONE, true) }
    }

    /** «جیبت رو شناختم» که باید روی اپ نشان داده شود؛ null یعنی هیچ. MainScreen نشانش می‌دهد و بعد از «بزن بریم» پاکش می‌کند */
    val pending = MutableStateFlow<RevealStats?>(null)

    private companion object {
        const val KEY_DONE = "reveal_done"
    }
}

/**
 * پیامک‌ها را می‌خواند و اگر اولین بار است (و پیش از آن هیچ تراکنشی نبود)، «جیبت رو شناختم» را در [FirstRunFlag.pending] می‌گذارد.
 * هم موقع باز شدن اپ و هم وقتی اجازه‌ی پیامک بعداً داده می‌شود (صفحه‌ی خالی یا تنظیمات)؛
 * پس کسی که اول «فعلاً دستی» زده هم بعد از دادن اجازه همین صفحه را می‌بیند.
 * @return چند تراکنش تازه پیدا شد
 */
suspend fun syncWithReveal(container: AppContainer): Int {
    val repository = container.transactionRepository
    return syncWithReveal(container.firstRun, repository.observeTransactions()) { repository.syncFromSms() }
}

/**
 * همان [syncWithReveal]، جدا از اپ (برای تست).
 * اگر خواندن خطا بدهد، «بار اول» ثبت نمی‌شود تا دفعه‌ی بعد دوباره امتحان شود.
 */
internal suspend fun syncWithReveal(flag: FirstRunFlag, transactions: Flow<List<Transaction>>, sync: suspend () -> Int): Int {
    val firstRun = !flag.done
    // کاربری که از نسخه‌ی قبل به‌روز کرده یا دستی ثبت کرده، داده دارد و این صفحه را نمی‌بیند
    val hadData = !firstRun || transactions.first().isNotEmpty()
    val found = sync()
    if (firstRun) {
        flag.markDone()
        if (!hadData && found > 0) {
            // فهرست مشترک تراکنش‌ها کمی بعد از ذخیره به‌روز می‌شود
            val all = withTimeoutOrNull(3_000) { transactions.first { it.size >= found } }
            flag.pending.value = all?.let { RevealStats.of(it) } ?: RevealStats(count = found, months = 0, banks = 0)
        }
    }
    return found
}
