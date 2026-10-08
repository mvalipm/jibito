package ir.jibito.app.ui.smslist

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import ir.jibito.app.data.sms.SmsSenderLookup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.ui.common.openSupportChat
import ir.jibito.app.ui.common.shareText
import androidx.compose.foundation.layout.width
import ir.jibito.app.domain.Transaction
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import ir.jibito.app.data.review.WrongReadingReport

/** «چه چیزی غلط خونده شده؟» و بعد ارسال گزارش (رقم‌ها پوشیده، به‌علاوه‌ی علت، سرشماره و نسخه‌ی اپ) */
@Composable
internal fun WrongReadingDialog(transaction: Transaction, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.review_share_title)
    var reason by rememberSaveable { mutableStateOf<WrongReadingReport.Reason?>(null) }
    val labels = mapOf(
        WrongReadingReport.Reason.AMOUNT to R.string.report_reason_amount,
        WrongReadingReport.Reason.TYPE to R.string.report_reason_type,
        WrongReadingReport.Reason.MERCHANT_MISSING to R.string.report_reason_merchant_missing,
        WrongReadingReport.Reason.MERCHANT to R.string.report_reason_merchant,
        WrongReadingReport.Reason.SHOULD_BE_TRANSFER to R.string.report_reason_transfer,
        WrongReadingReport.Reason.OTHER to R.string.report_reason_other,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_reason_title), fontWeight = FontWeight.Bold) },
        text = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(Modifier.selectableGroup()) {
                labels.forEach { (r, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(selected = reason == r, role = Role.RadioButton, onClick = { reason = r })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(label))
                    }
                }
            }
            }
        },
        confirmButton = {
            // اصلی: چت پشتیبانی در بله (متن گزارش کپی می‌شود تا کاربر فقط بچسباند و بفرستد)؛ فرعی: هر راه دیگری
            val copiedHint = stringResource(R.string.report_copied_paste)
            Row {
                TextButton(
                    enabled = reason != null,
                    onClick = {
                        shareText(context, reportText(context, transaction, reason), chooserTitle)
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.report_send_other)) }
                TextButton(
                    enabled = reason != null,
                    onClick = {
                        openSupportChat(context, reportText(context, transaction, reason), copiedHint, chooserTitle)
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.report_send_bale), fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.budget_dialog_cancel)) } },
    )
}

private fun reportText(context: Context, transaction: Transaction, reason: WrongReadingReport.Reason?): String {
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    val sender = SmsSenderLookup.sender(context, transaction.smsId)
    return WrongReadingReport.text(transaction, reason, sender, version)
}
