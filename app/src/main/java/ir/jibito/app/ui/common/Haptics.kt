package ir.jibito.app.ui.common

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * لرزش‌های کوتاه و ظریف برای لحظه‌های مهم (ثبت دسته، رد کردن، برگرداندن).
 * از تنظیم «لرزش لمسی» خود گوشی پیروی می‌کند؛ اگر کاربر خاموشش کرده باشد، هیچ لرزشی نیست.
 */
@Stable
class Haptics internal constructor(private val view: View) {
    /** کاری با موفقیت انجام شد */
    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    )

    /** رد کردن یا کنار گذاشتن */
    fun reject() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.VIRTUAL_KEY
    )

    /** تیک سبک (مثلاً برگرداندن یا رد شدن از یک مرحله) */
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
