package ir.jibito.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * وضعیت دسترسی‌ها در تنظیمات، و درست کردنشان با یک لمس:
 * پنجره‌ی اجازه‌ی اندروید همان‌جا باز می‌شود؛ اگر کاربر قبلاً «دیگر نپرس» زده (اندروید دیگر پنجره نشان نمی‌دهد)،
 * صفحه‌ی تنظیمات اپ در گوشی. بعد از برگشتن از تنظیمات گوشی دوباره بررسی می‌شود.
 */
internal class SettingsPermissions(
    val smsOk: Boolean,
    val notifyOk: Boolean,
    val fixSms: () -> Unit,
    val fixNotify: () -> Unit,
)

private val SMS_PERMISSIONS = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

/** @param onSmsGranted خواندن پیامک تازه روشن شد (مثلاً برای خواندن پیامک‌ها همان لحظه) */
@Composable
internal fun rememberSettingsPermissions(onSmsGranted: () -> Unit): SettingsPermissions {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnSmsGranted by rememberUpdatedState(onSmsGranted)
    var smsOk by remember { mutableStateOf(smsGranted(context)) }
    var notifyOk by remember { mutableStateOf(notificationsOn(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        smsOk = smsGranted(context)
        notifyOk = notificationsOn(context)
    }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val before = smsOk
        smsOk = smsGranted(context)
        if (smsOk && !before) currentOnSmsGranted()
        val missing = SMS_PERMISSIONS.filter { p -> !granted(context, p) }
        // رد شد و اندروید دیگر پنجره نشان نمی‌دهد ← مستقیم تنظیمات اپ
        if (missing.isNotEmpty() && activity != null &&
            missing.none { p -> ActivityCompat.shouldShowRequestPermissionRationale(activity, p) }
        ) {
            openAppDetails(context)
        }
    }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        notifyOk = notificationsOn(context)
        if (!ok && activity != null && Build.VERSION.SDK_INT >= 33 &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            openNotificationSettings(context)
        }
    }

    return SettingsPermissions(
        smsOk = smsOk,
        notifyOk = notifyOk,
        fixSms = { smsLauncher.launch(SMS_PERMISSIONS) },
        fixNotify = {
            // اجازه داده شده ولی نوتیف‌های اپ در تنظیمات گوشی خاموش است ← همان صفحه
            if (Build.VERSION.SDK_INT >= 33 && !granted(context, Manifest.permission.POST_NOTIFICATIONS)) {
                notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings(context)
            }
        },
    )
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun smsGranted(context: Context) = SMS_PERMISSIONS.all { granted(context, it) }

private fun notificationsOn(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun openAppDetails(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { openAppDetails(context) }
}
