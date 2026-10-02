package ir.jibito.app.ui.theme

import android.content.Context
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
}

/** پوسته‌ی انتخاب‌شده (در SharedPreferences)؛ با عوض شدنش، کل اپ فوراً رنگ عوض می‌کند. */
class ThemeSettings(context: Context) {

    private val prefs = context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
    private val _style = MutableStateFlow(read())
    val style: StateFlow<AppThemeStyle> = _style.asStateFlow()

    fun set(style: AppThemeStyle) {
        prefs.edit().putString(KEY, style.name).apply()
        _style.value = style
    }

    private fun read(): AppThemeStyle =
        runCatching { AppThemeStyle.valueOf(prefs.getString(KEY, null) ?: "") }.getOrDefault(AppThemeStyle.DEFAULT)

    private companion object {
        const val KEY = "theme_style"
    }
}
