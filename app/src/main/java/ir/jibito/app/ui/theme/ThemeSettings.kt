package ir.jibito.app.ui.theme

import android.content.Context
import androidx.core.content.edit
import ir.jibito.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** پوسته‌های اپ. رنگ دسته‌ها در نمودار در همه یکی است (پالت اعتبارسنجی‌شده). */
enum class AppThemeStyle(val label: Int, val hint: Int) {
    /** مرجانی (پیش‌فرض) */
    DEFAULT(R.string.theme_default, R.string.theme_default_hint),
    /** گرم: کرم، صورتی‌گلی، هلویی، با آبی خاکستری برای تعادل */
    WARM(R.string.theme_warm, R.string.theme_warm_hint),
    /** سرد: سرمه‌ای، آبی، یاسی */
    COOL(R.string.theme_cool, R.string.theme_cool_hint),
    /** ماچا لاته: سبز مریمی و کرم شیری */
    MATCHA(R.string.theme_matcha, R.string.theme_matcha_hint),
    /** لوندر دیجیتال: بنفش اسطوخودوسی با کمی صورتی */
    LAVENDER(R.string.theme_lavender, R.string.theme_lavender_hint),
    /** گیلاسی: قرمز گیلاسی روی صورتی خیلی کم‌رنگ */
    CHERRY(R.string.theme_cherry, R.string.theme_cherry_hint),
    /** غروب آینده: نیلی و هلویی غروب */
    DUSK(R.string.theme_dusk, R.string.theme_dusk_hint),
}

/** روشن یا تیره: مثل گوشی، یا همیشه یکی (دکمه‌ی ماه/خورشید بالای «خلاصه») */
enum class DarkMode(val label: Int) {
    SYSTEM(R.string.dark_mode_system),
    LIGHT(R.string.dark_mode_light),
    DARK(R.string.dark_mode_dark),
}

/**
 * ظاهر انتخاب‌شده (در SharedPreferences)؛ با عوض شدنش، کل اپ فوراً عوض می‌شود:
 * پوسته، روشن/تیره، و «پنهان کردن مبلغ‌ها» (دکمه‌ی چشم، برای وقتی کسی کنارت نشسته).
 */
class ThemeSettings(context: Context) {

    private val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
    private val _style = MutableStateFlow(read())
    val style: StateFlow<AppThemeStyle> = _style.asStateFlow()
    private val _darkMode = MutableStateFlow(readDarkMode())
    val darkMode: StateFlow<DarkMode> = _darkMode.asStateFlow()
    private val _hideAmounts = MutableStateFlow(prefs.getBoolean(KEY_HIDE, false))
    val hideAmounts: StateFlow<Boolean> = _hideAmounts.asStateFlow()

    fun set(style: AppThemeStyle) {
        prefs.edit { putString(KEY, style.name) }
        _style.value = style
    }

    fun setDarkMode(mode: DarkMode) {
        prefs.edit { putString(KEY_DARK, mode.name) }
        _darkMode.value = mode
    }

    fun setHideAmounts(hide: Boolean) {
        prefs.edit { putBoolean(KEY_HIDE, hide) }
        _hideAmounts.value = hide
    }

    private fun read(): AppThemeStyle =
        runCatching { AppThemeStyle.valueOf(prefs.getString(KEY, null) ?: "") }.getOrDefault(AppThemeStyle.DEFAULT)

    private fun readDarkMode(): DarkMode =
        runCatching { DarkMode.valueOf(prefs.getString(KEY_DARK, null) ?: "") }.getOrDefault(DarkMode.SYSTEM)

    private companion object {
        const val KEY = "theme_style"
        const val KEY_DARK = "dark_mode"
        const val KEY_HIDE = "hide_amounts"
    }
}
