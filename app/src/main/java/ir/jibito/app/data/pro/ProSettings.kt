package ir.jibito.app.data.pro

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * وضعیت «جیبیتوی کامل» روی همین گوشی (SharedPreferences):
 * - شروع ماه رایگان: اولین باری که این نسخه باز می‌شود. در پشتیبان می‌رود، تا بازگردانی روی گوشی تازه ماه رایگان را از نو شروع نکند.
 * - «خریده»: فقط بعد از تأیید امضای کافه‌بازار (BazaarStore). در پشتیبان نمی‌رود؛ روی گوشی تازه، خود بازار می‌گوید خریده یا نه.
 * - آخرین پرسیدن از بازار، این‌که «ماه رایگانت تموم شد» یک بار دیده شده و آخرین نوتیف دعوت به خرید، مال همین گوشی است.
 */
class ProSettings(context: Context, now: Long = System.currentTimeMillis()) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_TRIAL_START)) prefs.edit { putLong(KEY_TRIAL_START, now) }
    }

    private val _status = MutableStateFlow(compute(now))

    /** با گذشت روزها خودش عوض نمی‌شود: [refresh] (مثلاً هر بار که اپ دیده می‌شود) */
    val status: StateFlow<ProStatus> = _status.asStateFlow()

    /** روزهای ماه رایگان را دوباره می‌شمارد */
    fun refresh(now: Long = System.currentTimeMillis()) {
        _status.value = compute(now)
    }

    /** خرید تأییدشده (از صفحه‌ی خرید، یا پرسیدن خرید قبلی از بازار) */
    fun markOwned(now: Long = System.currentTimeMillis()) {
        prefs.edit { putBoolean(KEY_OWNED, true) }
        refresh(now)
    }

    /** وقت پرسیدن خرید قبلی از بازار است؟ (حداکثر روزی یک بار، مگر کاربر خودش «قبلاً خریدم» را بزند) */
    fun checkDue(now: Long = System.currentTimeMillis()): Boolean =
        _status.value != ProStatus.Owned && now - prefs.getLong(KEY_LAST_CHECK, 0L) >= ProRules.CHECK_INTERVAL_MILLIS

    fun markChecked(now: Long = System.currentTimeMillis()) = prefs.edit { putLong(KEY_LAST_CHECK, now) }

    /** «ماه رایگانت تموم شد» هنوز نشان داده نشده (فقط یک بار، بعد از تمام شدن ماه اول) */
    val trialEndNoticeDue: Boolean
        get() = _status.value == ProStatus.Free && !prefs.getBoolean(KEY_END_NOTICE_SEEN, false)

    fun markTrialEndNoticeSeen() = prefs.edit { putBoolean(KEY_END_NOTICE_SEEN, true) }

    /** وقت نوتیف «خرجت ثبت شد؛ جیبیتوی کامل…» است؟ (فقط وقتی بسته است؛ [ProRules.notifyInviteDue]) */
    fun notifyInviteDue(now: Long = System.currentTimeMillis()): Boolean =
        _status.value == ProStatus.Free &&
            ProRules.notifyInviteDue(prefs.getLong(KEY_LAST_INVITE, 0L), prefs.getBoolean(KEY_END_NOTICE_SEEN, false), now)

    fun markNotifyInvited(now: Long = System.currentTimeMillis()) = prefs.edit { putLong(KEY_LAST_INVITE, now) }

    private fun compute(now: Long): ProStatus =
        ProRules.status(prefs.getBoolean(KEY_OWNED, false), prefs.getLong(KEY_TRIAL_START, now), now)

    companion object {
        const val PREFS = "pro"
        const val KEY_TRIAL_START = "trial_started_at"
        private const val KEY_OWNED = "owned"
        private const val KEY_LAST_CHECK = "last_store_check"
        private const val KEY_END_NOTICE_SEEN = "trial_end_notice_seen"
        private const val KEY_LAST_INVITE = "last_notify_invite"
    }
}
