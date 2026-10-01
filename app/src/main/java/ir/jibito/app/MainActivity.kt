package ir.jibito.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import ir.jibito.app.ui.permission.SmsPermissionScreen
import ir.jibito.app.ui.smslist.SmsListScreen
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.welcome.WelcomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JibitoTheme {
                // فعلاً کل اپ را راست‌به‌چپ می‌کنیم؛ سوییچ زبان را بعداً اضافه می‌کنیم
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    JibitoApp()
                }
            }
        }
    }
}

/** صفحه‌های اپ. (بعداً با Navigation-Compose جایگزین می‌شود.) */
private enum class Screen { Welcome, Permission, SmsList }

@Composable
private fun JibitoApp() {
    val context = LocalContext.current
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** خواندن پیامک‌های قبلی + باخبر شدن از پیامک تازه — هر دو لازم‌اند. */
    fun hasSmsPermissions() = granted(Manifest.permission.READ_SMS) && granted(Manifest.permission.RECEIVE_SMS)

    /** همه‌ی اجازه‌هایی که می‌خواهیم؛ نوتیفیکیشن فقط از اندروید ۱۳ اجازه‌ی جدا دارد و اختیاری است. */
    val wantedPermissions = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun hasAllWanted() = wantedPermissions.all { granted(it) }

    // اگر قبلاً اجازه‌ها داده شده، مستقیم فهرست تراکنش‌ها (مثلاً وقتی از نوتیفیکیشن باز می‌شود)
    var screen by rememberSaveable { mutableStateOf(if (hasSmsPermissions()) Screen.SmsList else Screen.Welcome) }
    var wasDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // اگر فقط نوتیفیکیشن رد شد، باز هم ادامه می‌دهیم؛ پیامک‌ها ضروری‌اند
        if (hasSmsPermissions()) {
            screen = Screen.SmsList
        } else {
            wasDenied = true
        }
    }

    // دکمه‌ی «برگشت» گوشی: از صفحه‌ی اجازه به خوش‌آمد؛ از فهرست تراکنش‌ها، خروج از اپ (رفتار عادی اندروید)
    BackHandler(enabled = screen == Screen.Permission) { screen = Screen.Welcome }

    when (screen) {
        Screen.Welcome -> WelcomeScreen(
            onStart = {
                screen = if (hasAllWanted()) Screen.SmsList else Screen.Permission
            }
        )
        Screen.Permission -> SmsPermissionScreen(
            wasDenied = wasDenied,
            onAllowClick = {
                if (hasAllWanted()) {
                    screen = Screen.SmsList
                } else {
                    permissionLauncher.launch(wantedPermissions)
                }
            },
        )
        Screen.SmsList -> SmsListScreen()
    }
}
