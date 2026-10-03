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
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.AppThemeStyle
import ir.jibito.app.ui.theme.previewColors
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import ir.jibito.app.domain.Category
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.ErrorLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import ir.jibito.app.ui.theme.JibitoIcons
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Arrangement
import ir.jibito.app.ui.common.PocketMascot
import ir.jibito.app.ui.common.MascotFace

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
        // مشکل‌ها اول: بدون این دسترسی‌ها اپ کار اصلی‌اش را نمی‌کند
        if (!smsOk || !notifyOk) {
            PermissionBanner(smsOk = smsOk, notifyOk = notifyOk, onFix = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            })
            Spacer(Modifier.height(24.dp))
        }

        // پوسته
        val themeSettings = app.container.themeSettings
        val currentStyle by themeSettings.style.collectAsState()
        SettingsSection(stringResource(R.string.settings_theme_title), JibitoIcons.Palette) {
            ThemePicker(currentStyle, onSelect = themeSettings::set)
        }

        SectionGap()

        // خواندن دوباره‌ی همه‌ی پیامک‌ها
        SettingsSection(stringResource(R.string.settings_rescan_title), JibitoIcons.Message) {
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

        SectionGap()

        // دسترسی‌ها
        SettingsSection(stringResource(R.string.settings_permissions_title), JibitoIcons.Bell) {
            PermissionRow(stringResource(R.string.settings_perm_sms), smsOk)
            PermissionRow(stringResource(R.string.settings_perm_notify), notifyOk)
            // خلاصه‌ی هفتگی (جمعه‌ها عصر)
            val digest = app.container.weeklyDigest
            val digestOn by digest.enabled.collectAsState()
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_digest), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                    Text(stringResource(R.string.settings_digest_hint), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                Switch(checked = digestOn, onCheckedChange = digest::setEnabled)
            }
        }

        SectionGap()

        // دسته‌های شخصی (ساخته‌شده از برگه‌ی انتخاب دسته)
        val byId = categories.associateBy { it.id }
        val custom = categories.filter { it.isCustom }
        // نمایش دسته‌ها: چند لایه، و کدام دسته‌های اصلی
        val display = app.container.categoryDisplay
        val depth by display.depth.collectAsState()
        val hidden by display.hiddenRoots.collectAsState()
        SettingsSection(stringResource(R.string.settings_display_title), JibitoIcons.Layers) {
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
                    if (selected) Icon(JibitoIcons.Check, contentDescription = stringResource(R.string.cd_selected), tint = colors.primary, modifier = Modifier.size(22.dp))
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

        SectionGap()

        SettingsSection(stringResource(R.string.settings_custom_title), JibitoIcons.Tag) {
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

        SectionGap()
        // پرداخت‌های ماهانه (یادآوری)
        RecurringCard { title, content -> SettingsSection(title, JibitoIcons.Repeat, content) }

        SectionGap()
        // خروجی اکسل
        ExportCard { title, content -> SettingsSection(title, JibitoIcons.Export, content) }

        SectionGap()
        // قفل اپ و پشتیبان‌گیری
        SecurityBackupCard { title, content -> SettingsSection(title, JibitoIcons.Lock, content) }

        SectionGap()
        ErrorLogCard { title, content -> SettingsSection(title, JibitoIcons.Warning, content) }

        // پایین صفحه: جیبی و نسخه
        Column(
            Modifier.fillMaxWidth().padding(top = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PocketMascot(MascotFace.HAPPY, size = 52.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                Jalali.toPersianDigits(stringResource(R.string.settings_version, version)),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
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

/** فاصله‌ی بین بخش‌ها (به‌جای کارت، فضای خالی جدا می‌کند) */
@Composable
private fun SectionGap() = Spacer(Modifier.height(30.dp))

/**
 * یک بخش تنظیمات، بدون کارت: سرتیتر با آیکون رنگی کم‌رنگ، و محتوا زیرش.
 */
@Composable
internal fun SettingsSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.size(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = colors.onBackground)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/**
 * پوسته‌ها کنار هم: هر کدام یک «جیب» کوچک به رنگ‌های خودش؛ انتخاب‌شده حلقه‌ی رنگی و تیک دارد،
 * و توضیحش زیر ردیف می‌آید.
 */
@Composable
internal fun ThemePicker(current: AppThemeStyle, onSelect: (AppThemeStyle) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppThemeStyle.entries.forEach { style ->
                val selected = style == current
                val ring by animateColorAsState(if (selected) colors.primary else Color.Transparent, label = "themeRing")
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .border(2.dp, ring, RoundedCornerShape(20.dp))
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(style) })
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ThemeSwatch(previewColors(style), selected)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(style.label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                        color = colors.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(current.hint),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

/** نمونه‌ی یک پوسته: جیب کوچک به رنگ اول، دو سکه به رنگ‌های بعدی، تیک وقتی انتخاب شده */
@Composable
private fun ThemeSwatch(palette: List<Color>, selected: Boolean) {
    Box(Modifier.size(width = 56.dp, height = 52.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            // سکه‌ها پشت جیب
            drawCircle(palette.getOrElse(2) { palette.last() }, radius = w * 0.13f, center = Offset(w * 0.68f, h * 0.18f))
            drawCircle(palette.getOrElse(1) { palette.last() }, radius = w * 0.13f, center = Offset(w * 0.38f, h * 0.22f))
            val top = h * 0.3f
            val pocket = Path().apply {
                moveTo(w * 0.12f, top)
                lineTo(w * 0.88f, top)
                lineTo(w * 0.88f, h * 0.62f)
                cubicTo(w * 0.88f, h * 0.82f, w * 0.66f, h * 0.94f, w * 0.5f, h)
                cubicTo(w * 0.34f, h * 0.94f, w * 0.12f, h * 0.82f, w * 0.12f, h * 0.62f)
                close()
            }
            drawPath(pocket, palette.first())
            drawRect(palette.getOrElse(3) { palette.last() }.copy(alpha = 0.9f), topLeft = Offset(w * 0.12f, top), size = Size(w * 0.76f, h * 0.1f))
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(JibitoIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp)) }
        }
    }
}

/** بالای تنظیمات وقتی دسترسی لازم نیست: چه چیزی خاموش است و چرا مهم است، با یک دکمه */
@Composable
internal fun PermissionBanner(smsOk: Boolean, notifyOk: Boolean, onFix: () -> Unit) {
    val background = JibitoTheme.colors.moodWarn
    Column(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(JibitoIcons.Bell, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(if (!smsOk) R.string.settings_banner_sms else R.string.settings_banner_notify),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onFix,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = background),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_banner_fix), fontWeight = FontWeight.Black) }
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
            color = if (ok) JibitoTheme.colors.income else colors.error,
        )
    }
}
