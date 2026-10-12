package ir.jibito.app.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.domain.Category
import ir.jibito.app.ui.main.LocalBottomBarSpace

/** صفحه‌ی اسکرول‌شونده‌ی تنظیمات با فاصله‌ی درست بالا و پایین (زیر نوار شناور) */
@Composable
internal fun SettingsColumn(scroll: ScrollState, content: @Composable ColumnScope.() -> Unit) {
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
internal fun SettingsPageScreen(
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
