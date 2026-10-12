package ir.jibito.app.ui.smslist

import android.content.Context
import androidx.core.content.edit
import ir.jibito.app.domain.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * عددِ کنار «بی‌دسته» در «تراکنش‌ها». «فعلاً نه» روی کارت بالای فهرست همان کلید تنظیمات را خاموش می‌کند
 * (و روشن کردن دوباره‌اش عدد همه را برمی‌گرداند). باز کردن فهرست از «کارها» خرج‌های فعلی را «دیده‌شده» می‌کند
 * (مثل «خوانده‌شد» در پیامک) تا فقط خرج‌های تازه‌تر عدد بسازند. خود تراکنش‌ها و فیلتر «بی‌دسته» دست نمی‌خورند.
 */
class UncategorizedBadgeSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))

    /** نشان دادن عدد (تنظیمات) */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _seenUntil = MutableStateFlow(prefs.getLong(KEY_SEEN_UNTIL, 0L))

    /** خرج‌های تا این زمان «دیده‌شده» حساب می‌شوند (۰ = هنوز بسته نشده) */
    val seenUntil: StateFlow<Long> = _seenUntil.asStateFlow()

    /** روشن کردن دوباره یعنی «همه را دوباره نشانم بده»: «فعلاً نه»ی قبلی فراموش می‌شود تا عدد برگردد */
    fun setEnabled(on: Boolean) {
        val turningOn = on && !_enabled.value
        prefs.edit {
            putBoolean(KEY_ENABLED, on)
            if (turningOn) remove(KEY_SEEN_UNTIL)
        }
        _enabled.value = on
        if (turningOn) _seenUntil.value = 0L
    }

    /**
     * «فعلاً نه» روی کارت بالای تراکنش‌ها: همان کلید تنظیمات خاموش می‌شود.
     * @return «برگردون»: حالت قبل، بی‌آنکه «دیده‌شده»های قبلی پاک شوند
     */
    fun turnOff(): () -> Unit {
        val before = _seenUntil.value
        setEnabled(false)
        return {
            prefs.edit {
                putBoolean(KEY_ENABLED, true)
                putLong(KEY_SEEN_UNTIL, before)
            }
            _seenUntil.value = before
            _enabled.value = true
        }
    }

    /** باز کردن فهرست بی‌دسته‌ها: همه‌ی خرج‌های بی‌دسته‌ی تا الان دیده‌شده */
    fun markSeen(all: List<Transaction>) {
        val newest = all.filter(::isUncategorizedSpend).maxOfOrNull { it.dateMillis } ?: return
        prefs.edit { putLong(KEY_SEEN_UNTIL, newest) }
        _seenUntil.value = newest
    }

    companion object {
        const val PREFS = "uncat_badge"
        const val KEY_ENABLED = "enabled"
        private const val KEY_SEEN_UNTIL = "seen_until"
    }
}

/** تعداد خرج‌های بی‌دسته‌ای که باید عددش دیده شود: صفر اگر خاموش است، و فقط تازه‌تر از [seenUntil] */
fun uncategorizedBadgeCount(all: List<Transaction>, enabled: Boolean, seenUntil: Long): Int =
    if (enabled) all.count { isUncategorizedSpend(it) && it.dateMillis > seenUntil } else 0
