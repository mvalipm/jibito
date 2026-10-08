package ir.jibito.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.category.CreateCategoryResult
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Category
import ir.jibito.app.ui.common.CategoryIconTile
import ir.jibito.app.ui.smslist.CreateTarget
import ir.jibito.app.ui.smslist.NewCategoryDialog
import ir.jibito.app.ui.theme.DarkMode
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.util.ErrorLog
import ir.jibito.app.util.Jalali
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** گزینه‌های «چند لایه»: عمق، اسم، توضیح */
internal val DEPTH_OPTIONS = listOf(
    Triple(1, R.string.settings_depth_1, R.string.settings_depth_1_hint),
    Triple(2, R.string.settings_depth_2, R.string.settings_depth_2_hint),
    Triple(3, R.string.settings_depth_3, R.string.settings_depth_3_hint),
)

/** «پوسته»: سه جیب رنگی، و روشن / تیره / مثل گوشی */
@Composable
internal fun ThemePageContent() {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val themeSettings = app.container.themeSettings
    val style by themeSettings.style.collectAsState()
    val darkMode by themeSettings.darkMode.collectAsState()
    // اول روشن/تیره (بیشتر عوض می‌شود)، بعد رنگ‌ها
    PageCard {
        Text(
            stringResource(R.string.settings_dark_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        SegmentedChoice(
            options = DarkMode.entries.map { it to stringResource(it.label) },
            selected = darkMode,
            onSelect = themeSettings::setDarkMode,
        )
        Spacer(Modifier.height(20.dp))
        ThemePicker(style, onSelect = themeSettings::set)
    }
}

/** «نمایش دسته‌ها»: چند لایه، و کدام دسته‌های اصلی دیده شوند */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryDisplayPageContent(categories: List<Category>) {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val display = app.container.categoryDisplay
    val depth by display.depth.collectAsState()
    val hidden by display.hiddenRoots.collectAsState()
    val roots = categories.filter { it.parentId == null && it.flowType == FlowType.WITHDRAWAL.code }

    PageCard {
        Text(
            stringResource(R.string.settings_display_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        SegmentedChoice(
            options = DEPTH_OPTIONS.map { (d, label, _) -> d to stringResource(label) },
            selected = depth,
            onSelect = display::setDepth,
        )
        DEPTH_OPTIONS.firstOrNull { it.first == depth }?.let { (_, _, hint) ->
            Text(
                stringResource(hint),
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                fontSize = 12.sp,
                color = JibitoTheme.colors.muted,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    PageCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_roots_title),
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                Jalali.toPersianDigits(stringResource(R.string.settings_roots_count, roots.count { it.id !in hidden }, roots.size)),
                fontSize = 13.sp,
                color = JibitoTheme.colors.muted,
            )
        }
        Spacer(Modifier.height(12.dp))
        // هر دسته یک کپسول که با لمس روشن/خاموش می‌شود
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            roots.forEach { root ->
                val visible = root.id !in hidden
                val tint = categoryTint(root.colorHex, root.icon)
                Row(
                    Modifier
                        .height(40.dp)
                        .background(if (visible) tint.bg else Color.Transparent, RoundedCornerShape(20.dp))
                        .border(1.dp, if (visible) Color.Transparent else JibitoTheme.colors.border, RoundedCornerShape(20.dp))
                        .toggleable(value = visible, role = Role.Checkbox, onValueChange = { display.setRootVisible(root.id, it) })
                        .padding(start = 10.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = tint.icon
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = if (visible) tint.fg else JibitoTheme.colors.faint, modifier = Modifier.size(18.dp))
                    } else {
                        Text(tint.glyph.orEmpty(), fontSize = 14.sp)
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(
                        root.name,
                        fontSize = 13.sp,
                        fontWeight = if (visible) FontWeight.Bold else FontWeight.Normal,
                        color = if (visible) tint.fg else JibitoTheme.colors.faint,
                        textDecoration = if (visible) null else TextDecoration.LineThrough,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** «دسته‌های شخصی»: ساختن، عوض کردن اسم، پاک کردن */
@Composable
internal fun CustomCategoriesPageContent(categories: List<Category>) {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val repository = app.container.transactionRepository
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val byId = categories.associateBy { it.id }
    val custom = categories.filter { it.isCustom }
    var deleting by remember { mutableStateOf<Category?>(null) }
    var renaming by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }

    PageCard {
        Text(
            stringResource(if (custom.isEmpty()) R.string.settings_custom_empty else R.string.settings_custom_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        if (custom.isNotEmpty()) Spacer(Modifier.height(6.dp))
        custom.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val root = generateSequence(c) { x -> x.parentId?.let { byId[it] } }.take(10).last()
                CategoryIconTile(categoryTint(root.colorHex, c.icon ?: root.icon), size = 38.dp, radius = 12.dp, iconSize = 18.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    c.parentId?.let { byId[it]?.name }?.let { parent ->
                        Text(parent, fontSize = 12.sp, color = JibitoTheme.colors.muted)
                    }
                }
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            DesignIcons.Dots,
                            contentDescription = stringResource(R.string.settings_custom_more, c.name),
                            tint = JibitoTheme.colors.muted,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_custom_rename)) },
                            onClick = { menuOpen = false; renaming = c },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_custom_delete), color = colors.error) },
                            onClick = { menuOpen = false; deleting = c },
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = { adding = true },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(stringResource(R.string.settings_custom_new), fontWeight = FontWeight.Bold) }

    if (adding) {
        NewCategoryDialog(
            target = CreateTarget(parentId = null, chooseParent = true),
            roots = categories.filter { it.parentId == null && it.flowType == FlowType.WITHDRAWAL.code },
            byId = byId,
            onConfirm = { name, parentId, icon, nature, onResult ->
                scope.launch {
                    val result = repository.createCategory(name, parentId, FlowType.WITHDRAWAL.code, icon, nature)
                    onResult(result)
                    if (result is CreateCategoryResult.Created) adding = false
                }
            },
            onDismiss = { adding = false },
            askNature = true,
        )
    }

    renaming?.let { c ->
        RenameDialog(
            category = c,
            onRename = { name, onResult ->
                scope.launch {
                    val result = repository.renameCustomCategory(c.id, name)
                    onResult(result)
                    if (result is CreateCategoryResult.Created) renaming = null
                }
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { c ->
        val parentName = c.parentId?.let { id -> byId[id]?.name }
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

@Composable
private fun RenameDialog(
    category: Category,
    onRename: (String, (CreateCategoryResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(category.name) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val errEmpty = stringResource(R.string.custom_error_empty)
    val errLong = stringResource(R.string.custom_error_long)
    val errDup = stringResource(R.string.settings_custom_rename_duplicate)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_custom_rename_title, category.name), fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                singleLine = true,
                label = { Text(stringResource(R.string.custom_name)) },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    onRename(name) { result ->
                        saving = false
                        if (result is CreateCategoryResult.Invalid) {
                            error = when (result.reason) {
                                CreateCategoryResult.Reason.EMPTY -> errEmpty
                                CreateCategoryResult.Reason.TOO_LONG -> errLong
                                CreateCategoryResult.Reason.DUPLICATE -> errDup
                            }
                        }
                    }
                },
            ) { Text(stringResource(R.string.settings_save), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.budget_dialog_cancel)) } },
    )
}

/** «حریم خصوصی»: قول‌های جیبیتو درباره‌ی داده‌ها، هر کدام یک خط با آیکون */
@Composable
internal fun PrivacyPageContent() {
    PageCard {
        Text(
            stringResource(R.string.settings_privacy_intro),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        PromiseLine(SettingsIcons.Shield, stringResource(R.string.settings_privacy_local))
        PromiseLine(JibitoIcons.Bank, stringResource(R.string.settings_privacy_bank_only))
        PromiseLine(JibitoIcons.Message, stringResource(R.string.settings_privacy_no_send))
        PromiseLine(JibitoIcons.Lock, stringResource(R.string.settings_privacy_backup))
        PromiseLine(JibitoIcons.Warning, stringResource(R.string.settings_privacy_errorlog))
        PromiseLine(DesignIcons.EyeOff, stringResource(R.string.settings_privacy_hide))
    }
}

@Composable
private fun PromiseLine(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).background(JibitoTheme.colors.teal.copy(alpha = 0.14f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = JibitoTheme.colors.teal, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** «پیشرفته»: دوباره خوندن همه‌ی پیامک‌ها، و گزارش خطا */
@Composable
internal fun AdvancedPageContent(smsOk: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext as JibitoApplication
    val repository = app.container.transactionRepository
    val isSyncing by repository.isSyncing.collectAsState()
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

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
            // کار نگهداری است، نه کار اصلی صفحه ← دکمه‌ی ثانویه
            OutlinedButton(
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
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.settings_rescan_button), fontWeight = FontWeight.Bold) }
            if (!smsOk) {
                Text(
                    stringResource(R.string.settings_rescan_needs_sms),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = JibitoTheme.colors.warning,
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    ErrorLogCard { title, content -> SettingsSection(title, JibitoIcons.Warning, content) }
}
