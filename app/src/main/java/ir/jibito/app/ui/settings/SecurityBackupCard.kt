package ir.jibito.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.backup.BackupCrypto
import ir.jibito.app.data.backup.BackupManager
import ir.jibito.app.data.security.AppLockSession
import ir.jibito.app.ui.lock.DeviceAuth
import ir.jibito.app.ui.lock.rememberDeviceAuthenticator
import ir.jibito.app.util.Jalali
import ir.jibito.app.ui.theme.JibitoTheme
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/** «پشتیبان‌گیری» در تنظیمات: آخرین پشتیبان، گرفتن پشتیبان رمزدار، بازگردانی. */
@Composable
fun BackupCard(card: @Composable (title: String, content: @Composable () -> Unit) -> Unit) {
    val context = LocalContext.current
    // متن‌ها از LocalResources (با تغییر پیکربندی، مثلاً زبان یا چرخش، به‌روز می‌ماند)
    val resources = LocalResources.current
    val container = (context.applicationContext as JibitoApplication).container
    val backup = container.backupManager
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var askExportPassword by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restored by remember { mutableStateOf<BackupManager.Summary?>(null) }
    // رمز فقط تا وقتی فایل انتخاب شود در حافظه می‌ماند
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val password = pendingPassword
        pendingPassword = null
        if (uri == null || password == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            message = try {
                context.contentResolver.openOutputStream(uri)!!.use { backup.export(it, password) }
                resources.getString(R.string.backup_export_done)
            } catch (e: Exception) {
                resources.getString(R.string.backup_export_failed)
            } finally {
                password.fill(' ')
                busy = false
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    val lastExportAt by backup.lastExportAt.collectAsState()

    card(stringResource(R.string.security_title)) {
        // پشتیبان
        Text(
            stringResource(R.string.backup_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            lastExportAt?.let { Jalali.toPersianDigits(stringResource(R.string.settings_backup_last, Jalali.format(it))) }
                ?: stringResource(R.string.settings_backup_last_never),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (backupOverdue(lastExportAt)) JibitoTheme.colors.warning else colors.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        } else {
            Button(
                onClick = { askExportPassword = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_export_button), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmRestore = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_restore_button)) }
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.primary, fontWeight = FontWeight.Bold)
        }
    }

    if (askExportPassword) {
        PasswordDialog(
            title = stringResource(R.string.backup_export_password_title),
            body = stringResource(R.string.backup_export_password_body),
            confirmLabel = stringResource(R.string.backup_export_continue),
            needsRepeat = true,
            onDismiss = { askExportPassword = false },
            onConfirm = { password ->
                askExportPassword = false
                message = null
                pendingPassword = password
                exportLauncher.launch(backupFileName())
            },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.backup_restore_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.backup_restore_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    message = null
                    restoreLauncher.launch(arrayOf("*/*"))
                }) { Text(stringResource(R.string.backup_restore_choose_file)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.budget_dialog_cancel)) }
            },
        )
    }

    restoreUri?.let { uri ->
        var error by remember(uri) { mutableStateOf<String?>(null) }
        var working by remember(uri) { mutableStateOf(false) }
        PasswordDialog(
            title = stringResource(R.string.backup_restore_password_title),
            body = stringResource(R.string.backup_restore_password_body),
            confirmLabel = stringResource(R.string.backup_restore_go),
            needsRepeat = false,
            error = error,
            working = working,
            onDismiss = { if (!working) restoreUri = null },
            onConfirm = { password ->
                working = true
                error = null
                scope.launch {
                    try {
                        val summary = context.contentResolver.openInputStream(uri)!!.use { backup.stageRestore(it, password) }
                        restoreUri = null
                        restored = summary
                    } catch (e: BackupCrypto.WrongPasswordException) {
                        error = resources.getString(R.string.backup_error_password)
                    } catch (e: BackupCrypto.NotABackupException) {
                        error = resources.getString(R.string.backup_error_not_backup)
                    } catch (e: BackupManager.TooNewException) {
                        error = resources.getString(R.string.backup_error_too_new)
                    } catch (e: Exception) {
                        error = resources.getString(R.string.backup_error_damaged)
                    } finally {
                        password.fill(' ')
                        working = false
                    }
                }
            },
        )
    }

    restored?.let { summary ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.backup_restore_ready_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    Jalali.toPersianDigits(
                        stringResource(
                            R.string.backup_restore_ready_body,
                            summary.transactionCount,
                            summary.createdAt?.let { Jalali.format(it) } ?: "—",
                        )
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { restartApp(context) }) {
                    Text(stringResource(R.string.backup_restore_restart), fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    body: String,
    confirmLabel: String,
    needsRepeat: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
    error: String? = null,
    working: Boolean = false,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = password.length < BackupCrypto.MIN_PASSWORD_LENGTH
    val mismatch = needsRepeat && repeat.isNotEmpty() && repeat != password
    val canConfirm = !working && !tooShort && (!needsRepeat || repeat == password)
    val colors = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    singleLine = true,
                    enabled = !working,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = if (needsRepeat && password.isNotEmpty() && tooShort) {
                        { Text(Jalali.toPersianDigits(stringResource(R.string.backup_password_short, BackupCrypto.MIN_PASSWORD_LENGTH))) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (needsRepeat) {
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = { Text(stringResource(R.string.backup_password_repeat)) },
                        singleLine = true,
                        isError = mismatch,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = if (mismatch) {
                            { Text(stringResource(R.string.backup_password_mismatch)) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (working) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password.toCharArray()) }, enabled = canConfirm) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) { Text(stringResource(R.string.budget_dialog_cancel)) }
        },
    )
}

/** مثلاً jibito-1405-07-10.jibito (تاریخ شمسی، با رقم انگلیسی تا در همه‌ی برنامه‌های فایل درست دیده شود) */
private fun backupFileName(): String {
    val now = java.util.Calendar.getInstance()
    val (y, m, d) = Jalali.fromGregorian(
        now.get(java.util.Calendar.YEAR),
        now.get(java.util.Calendar.MONTH) + 1,
        now.get(java.util.Calendar.DAY_OF_MONTH),
    )
    return "jibito-%04d-%02d-%02d.jibito".format(java.util.Locale.US, y, m, d)
}

/** بعد از آماده شدن بازگردانی: اپ از نو شروع می‌شود تا داده‌های پشتیبان جایگزین شوند (JibitoApplication). */
private fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
    if (launch?.component != null) {
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
    }
    exitProcess(0)
}

/** پشتیبانی که هیچ‌وقت گرفته نشده یا بیشتر از [BACKUP_REMIND_DAYS] روز از آن گذشته */
internal fun backupOverdue(lastExportAt: Long?, now: Long = System.currentTimeMillis()): Boolean =
    lastExportAt == null || daysSince(lastExportAt, now) > BACKUP_REMIND_DAYS

internal fun daysSince(at: Long, now: Long = System.currentTimeMillis()): Int =
    ((now - at).coerceAtLeast(0) / (24L * 60 * 60 * 1000)).toInt()

internal const val BACKUP_REMIND_DAYS = 30

/** قفل اپ: روشن/خاموش (روشن کردن با تأیید قفل گوشی)، برای ردیف کلیدی صفحه‌ی تنظیمات */
internal class AppLockState(val enabled: Boolean, val available: Boolean, val set: (Boolean) -> Unit)

@Composable
internal fun rememberAppLock(): AppLockState {
    val context = LocalContext.current
    val container = (context.applicationContext as JibitoApplication).container
    val lockSettings = container.appLockSettings
    val enabled by lockSettings.enabled.collectAsState()
    val turnOn = rememberDeviceAuthenticator(
        title = stringResource(R.string.lock_prompt_title),
        subtitle = stringResource(R.string.lock_enable_subtitle),
    ) { ok ->
        if (ok) {
            AppLockSession.unlocked = true
            lockSettings.setEnabled(true)
            container.refreshWidget()
        }
    }
    return AppLockState(enabled = enabled, available = DeviceAuth.isAvailable(context)) { on ->
        if (on) {
            turnOn()
        } else {
            lockSettings.setEnabled(false)
            container.refreshWidget()
        }
    }
}
