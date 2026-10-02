package ir.jibito.app.data.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * قفل اپ: روشن یا خاموش (در SharedPreferences همین گوشی؛ در پشتیبان و انتقال به گوشی دیگر نمی‌رود).
 * باز کردن قفل با همان قفل خود گوشی است (اثر انگشت، چهره، رمز یا الگو)؛ اپ رمز جداگانه نگه نمی‌دارد.
 */
class AppLockSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    companion object {
        const val PREFS_NAME = "security"
        private const val KEY_ENABLED = "app_lock_enabled"

        /** اگر کاربر کمتر از این مدت از اپ بیرون رفته باشد، دوباره قفل نمی‌شود */
        const val GRACE_MILLIS = 30_000L
    }
}

/**
 * وضعیت قفل در همین اجرای اپ (در حافظه). با بسته شدن کامل اپ از بین می‌رود ← دفعه‌ی بعد دوباره قفل است.
 */
object AppLockSession {
    @Volatile var unlocked: Boolean = false
    @Volatile var backgroundedAt: Long = 0L
}
