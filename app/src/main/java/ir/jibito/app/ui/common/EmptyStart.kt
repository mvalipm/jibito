package ir.jibito.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.ui.settings.rememberSettingsPermissions
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.ErrorLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * هنوز هیچ تراکنشی ثبت نشده (نه از پیامک، نه دستی)؟
 * null یعنی هنوز از دیتابیس چیزی نیامده.
 */
@Composable
fun rememberNoTransactions(): Boolean? {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val flow = remember { app.container.transactionRepository.observeTransactions().map { it.isEmpty() } }
    val empty by flow.collectAsState(initial = null)
    return empty
}

/** اجازه‌ی خواندن پیامک برای صفحه‌ی خالی: داده شده؟ و دادنش با یک لمس (و خواندن پیامک‌ها همان لحظه) */
class SmsAccess(val granted: Boolean, val allow: () -> Unit)

@Composable
fun rememberSmsAccess(): SmsAccess {
    val context = LocalContext.current
    val repository = (context.applicationContext as JibitoApplication).container.transactionRepository
    val scope = rememberCoroutineScope()
    val permissions = rememberSettingsPermissions(onSmsGranted = {
        scope.launch {
            try {
                repository.syncFromSms()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ErrorLog.record(context, "sync after sms grant", e)
            }
        }
    })
    return SmsAccess(permissions.smsOk, permissions.fixSms)
}

/**
 * صفحه‌ی خالیِ کاربری که هنوز هیچ تراکنشی ندارد: می‌گوید چرا خالی است و قدم بعدی را نشان می‌دهد.
 * بی اجازه‌ی پیامک («فعلاً دستی»): ثبت دستی، یا دادن اجازه تا خرج‌ها خودکار بیایند.
 * با اجازه ولی بی پیامک بانکی: خرج بعدی خودش می‌آید؛ تا آن موقع ثبت دستی.
 * @param card کارت با قاب (برای «خلاصه»)؛ بدون قاب وسط صفحه (برای «تراکنش‌ها»)
 */
@Composable
fun EmptyStart(
    smsGranted: Boolean,
    onAddManual: () -> Unit,
    onAllowSms: () -> Unit,
    modifier: Modifier = Modifier,
    card: Boolean = false,
) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (card) Modifier.clip(shape).background(t.sheet).border(1.dp, t.border, shape).padding(horizontal = 18.dp, vertical = 18.dp)
                else Modifier.padding(horizontal = 30.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mascot(if (card) 72.dp else 110.dp, face = if (smsGranted) MascotFace.HAPPY else MascotFace.UNSURE)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.start_empty_title),
            modifier = Modifier.semantics { heading() },
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (smsGranted) R.string.start_empty_no_bank_sms else R.string.start_empty_no_permission),
            fontSize = 14.sp,
            lineHeight = 24.sp,
            color = t.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        StartButton(stringResource(R.string.start_empty_add), primary = true, onClick = onAddManual)
        if (!smsGranted) {
            Spacer(Modifier.height(8.dp))
            StartButton(stringResource(R.string.start_empty_allow_sms), primary = false, onClick = onAllowSms)
        }
    }
}

@Composable
private fun StartButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val t = JibitoTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(if (primary) t.btnBg else t.chip)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (primary) t.btnFg else MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}
