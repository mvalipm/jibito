package ir.jibito.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.util.ErrorLog
import ir.jibito.app.util.Jalali

/** «گزارش خطا»: خطاهای ثبت‌شده روی همین گوشی؛ ارسال فقط با انتخاب خود کاربر. */
@Composable
fun ErrorLogCard(card: @Composable (title: String, content: @Composable () -> Unit) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    // بعد از «پاک کردن» دوباره خوانده شود
    var version by remember { mutableIntStateOf(0) }
    val count = remember(version) { ErrorLog.count(context) }
    val lastAt = remember(version) { ErrorLog.lastAt(context) }

    card(stringResource(R.string.errorlog_title)) {
        Text(
            if (count == 0) {
                stringResource(R.string.errorlog_empty)
            } else {
                Jalali.toPersianDigits(
                    stringResource(R.string.errorlog_summary, count, lastAt?.let { Jalali.format(it) } ?: "—")
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        if (count > 0) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.errorlog_subject))
                            .putExtra(Intent.EXTRA_TEXT, ErrorLog.report(context))
                        context.startActivity(Intent.createChooser(send, context.getString(R.string.errorlog_send)))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.errorlog_send)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    ErrorLog.clear(context)
                    version++
                }) { Text(stringResource(R.string.errorlog_clear), color = colors.error) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.errorlog_privacy),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
