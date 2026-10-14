package ir.jibito.app.ui.lock

import android.view.Window
import android.view.WindowManager

/**
 * وقتی قفل اپ روشن است، پنجره «امن» می‌شود: اسکرین‌شات و ضبط صفحه نمی‌شود و در «برنامه‌های اخیر» هم تصویر اپ دیده نمی‌شود.
 * (setRecentsScreenshotEnabled فقط از اندروید ۱۳ هست؛ FLAG_SECURE روی همه‌ی نسخه‌ها کار می‌کند.)
 */
object ScreenPrivacy {
    fun apply(window: Window, hidden: Boolean) {
        if (hidden) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
