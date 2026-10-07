package ir.jibito.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import ir.jibito.app.data.security.AppLockSession
import ir.jibito.app.data.security.AppLockSettings
import ir.jibito.app.ui.lock.DeviceAuth
import ir.jibito.app.ui.lock.LockScreen
import ir.jibito.app.ui.permission.SmsPermissionScreen
import ir.jibito.app.ui.main.MainScreen
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.DarkMode
import ir.jibito.app.ui.common.LocalHideAmounts
import ir.jibito.app.ui.common.LocalStatusBarOnColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import ir.jibito.app.ui.welcome.WelcomeScreen

class MainActivity : ComponentActivity() {
    /**
     * زبان (و جهت راست‌به‌چپ) کل اپ از همان لحظه‌ی ساخته شدن: فارسی.
     * بدون این، پنجره‌های جدا (پنجره‌ی بودجه، برگه‌هایی که از پایین باز می‌شوند) در اولین لحظه
     * جهت زبان گوشی (مثلاً انگلیسی، چپ‌به‌راست) را می‌گرفتند و نوشته‌ها یک لحظه سمت چپ دیده می‌شدند.
     */
    override fun attachBaseContext(newBase: Context) {
        val persian = Locale.forLanguageTag("fa")
        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(persian)
            setLayoutDirection(persian)
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    /** قفل اپ: true یعنی تا تأیید قفل گوشی، فقط صفحه‌ی قفل دیده می‌شود */
    private var locked by mutableStateOf(false)

    /** تراکنشی که از نوتیفیکیشن خواسته شده و هنوز باز نشده */
    private var openTransactionId by mutableStateOf<Long?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readOpenRequest(intent)
    }

    private fun readOpenRequest(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_TRANSACTION_ID, -1L) ?: -1L
        if (id > 0) openTransactionId = id
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as JibitoApplication).container
        val themeSettings = container.themeSettings
        val lockSettings = container.appLockSettings
        refreshLock()
        // فقط بار اول؛ بعد از چرخاندن گوشی دوباره باز نشود
        if (savedInstanceState == null) readOpenRequest(intent)
        setContent {
            val style by themeSettings.style.collectAsState()
            val darkMode by themeSettings.darkMode.collectAsState()
            val hideAmounts by themeSettings.hideAmounts.collectAsState()
            val dark = when (darkMode) {
                DarkMode.SYSTEM -> isSystemInDarkTheme()
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
            }
            // آیکون‌های نوار وضعیت: تیره روی زمینه‌ی روشن، سفید در حالت تیره یا روی سرصفحه‌ی رنگی «خلاصه»
            val statusOnColor = remember { mutableStateOf(false) }
            LaunchedEffect(dark, statusOnColor.value) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark && !statusOnColor.value
                    isAppearanceLightNavigationBars = !dark
                }
            }
            val lockEnabled by lockSettings.enabled.collectAsState()
            LaunchedEffect(lockEnabled) {
                // وقتی قفل روشن است، تصویر اپ در «برنامه‌های اخیر» نشان داده نشود
                if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!lockEnabled)
            }
            JibitoTheme(style = style, darkTheme = dark) {
                // فعلاً کل اپ را راست‌به‌چپ می‌کنیم؛ سوییچ زبان را بعداً اضافه می‌کنیم
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalHideAmounts provides hideAmounts,
                    LocalStatusBarOnColor provides statusOnColor,
                ) {
                    // اپ زیر صفحه‌ی قفل زنده می‌ماند (تا مثلاً جواب انتخاب فایل پشتیبان گم نشود)، ولی دیده و خوانده نمی‌شود
                    Box(Modifier.fillMaxSize()) {
                        Box(if (locked) Modifier.clearAndSetSemantics { } else Modifier) {
                            JibitoApp(
                                openTransactionId = openTransactionId,
                                onOpenHandled = { openTransactionId = null },
                            )
                        }
                        if (locked) {
                            LockScreen(onUnlocked = {
                                AppLockSession.unlocked = true
                                // اگر تأیید (مثلاً صفحه‌ی رمز گوشی) طول کشید، برگشتن از آن دوباره قفل نکند
                                AppLockSession.backgroundedAt = 0
                                locked = false
                            })
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val session = AppLockSession
        if (session.backgroundedAt > 0 &&
            SystemClock.elapsedRealtime() - session.backgroundedAt > AppLockSettings.GRACE_MILLIS
        ) {
            session.unlocked = false
        }
        session.backgroundedAt = 0
        refreshLock()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AppLockSession.backgroundedAt = SystemClock.elapsedRealtime()
    }

    private fun refreshLock() {
        val settings = (application as JibitoApplication).container.appLockSettings
        if (settings.enabled.value && !DeviceAuth.isAvailable(this)) {
            // کاربر قفل صفحه‌ی گوشی را برداشته: بدون آن قفل اپ باز نمی‌شود، پس خاموشش می‌کنیم تا کاربر پشت در نماند
            settings.setEnabled(false)
        }
        locked = settings.enabled.value && !AppLockSession.unlocked
    }

    companion object {
        const val EXTRA_TRANSACTION_ID = "ir.jibito.app.extra.TRANSACTION_ID"

        /** باز کردن اپ روی یک تراکنش، با برگه‌ی انتخاب دسته (برای لمس نوتیفیکیشن) */
        fun openTransactionIntent(context: Context, transactionId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_TRANSACTION_ID, transactionId)
    }
}

private const val PREFS_ONBOARDING = "onboarding"
private const val KEY_MANUAL_ONLY = "manual_only"

/** صفحه‌های اپ. (بعداً با Navigation-Compose جایگزین می‌شود.) */
private enum class Screen { Welcome, Permission, SmsList }

@Composable
private fun JibitoApp(openTransactionId: Long?, onOpenHandled: () -> Unit) {
    val context = LocalContext.current
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** خواندن پیامک‌های قبلی + باخبر شدن از پیامک تازه — هر دو لازم‌اند. */
    fun hasSmsPermissions() = granted(Manifest.permission.READ_SMS) && granted(Manifest.permission.RECEIVE_SMS)

    val smsPermissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    // «فعلاً دستی ثبت می‌کنم»: کاربر بدون اجازه‌ی پیامک وارد اپ شده؛ دفعه‌های بعد هم مستقیم وارد می‌شود
    val onboardingPrefs = remember { context.getSharedPreferences(PREFS_ONBOARDING, Context.MODE_PRIVATE) }
    fun manualOnly() = onboardingPrefs.getBoolean(KEY_MANUAL_ONLY, false)

    // اگر قبلاً اجازه‌ها داده شده، مستقیم فهرست تراکنش‌ها (مثلاً وقتی از نوتیفیکیشن باز می‌شود)
    var screen by rememberSaveable {
        mutableStateOf(if (hasSmsPermissions() || manualOnly()) Screen.SmsList else Screen.Welcome)
    }
    var wasDenied by rememberSaveable { mutableStateOf(false) }

    // جواب پنجره‌ی نوتیفیکیشن هر چه باشد، وارد اپ می‌شویم (اختیاری است)
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        screen = Screen.SmsList
    }

    fun enterApp() {
        // نوتیفیکیشن فقط از اندروید ۱۳ اجازه‌ی جدا دارد و اختیاری است؛ بعد از پیامک و جدا از آن پرسیده می‌شود
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            screen = Screen.SmsList
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasSmsPermissions()) {
            onboardingPrefs.edit { putBoolean(KEY_MANUAL_ONLY, false) }
            enterApp()
        } else {
            wasDenied = true
        }
    }

    // دکمه‌ی «برگشت» گوشی: از صفحه‌ی اجازه به خوش‌آمد؛ از فهرست تراکنش‌ها، خروج از اپ (رفتار عادی اندروید)
    BackHandler(enabled = screen == Screen.Permission) { screen = Screen.Welcome }

    when (screen) {
        Screen.Welcome -> WelcomeScreen(
            onStart = {
                screen = if (hasSmsPermissions()) Screen.SmsList else Screen.Permission
            }
        )
        Screen.Permission -> SmsPermissionScreen(
            wasDenied = wasDenied,
            onAllowClick = {
                if (hasSmsPermissions()) {
                    enterApp()
                } else {
                    permissionLauncher.launch(smsPermissions)
                }
            },
            onManualClick = {
                onboardingPrefs.edit { putBoolean(KEY_MANUAL_ONLY, true) }
                screen = Screen.SmsList
            },
        )
        Screen.SmsList -> MainScreen(openTransactionId = openTransactionId, onOpenHandled = onOpenHandled)
    }
}
