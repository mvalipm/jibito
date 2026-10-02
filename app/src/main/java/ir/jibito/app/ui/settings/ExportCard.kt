package ir.jibito.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.export.CsvExport
import ir.jibito.app.util.ErrorLog
import ir.jibito.app.util.JalaliMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** «خروجی اکسل»: تراکنش‌های این ماه یا همه، در یک فایل CSV که اکسل و Google Sheets باز می‌کنند. */
@Composable
fun ExportCard(card: @Composable (title: String, content: @Composable () -> Unit) -> Unit) {
    val context = LocalContext.current
    val repository = (context.applicationContext as JibitoApplication).container.transactionRepository
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    // null = همه‌ی تراکنش‌ها
    var pendingMonth by remember { mutableStateOf<JalaliMonth?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val month = pendingMonth
        scope.launch {
            message = try {
                val all = repository.observeTransactions().first()
                val selected = if (month == null) all else all.filter { it.dateMillis >= month.startMillis() && it.dateMillis < month.endMillis() }
                val csv = withContext(Dispatchers.Default) { CsvExport.build(selected, repository.observeCategories().first()) }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)!!.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                context.getString(R.string.export_done, selected.size)
            } catch (e: Exception) {
                ErrorLog.record(context, "csv export", e)
                context.getString(R.string.export_failed)
            }
        }
    }

    card(stringResource(R.string.export_title)) {
        Text(
            stringResource(R.string.export_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    val month = JalaliMonth.current()
                    pendingMonth = month
                    message = null
                    launcher.launch("jibito-%04d-%02d.csv".format(Locale.US, month.year, month.month))
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.export_this_month)) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    pendingMonth = null
                    message = null
                    launcher.launch("jibito-all.csv")
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.export_all)) }
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                ir.jibito.app.util.Jalali.toPersianDigits(it),
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
