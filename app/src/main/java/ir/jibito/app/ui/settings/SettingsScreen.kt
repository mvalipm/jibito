package ir.jibito.app.ui.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Category
import ir.jibito.app.ui.common.MascotFace
import ir.jibito.app.ui.common.PocketMascot
import ir.jibito.app.ui.common.openSupportChat
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.ErrorLog
import ir.jibito.app.util.Jalali
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** زیرصفحه‌های تنظیمات */
internal enum class SettingsPage { Backup, Export, Accounts, CategoryDisplay, CustomCategories, Recurring, Theme, Privacy, Advanced, NotificationStyle, WhatsNew, Help }

/**
 * تنظیمات: یک فهرست گروه‌بندی‌شده که هر ردیفش «مقدار فعلی» را هم نشان می‌دهد
 * (مثلاً «آخرین پشتیبان: ۳ روز پیش»، «مرجانی · مثل گوشی»)، تا وضعیت همه‌چیز با یک نگاه معلوم باشد.
 * کلیدهای ساده (قفل اپ، خلاصه‌ی هفتگی) همان‌جا روشن/خاموش می‌شوند و بقیه زیرصفحه‌ی خودشان را باز می‌کنند.
 * دکمه‌ی برگشت گوشی از زیرصفحه به فهرست برمی‌گردد.
 *
 * @param onBack تنظیمات از چرخ‌دنده‌ی «خلاصه» باز می‌شود (تب نیست): دکمه‌ی برگشت کنار عنوان
 */
@Composable
fun SettingsScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val app = context.applicationContext as JibitoApplication
    val repository = app.container.transactionRepository
    val categoriesFlow = remember { repository.observeCategories() }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val permissions = rememberSettingsPermissions(onSmsGranted = {
        // دسترسی تازه داده شد ← پیامک‌ها همین الان خوانده شوند
        scope.launch {
            try {
                repository.syncFromSms()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ErrorLog.record(context, "sync after permission", e)
            }
        }
    })

    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }
    // جای اسکرول فهرست بعد از برگشتن از زیرصفحه حفظ شود
    val hubScroll = rememberScrollState()

    AnimatedContent(
        targetState = page,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "settingsPage",
    ) { current ->
        if (current == null) {
            SettingsHub(hubScroll, categories, permissions, onBack, onOpen = { page = it })
        } else {
            SettingsPageScreen(current, categories, permissions, onBack = { page = null })
        }
    }
}

/** صفحه‌ی اسکرول‌شونده‌ی تنظیمات با فاصله‌ی درست بالا و پایین (زیر نوار شناور) */
@Composable
private fun SettingsColumn(scroll: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp)
    ) {
        content()
        // جای خالی زیر محتوا، تا آخرین بخش زیر نوار شناور گم نشود
        Spacer(Modifier.height(LocalBottomBarSpace.current + 16.dp))
    }
}

@Composable
private fun SettingsHub(
    scroll: ScrollState,
    categories: List<Category>,
    permissions: SettingsPermissions,
    onBack: (() -> Unit)?,
    onOpen: (SettingsPage) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val container = (context.applicationContext as JibitoApplication).container
    val tones = JibitoTheme.colors
    val primary = MaterialTheme.colorScheme.primary
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    SettingsColumn(scroll) {
        Row(Modifier.padding(top = 18.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(JibitoIcons.Back, contentDescription = stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.onBackground)
                }
            }
            Text(
                stringResource(R.string.settings_title),
                modifier = Modifier.padding(start = 4.dp),
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        // بنر فقط وقتی اپ واقعاً کار اصلی‌اش را نمی‌کند (پیامک خاموش)؛ نوتیف خاموش فقط در ردیف خودش دیده می‌شود
        if (!permissions.smsOk) {
            PermissionBanner(
                smsOk = false,
                notifyOk = permissions.notifyOk,
                onFix = permissions.fixSms,
            )
            Spacer(Modifier.height(24.dp))
        }

        // ── داده‌هات ──
        val lastExportAt by container.backupManager.lastExportAt.collectAsState()
        val lock = rememberAppLock()
        SettingsGroup(stringResource(R.string.settings_group_data)) {
            val backupDue = backupOverdue(lastExportAt)
            SettingsRow(
                icon = SettingsIcons.Backup,
                tint = tones.teal,
                title = stringResource(R.string.security_title),
                value = Jalali.toPersianDigits(backupValue(lastExportAt)),
                attention = backupDue,
                // وقتش گذشته: دکمه‌ی خود کار، نه فقط هشدار
                trailing = if (backupDue) RowTrailing.Action(stringResource(R.string.settings_backup_now)) { onOpen(SettingsPage.Backup) } else RowTrailing.Chevron,
                onClick = { onOpen(SettingsPage.Backup) },
            )
            RowDivider()
            SettingsRow(
                icon = JibitoIcons.Lock,
                tint = tones.transfer,
                title = stringResource(R.string.lock_setting_title),
                value = stringResource(if (lock.available || lock.enabled) R.string.lock_setting_hint else R.string.lock_setting_unavailable),
                trailing = RowTrailing.Toggle(
                    checked = lock.enabled,
                    enabled = lock.enabled || lock.available,
                    onChange = lock.set,
                ),
            )
            RowDivider()
            // چند حساب در یک بانک: اسم، یکی/جدا کردن، آمدن در جمع موجودی
            val accountGroupsFlow = remember { container.accountRepository.observeGroups() }
            val accountGroups by accountGroupsFlow.collectAsState(initial = emptyList())
            val accountQuestionFlow = remember { container.accountRepository.observeQuestion() }
            val accountQuestion by accountQuestionFlow.collectAsState(initial = null)
            val (accountCount, bankCount) = accountsSummary(accountGroups)
            SettingsRow(
                icon = JibitoIcons.Bank,
                tint = tones.income,
                title = stringResource(R.string.settings_accounts_title),
                value = when {
                    accountQuestion != null -> stringResource(R.string.settings_accounts_question)
                    accountCount == 0 -> stringResource(R.string.settings_accounts_none)
                    else -> Jalali.toPersianDigits(stringResource(R.string.settings_accounts_sub, accountCount, bankCount))
                },
                attention = accountQuestion != null,
                onClick = { onOpen(SettingsPage.Accounts) },
            )
            RowDivider()
            SettingsRow(
                icon = JibitoIcons.Export,
                tint = tones.income,
                title = stringResource(R.string.export_title),
                value = stringResource(R.string.settings_export_sub),
                onClick = { onOpen(SettingsPage.Export) },
            )
        }
        GroupGap()

        // ── دسته‌ها و پرداخت‌ها ──
        val display = container.categoryDisplay
        val depth by display.depth.collectAsState()
        val hidden by display.hiddenRoots.collectAsState()
        val roots = categories.filter { it.parentId == null && it.flowType == FlowType.WITHDRAWAL.code }
        val customCount = categories.count { it.isCustom }
        val recurringFlow = remember { container.recurringRepository.observeAll() }
        val recurring by recurringFlow.collectAsState(initial = emptyList())
        val suggestionsFlow = remember { container.recurringSuggestions.observe() }
        val suggestions by suggestionsFlow.collectAsState(initial = emptyList())
        SettingsGroup(stringResource(R.string.settings_group_categories)) {
            SettingsRow(
                icon = JibitoIcons.Layers,
                tint = primary,
                title = stringResource(R.string.settings_display_title),
                value = Jalali.toPersianDigits(
                    stringResource(
                        R.string.settings_display_sub,
                        stringResource(DEPTH_OPTIONS.firstOrNull { it.first == depth }?.second ?: R.string.settings_depth_3),
                        roots.count { it.id !in hidden },
                        roots.size,
                    )
                ),
                onClick = { onOpen(SettingsPage.CategoryDisplay) },
            )
            RowDivider()
            SettingsRow(
                icon = JibitoIcons.Tag,
                tint = tones.amber,
                title = stringResource(R.string.settings_custom_title),
                value = if (customCount == 0) stringResource(R.string.settings_custom_none)
                else Jalali.toPersianDigits(stringResource(R.string.settings_custom_count, customCount)),
                onClick = { onOpen(SettingsPage.CustomCategories) },
            )
            RowDivider()
            SettingsRow(
                icon = JibitoIcons.Repeat,
                tint = tones.teal,
                title = stringResource(R.string.recurring_title),
                value = if (recurring.isEmpty() && suggestions.isEmpty()) {
                    stringResource(R.string.settings_recurring_none)
                } else {
                    Jalali.toPersianDigits(
                        listOfNotNull(
                            recurring.size.takeIf { it > 0 }?.let { resources.getString(R.string.settings_recurring_count, it) },
                            suggestions.size.takeIf { it > 0 }?.let { resources.getString(R.string.settings_recurring_suggest, it) },
                        ).joinToString(" · ")
                    )
                },
                attention = suggestions.isNotEmpty(),
                onClick = { onOpen(SettingsPage.Recurring) },
            )
        }
        GroupGap()

        // ── نوتیف و دسترسی‌ها ──
        val digest = container.weeklyDigest
        val digestOn by digest.enabled.collectAsState()
        val notifStyle by container.notificationStyle.style.collectAsState()
        SettingsGroup(stringResource(R.string.settings_group_alerts)) {
            SettingsRow(
                icon = JibitoIcons.Message,
                tint = primary,
                title = stringResource(R.string.settings_perm_sms),
                value = stringResource(if (permissions.smsOk) R.string.settings_sms_sub_ok else R.string.settings_perm_missing),
                attention = !permissions.smsOk,
                trailing = if (permissions.smsOk) RowTrailing.Status(true) else RowTrailing.Action(stringResource(R.string.todo_turn_on), permissions.fixSms),
                onClick = if (permissions.smsOk) null else permissions.fixSms,
            )
            RowDivider()
            SettingsRow(
                icon = JibitoIcons.Bell,
                tint = tones.amber,
                title = stringResource(R.string.settings_perm_notify),
                value = stringResource(if (permissions.notifyOk) R.string.settings_notify_sub_ok else R.string.settings_perm_missing),
                attention = !permissions.notifyOk,
                trailing = if (permissions.notifyOk) RowTrailing.Status(true) else RowTrailing.Action(stringResource(R.string.todo_turn_on), permissions.fixNotify),
                onClick = if (permissions.notifyOk) null else permissions.fixNotify,
            )
            RowDivider()
            SettingsRow(
                icon = SettingsIcons.Pen,
                tint = tones.teal,
                title = stringResource(R.string.settings_notif_style_title),
                value = stringResource(notifStyle.label),
                onClick = { onOpen(SettingsPage.NotificationStyle) },
            )
            RowDivider()
            SettingsRow(
                icon = SettingsIcons.Weekly,
                tint = tones.transfer,
                title = stringResource(R.string.settings_digest),
                value = stringResource(R.string.settings_digest_hint),
                trailing = RowTrailing.Toggle(checked = digestOn, onChange = digest::setEnabled),
            )
        }
        GroupGap()

        // ── ظاهر ──
        val style by container.themeSettings.style.collectAsState()
        val darkMode by container.themeSettings.darkMode.collectAsState()
        SettingsGroup(stringResource(R.string.settings_group_look)) {
            SettingsRow(
                icon = JibitoIcons.Palette,
                tint = primary,
                title = stringResource(R.string.settings_theme_title),
                value = stringResource(R.string.settings_theme_sub, stringResource(style.label), stringResource(darkMode.label)),
                onClick = { onOpen(SettingsPage.Theme) },
            )
        }
        GroupGap()

        // ── جیبیتو ──
        // بعد از «پاک کردن» در صفحه‌ی پیشرفته، با برگشتن به فهرست دوباره خوانده می‌شود
        val errorCount = remember { ErrorLog.count(context) }
        SettingsGroup(stringResource(R.string.settings_group_about)) {
            SettingsRow(
                icon = SettingsIcons.Help,
                tint = tones.teal,
                title = stringResource(R.string.help_title),
                value = stringResource(R.string.help_sub),
                onClick = { onOpen(SettingsPage.Help) },
            )
            RowDivider()
            SettingsRow(
                icon = SettingsIcons.Shield,
                tint = tones.teal,
                title = stringResource(R.string.settings_privacy_title),
                value = stringResource(R.string.settings_privacy_sub),
                onClick = { onOpen(SettingsPage.Privacy) },
            )
            RowDivider()
            SettingsRow(
                icon = SettingsIcons.Sparkle,
                tint = tones.amber,
                title = stringResource(R.string.whats_new_title),
                value = Jalali.toPersianDigits(stringResource(R.string.whats_new_version, version.substringBefore('-'))),
                onClick = { onOpen(SettingsPage.WhatsNew) },
            )
            RowDivider()
            SettingsRow(
                icon = SettingsIcons.Heart,
                tint = tones.alert,
                title = stringResource(R.string.settings_feedback_title),
                value = stringResource(R.string.settings_feedback_sub),
                onClick = {
                    // چت پشتیبانی در بله؛ متن آماده (با نسخه‌ی اپ و اندروید) کپی می‌شود تا کاربر بچسباند و نظرش را بنویسد
                    openSupportChat(
                        context,
                        text = resources.getString(R.string.settings_feedback_body, version, Build.VERSION.RELEASE),
                        copiedHint = resources.getString(R.string.settings_feedback_copied),
                        chooserTitle = resources.getString(R.string.settings_feedback_title),
                        subject = resources.getString(R.string.settings_feedback_subject),
                    )
                },
            )
            RowDivider()
            SettingsRow(
                icon = SettingsIcons.Wrench,
                tint = tones.muted,
                title = stringResource(R.string.settings_advanced_title),
                value = if (errorCount > 0) Jalali.toPersianDigits(stringResource(R.string.settings_advanced_errors, errorCount))
                else stringResource(R.string.settings_advanced_sub),
                attention = errorCount > 0,
                onClick = { onOpen(SettingsPage.Advanced) },
            )
        }

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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** «آخرین پشتیبان: ۳ روز پیش» / «هنوز پشتیبان نگرفتی» / «۴۵ روزه پشتیبان نگرفتی؛ وقتشه!» */
@Composable
private fun backupValue(lastExportAt: Long?): String {
    if (lastExportAt == null) return stringResource(R.string.settings_backup_never)
    val days = daysSince(lastExportAt)
    return when {
        days > BACKUP_REMIND_DAYS -> stringResource(R.string.settings_backup_old, days)
        days == 0 -> stringResource(R.string.settings_backup_today)
        days == 1 -> stringResource(R.string.settings_backup_yesterday)
        else -> stringResource(R.string.settings_backup_days, days)
    }
}

/** فاصله‌ی بین گروه‌ها */
@Composable
private fun GroupGap() = Spacer(Modifier.height(22.dp))

@Composable
private fun SettingsPageScreen(
    page: SettingsPage,
    categories: List<Category>,
    permissions: SettingsPermissions,
    onBack: () -> Unit,
) {
    val scroll = rememberScrollState()
    // کارت‌هایی که عنوان خودشان را می‌دهند: عنوان در سربرگ صفحه، محتوا در کارت
    val asPage: @Composable (String, @Composable () -> Unit) -> Unit = { title, content ->
        SettingsPageHeader(title, onBack)
        PageCard { content() }
    }
    SettingsColumn(scroll) {
        when (page) {
            SettingsPage.Backup -> BackupCard(asPage)
            SettingsPage.Export -> ExportCard(asPage)
            SettingsPage.Accounts -> {
                SettingsPageHeader(stringResource(R.string.settings_accounts_title), onBack)
                AccountsPageContent()
            }
            SettingsPage.Recurring -> RecurringCard(asPage)
            SettingsPage.Theme -> {
                SettingsPageHeader(stringResource(R.string.settings_theme_title), onBack)
                ThemePageContent()
            }
            SettingsPage.CategoryDisplay -> {
                SettingsPageHeader(stringResource(R.string.settings_display_title), onBack)
                CategoryDisplayPageContent(categories)
            }
            SettingsPage.CustomCategories -> {
                SettingsPageHeader(stringResource(R.string.settings_custom_title), onBack)
                CustomCategoriesPageContent(categories)
            }
            SettingsPage.Privacy -> {
                SettingsPageHeader(stringResource(R.string.settings_privacy_title), onBack)
                PrivacyPageContent()
            }
            SettingsPage.Advanced -> {
                SettingsPageHeader(stringResource(R.string.settings_advanced_title), onBack)
                AdvancedPageContent(permissions.smsOk)
            }
            SettingsPage.NotificationStyle -> {
                SettingsPageHeader(stringResource(R.string.settings_notif_style_title), onBack)
                NotificationStylePageContent()
            }
            SettingsPage.WhatsNew -> {
                SettingsPageHeader(stringResource(R.string.whats_new_title), onBack)
                WhatsNewPageContent()
            }
            SettingsPage.Help -> {
                SettingsPageHeader(stringResource(R.string.help_title), onBack)
                HelpPageContent()
            }
        }
    }
}
