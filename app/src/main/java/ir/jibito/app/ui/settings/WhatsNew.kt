package ir.jibito.app.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import ir.jibito.app.R
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Changelog
import ir.jibito.app.util.Jalali

/** آخرین نسخه‌ای که «چه چیزی تازه است»ش دیده شده */
class WhatsNewSeen(context: Context) {
    private val prefs = context.getSharedPreferences("whats_new", Context.MODE_PRIVATE)

    val lastSeen: String? get() = prefs.getString(KEY, null)

    fun markSeen(version: String) = prefs.edit { putString(KEY, version) }

    private companion object {
        const val KEY = "last_seen"
    }
}

/** نسخه‌ی نصب‌شده بدون پسوند ساخت آزمایشی («0.52.0-165» ← «0.52.0») */
fun installedVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull().orEmpty().substringBefore('-')

/** نصب تازه (نه به‌روزرسانی): کاربر تازه چیزی برای «چه چیزی تازه است» ندارد */
fun isFreshInstall(context: Context): Boolean = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.firstInstallTime == info.lastUpdateTime
}.getOrDefault(true)

/**
 * بعد از به‌روزرسانی، یک بار: بخش‌های تازه‌ی CHANGELOG از آخرین نسخه‌ی دیده‌شده تا نسخه‌ی فعلی.
 * فهرست خالی یعنی چیزی برای نشان دادن نیست (نصب تازه، یا همین نسخه قبلاً دیده شده) و همان‌جا «دیده‌شده» ثبت می‌شود.
 */
fun pendingWhatsNew(context: Context, seen: WhatsNewSeen): List<Changelog.Release> {
    val current = installedVersion(context)
    if (current.isEmpty()) return emptyList()
    val last = seen.lastSeen
    if (last != null && Changelog.compare(current, last) <= 0) return emptyList()
    val releases = if (isFreshInstall(context)) emptyList() else Changelog.newSince(Changelog.load(context), last, current)
    if (releases.isEmpty()) seen.markSeen(current)
    return releases
}

/** «نسخه‌ی ۰.۵۲.۰» و زیرش هر تغییر یک خط با نقطه */
@Composable
internal fun ReleaseNotes(release: Changelog.Release) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            Jalali.toPersianDigits(stringResource(R.string.whats_new_version, release.version)),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        release.notes.forEach { note ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(6.dp)
                        .background(JibitoTheme.colors.teal, CircleShape),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    Jalali.toPersianDigits(note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** همه‌ی بخش‌ها، هر نسخه در کارت خودش */
@Composable
internal fun WhatsNewList(releases: List<Changelog.Release>) {
    if (releases.isEmpty()) {
        PageCard {
            Text(
                stringResource(R.string.whats_new_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    releases.forEachIndexed { i, release ->
        if (i > 0) Spacer(Modifier.height(12.dp))
        PageCard { ReleaseNotes(release) }
    }
}

/** زیرصفحه‌ی «چه چیزی تازه است» در تنظیمات */
@Composable
internal fun WhatsNewPageContent() {
    val context = LocalContext.current
    val releases = remember { Changelog.load(context) }
    WhatsNewList(releases)
}

/** پنجره‌ی یک‌باره‌ی بعد از به‌روزرسانی */
@Composable
fun WhatsNewDialog(releases: List<Changelog.Release>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                releases.forEachIndexed { i, release ->
                    if (i > 0) Spacer(Modifier.height(16.dp))
                    ReleaseNotes(release)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.whats_new_ok), fontWeight = FontWeight.Bold) }
        },
    )
}
