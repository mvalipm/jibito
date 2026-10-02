package ir.jibito.app.ui.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * تأیید هویت با قفل خود گوشی (اثر انگشت / چهره / رمز / الگو)، بدون کتابخانه‌ی اضافه:
 * - اندروید ۱۰ به بعد: BiometricPrompt خود اندروید (با اجازه‌ی رمز گوشی به‌جای اثر انگشت)
 * - اندروید ۸ و ۹: صفحه‌ی تأیید قفل گوشی (KeyguardManager)
 */
object DeviceAuth {
    /** قفل صفحه‌ای روی گوشی تنظیم شده؟ بدون آن، قفل اپ معنی ندارد. */
    fun isAvailable(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
}

/**
 * یک تابع برمی‌گرداند که با صدا زدنش، پنجره‌ی تأیید هویت باز می‌شود.
 * [onResult] با true (موفق) یا false (لغو / خطا) صدا زده می‌شود.
 */
@Composable
fun rememberDeviceAuthenticator(title: String, subtitle: String, onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val keyguardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        currentOnResult(it.resultCode == Activity.RESULT_OK)
    }
    return remember(context, title, subtitle) {
        {
            if (!DeviceAuth.isAvailable(context)) {
                currentOnResult(false)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                showBiometricPrompt(context, title, subtitle) { currentOnResult(it) }
            } else {
                @Suppress("DEPRECATION")
                val intent = context.getSystemService(KeyguardManager::class.java)
                    ?.createConfirmDeviceCredentialIntent(title, subtitle)
                if (intent != null) keyguardLauncher.launch(intent) else currentOnResult(false)
            }
        }
    }
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
private fun showBiometricPrompt(context: Context, title: String, subtitle: String, onResult: (Boolean) -> Unit) {
    val builder = BiometricPrompt.Builder(context).setTitle(title).setSubtitle(subtitle)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
    } else {
        @Suppress("DEPRECATION")
        builder.setDeviceCredentialAllowed(true)
    }
    builder.build().authenticate(
        CancellationSignal(),
        ContextCompat.getMainExecutor(context),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onResult(true)

            // لغو، تلاش‌های زیاد، ...؛ (onAuthenticationFailed فقط یک تلاش ناموفق است و پنجره باز می‌ماند)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onResult(false)
        },
    )
}
