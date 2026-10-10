package ir.jibito.app.ui.smslist

import android.content.Context
import androidx.core.content.edit
import ir.jibito.app.domain.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * عددِ کنار «بی‌دسته» در «تراکنش‌ها». کاربر می‌تواند با «فعلاً نه» آن را ببندد (مثل «خوانده‌شد» در پیامک):
 * خرج‌های قدیمیِ بی‌دسته دیگر عدد نمی‌سازند و فقط خرج‌های تازه‌تر از آن لحظه حساب می‌شوند.
 * در تنظیمات هم می‌شود عدد را کلاً خاموش کرد. خود تراکنش‌ها و فیلتر «بی‌دسته» دست نمی‌خورند.
 */
class UncategorizedBadgeSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))

    /** نشان دادن عدد (تنظیمات) */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _seenUntil = MutableStateFlow(prefs.getLong(KEY_SEEN_UNTIL, 0L))

    /** خرج‌های تا این زمان «دیده‌شده» حساب می‌شوند (۰ = هنوز بسته نشده) */
    val seenUntil: StateFlow<Long> = _seenUntil.asStateFlow()

    fun setEnabled(on: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, on) }
        _enabled.value = on
    }

    /** «فعلاً نه»: همه‌ی خرج‌های بی‌دسته‌ی تا الان دیده‌شده */
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
