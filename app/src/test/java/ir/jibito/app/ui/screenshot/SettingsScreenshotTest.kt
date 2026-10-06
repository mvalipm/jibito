package ir.jibito.app.ui.screenshot

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.jibito.app.ui.theme.AppThemeStyle
import org.junit.Test
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import ir.jibito.app.ui.settings.PermissionBanner
import ir.jibito.app.ui.settings.SettingsSection
import ir.jibito.app.ui.settings.ThemePicker
import ir.jibito.app.ui.theme.JibitoIcons
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** اسکرین‌شات‌های تنظیمات (پایه‌ی مشترک: ScreenshotTestBase) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class SettingsScreenshotTest : ScreenshotTestBase() {

    @Test
    fun settingsParts() {
        for ((style, dark) in variants) {
            shot("settings", style, dark) {
                PermissionBanner(smsOk = true, notifyOk = false, onFix = {})
                Spacer(Modifier.height(24.dp))
                SettingsSection("پوسته", JibitoIcons.Palette) { ThemePicker(style, onSelect = {}) }
            }
        }
        shot("settings", AppThemeStyle.DEFAULT, false, fontScale = 2f) {
            SettingsSection("پوسته", JibitoIcons.Palette) { ThemePicker(AppThemeStyle.DEFAULT, onSelect = {}) }
        }
    }

    /** صفحه‌ی اصلی تنظیمات: گروه با ردیف‌های زیرصفحه، کلید و وضعیت دسترسی */
    @Test
    fun settingsHub() {
        for ((style, dark) in variants) {
            shot("settings_hub", style, dark) { SettingsHubSample() }
        }
        shot("settings_hub", AppThemeStyle.DEFAULT, false, fontScale = 2f) { SettingsHubSample() }
    }
}
