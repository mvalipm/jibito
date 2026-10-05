package ir.jibito.app.notify

import android.content.Context
import androidx.annotation.StringRes
import ir.jibito.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * شکل نوتیفیکیشن «این خرج مال چی بود؟» (و «این پول از کجا اومد؟»):
 * - BUTTONS: ۳ دکمه‌ی دسته‌ی پیشنهادی
 * - MIXED: ۲ دکمه‌ی دسته + «بنویس» (پیش‌فرض)
 * - REPLY: فقط «بنویس»
 *
 * «بنویس» جعبه‌ی نوشتن را همان‌جا در نوتیفیکیشن باز می‌کند؛ نوشته اگر با دسته‌ای جور شد دسته می‌شود
 * و همیشه یادداشت تراکنش هم می‌شود. فقط روی نوتیفیکیشن‌های بعدی اثر دارد.
 */
enum class NotificationStyle(
    val code: String,
    @StringRes val label: Int,
    @StringRes val hint: Int,
    /** چند دکمه‌ی دسته (اندروید حداکثر ۳ دکمه نشان می‌دهد) */
    val categoryButtons: Int,
    val hasReply: Boolean,
) {
    BUTTONS("buttons", R.string.notif_style_buttons, R.string.notif_style_buttons_hint, 3, false),
    MIXED("mixed", R.string.notif_style_mixed, R.string.notif_style_mixed_hint, 2, true),
    REPLY("reply", R.string.notif_style_reply, R.string.notif_style_reply_hint, 0, true),
    ;

    companion object {
        val DEFAULT = MIXED
        fun fromCode(code: String?): NotificationStyle = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}

class NotificationStyleSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _style = MutableStateFlow(NotificationStyle.fromCode(prefs.getString(KEY_STYLE, null)))
    val style: StateFlow<NotificationStyle> = _style.asStateFlow()

    fun set(style: NotificationStyle) {
        prefs.edit().putString(KEY_STYLE, style.code).apply()
        _style.value = style
    }

    companion object {
        /** در پشتیبان‌گیری هم ذخیره می‌شود (BackupManager.BACKED_UP_PREFS) */
        const val PREFS = "notification_prefs"
        private const val KEY_STYLE = "tx_style"
    }
}
