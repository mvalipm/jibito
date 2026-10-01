package ir.jibito.app

import android.Manifest
import android.content.pm.PackageManager
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
    fun hasSmsPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    var screen by rememberSaveable { mutableStateOf(Screen.Welcome) }
    var wasDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            screen = Screen.SmsList
        } else {
            wasDenied = true
        }
    }

    // دکمه‌ی «برگشت» گوشی: از هر صفحه به صفحه‌ی خوش‌آمد
    BackHandler(enabled = screen != Screen.Welcome) { screen = Screen.Welcome }

    when (screen) {
        Screen.Welcome -> WelcomeScreen(
            onStart = {
                screen = if (hasSmsPermission()) Screen.SmsList else Screen.Permission
            }
        )
        Screen.Permission -> SmsPermissionScreen(
            wasDenied = wasDenied,
            onAllowClick = {
                if (hasSmsPermission()) {
                    screen = Screen.SmsList
                } else {
                    permissionLauncher.launch(Manifest.permission.READ_SMS)
                }
            },
        )
        Screen.SmsList -> SmsListScreen()
    }
}
