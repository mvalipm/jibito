package ir.jibito.app.ui.summary

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.compose.LocalActivity

/**
 * نوتیفیکیشن خاموش است؟ (یعنی «این خرج مال چی بود؟» و هشدار بودجه و یادآوری‌ها نمی‌آیند)
 * [visible]: باید پیشنهاد روشن کردن نشان داده شود. [fix]: با یک لمس درستش می‌کند:
 * پنجره‌ی اجازه‌ی اندروید، یا اگر کاربر قبلاً «دیگر نپرس» زده، صفحه‌ی تنظیمات نوتیفیکیشن اپ.
 * اگر کاربر بعد از لمس باز هم روشن نکرد، تا ۳۰ روز دیگر نشان داده نمی‌شود (یعنی خودش نخواسته).
 */
class NotificationPromptState(val visible: Boolean, val fix: () -> Unit)

@Composable
fun rememberNotificationPrompt(): NotificationPromptState {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var snoozed by remember { mutableStateOf(isSnoozed(context)) }
    // بعد از برگشتن از تنظیمات گوشی دوباره بررسی شود
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        snoozed = isSnoozed(context)
    }

    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        // رد شد و اندروید دیگر پنجره نشان نمی‌دهد ← مستقیم تنظیمات
        if (!granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            openNotificationSettings(context)
        }
    }

    return NotificationPromptState(visible = !enabled && !snoozed) {
        snooze(context)
        snoozed = true
        val permissionMissing = Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (permissionMissing) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(context)
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private const val PREFS = "ui_prompts"
private const val KEY_SNOOZED_AT = "notification_prompt_at"
private const val SNOOZE_MILLIS = 30L * 24 * 60 * 60 * 1000

private fun isSnoozed(context: Context): Boolean {
    val at = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_SNOOZED_AT, 0L)
    return at > 0 && System.currentTimeMillis() - at in 0 until SNOOZE_MILLIS
}

private fun snooze(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putLong(KEY_SNOOZED_AT, System.currentTimeMillis()) }
}
