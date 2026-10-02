package ir.jibito.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.repository.RecurringRepository
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import kotlinx.coroutines.launch

/** «پرداخت‌های ماهانه»: اجاره، قسط، شهریه...؛ روز موعدش یادآوری می‌شود. */
@Composable
fun RecurringCard(card: @Composable (title: String, content: @Composable () -> Unit) -> Unit) {
    val context = LocalContext.current
    val repository = (context.applicationContext as JibitoApplication).container.recurringRepository
    val itemsFlow = remember { repository.observeAll() }
    val items by itemsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    var adding by remember { mutableStateOf(false) }

    card(stringResource(R.string.recurring_title)) {
        Text(
            stringResource(R.string.recurring_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        items.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    Text(
                        Jalali.toPersianDigits(stringResource(R.string.recurring_row, Money.toman(p.amountRial), p.dayOfMonth)),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { scope.launch { repository.delete(p.id) } }) {
                    Text(stringResource(R.string.settings_custom_delete), color = colors.error)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { adding = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.recurring_add)) }
    }

    if (adding) {
        var title by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var day by remember { mutableStateOf("") }
        // ورودی با رقم فارسی هم قبول شود
        val amountToman = toLatinDigits(amount).filter { it.isDigit() }.take(13).toLongOrNull()
        val dayNumber = toLatinDigits(day).filter { it.isDigit() }.take(2).toIntOrNull()?.takeIf { it in 1..31 }
        val valid = title.isNotBlank() && amountToman != null && amountToman > 0 && dayNumber != null
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.recurring_add), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(RecurringRepository.MAX_TITLE) },
                        label = { Text(stringResource(R.string.recurring_field_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text(stringResource(R.string.recurring_field_amount)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = amountToman?.let { { Text(Money.toman(it * 10)) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = day,
                        onValueChange = { day = it },
                        label = { Text(stringResource(R.string.recurring_field_day)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        scope.launch { repository.add(title, amountToman!! * 10, dayNumber!!) }
                        adding = false
                    },
                ) { Text(stringResource(R.string.recurring_save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { adding = false }) { Text(stringResource(R.string.budget_dialog_cancel)) }
            },
        )
    }
}

private fun toLatinDigits(text: String): String = buildString {
    for (ch in text) {
        append(
            when (ch) {
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                else -> ch
            }
        )
    }
}
