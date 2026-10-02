package ir.jibito.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.ui.theme.AppThemeStyle
import ir.jibito.app.ui.theme.previewColors
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import ir.jibito.app.domain.Category
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.ErrorLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * تنظیمات: پوسته، خواندن دوباره‌ی پیامک‌ها، دسترسی‌ها، نمایش دسته‌ها، دسته‌های شخصی،
 * قفل اپ و پشتیبان‌گیری، نسخه‌ی اپ.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as JibitoApplication
    val repository = app.container.transactionRepository
    val isSyncing by repository.isSyncing.collectAsState()
    val categoriesFlow = remember { repository.observeCategories() }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    var deleting by remember { mutableStateOf<Category?>(null) }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val smsOk = granted(Manifest.permission.READ_SMS) && granted(Manifest.permission.RECEIVE_SMS)
    val notifyOk = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull().orEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            stringResource(R.string.settings_title),
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 16.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = colors.onBackground,
        )

        // پوسته
        val themeSettings = app.container.themeSettings
        val currentStyle by themeSettings.style.collectAsState()
        SettingsCard(stringResource(R.string.settings_theme_title)) {
            AppThemeStyle.entries.forEach { style ->
                val selected = style == currentStyle
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) colors.primary else colors.outlineVariant,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .clickable { themeSettings.set(style) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // نمونه‌ی رنگ‌ها
                    Row {
                        previewColors(style).forEachIndexed { i, c ->
                            Box(
                                Modifier
                                    .offset(x = (-8 * i).dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(2.dp, colors.surface, CircleShape)
                            )
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(
                            stringResource(style.label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        Text(
                            stringResource(style.hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    if (selected) Text("✓", color = colors.primary, fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // خواندن دوباره‌ی همه‌ی پیامک‌ها
        SettingsCard(stringResource(R.string.settings_rescan_title)) {
            Text(
                stringResource(R.string.settings_rescan_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (isSyncing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.list_syncing),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                repository.syncFromSms(forceFull = true)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                ErrorLog.record(context, "full rescan", e)
                            }
                        }
                    },
                    enabled = smsOk,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_rescan_button), fontWeight = FontWeight.Bold) }
            }
        }

        Spacer(Modifier.height(12.dp))

        // دسترسی‌ها
        SettingsCard(stringResource(R.string.settings_permissions_title)) {
            PermissionRow(stringResource(R.string.settings_perm_sms), smsOk)
            PermissionRow(stringResource(R.string.settings_perm_notify), notifyOk)
            if (!smsOk || !notifyOk) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_open_app_settings)) }
            }
        }

        Spacer(Modifier.height(12.dp))

        // دسته‌های شخصی (ساخته‌شده از برگه‌ی انتخاب دسته)
        val byId = categories.associateBy { it.id }
        val custom = categories.filter { it.isCustom }
        // نمایش دسته‌ها: چند لایه، و کدام دسته‌های اصلی
        val display = app.container.categoryDisplay
        val depth by display.depth.collectAsState()
        val hidden by display.hiddenRoots.collectAsState()
        SettingsCard(stringResource(R.string.settings_display_title)) {
            Text(
                stringResource(R.string.settings_display_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            listOf(
                Triple(1, R.string.settings_depth_1, R.string.settings_depth_1_hint),
                Triple(2, R.string.settings_depth_2, R.string.settings_depth_2_hint),
                Triple(3, R.string.settings_depth_3, R.string.settings_depth_3_hint),
            ).forEach { (d, label, hint) ->
                val selected = depth == d
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) colors.primary else colors.outlineVariant,
                            shape = RoundedCornerShape(14.dp),
                        )
                        .clickable { display.setDepth(d) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Text(stringResource(hint), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                    if (selected) Text("✓", color = colors.primary, fontWeight = FontWeight.Black)
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.settings_roots_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            categories.filter { it.parentId == null && it.flowType == 2 }.forEach { root ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { display.setRootVisible(root.id, root.id in hidden) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        listOfNotNull(root.icon, root.name).joinToString(" "),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (root.id in hidden) colors.onSurfaceVariant else colors.onSurface,
                    )
                    Switch(
                        checked = root.id !in hidden,
                        onCheckedChange = { display.setRootVisible(root.id, it) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SettingsCard(stringResource(R.string.settings_custom_title)) {
            if (custom.isEmpty()) {
                Text(
                    stringResource(R.string.settings_custom_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            custom.forEach { c ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val path = listOfNotNull(c.icon ?: rootIcon(c, byId), c.name, c.parentId?.let { byId[it]?.name }?.let { "· $it" })
                    Text(
                        path.joinToString(" "),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                    )
                    TextButton(onClick = { deleting = c }) {
                        Text(stringResource(R.string.settings_custom_delete), color = colors.error)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // پرداخت‌های ماهانه (یادآوری)
        RecurringCard { title, content -> SettingsCard(title, content) }

        Spacer(Modifier.height(12.dp))
        // خروجی اکسل
        ExportCard { title, content -> SettingsCard(title, content) }

        Spacer(Modifier.height(12.dp))
        // قفل اپ و پشتیبان‌گیری
        SecurityBackupCard { title, content -> SettingsCard(title, content) }

        Spacer(Modifier.height(12.dp))
        ErrorLogCard { title, content -> SettingsCard(title, content) }

        Text(
            Jalali.toPersianDigits(stringResource(R.string.settings_version, version)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // جای خالی زیر محتوا، تا آخرین بخش زیر نوار شناور گم نشود
        Spacer(Modifier.height(LocalBottomBarSpace.current + 16.dp))
    }

    deleting?.let { c ->
        val parentName = c.parentId?.let { id -> categories.firstOrNull { it.id == id }?.name }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.settings_custom_delete_title, c.name), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (parentName != null) stringResource(R.string.settings_custom_delete_to_parent, parentName)
                    else stringResource(R.string.settings_custom_delete_to_none)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteCustomCategory(c.id) }
                    deleting = null
                }) { Text(stringResource(R.string.settings_custom_delete), color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.budget_dialog_cancel)) }
            },
        )
    }
}

/** آیکون دسته‌ی اصلی (زیردسته‌ها آیکون ندارند) */
private fun rootIcon(c: Category, byId: Map<Long, Category>): String? {
    var current = c
    var steps = 0
    while (current.parentId != null && steps < 10) {
        current = byId[current.parentId] ?: break
        steps++
    }
    return current.icon
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = colors.onSurface)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun PermissionRow(label: String, ok: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        Text(
            stringResource(if (ok) R.string.settings_perm_ok else R.string.settings_perm_missing),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (ok) Color(0xFF1E9E6A) else colors.error,
        )
    }
}
