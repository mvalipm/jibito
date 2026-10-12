package ir.jibito.app.ui.summary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import ir.jibito.app.R
import ir.jibito.app.util.Money

/** پنجره‌ی تعیین بودجه (یک دسته، یا کل ماه). مبلغ به تومان وارد و به ریال ذخیره می‌شود. */
@Composable
internal fun BudgetDialog(
    key: String,
    title: String,
    hint: String,
    initialRial: Long?,
    /** پیشنهاد (مثلاً جمع بودجه‌ی دسته‌ها)؛ null یعنی نشان نده */
    suggestionRial: Long?,
    /** میانگین خرج ماه‌های تمام‌شده (فقط بودجه‌ی کل)؛ null یعنی پیشنهادی از روی خرج نشان نده */
    averageSpendRial: Long? = null,
    onSave: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = initialRial?.let { (it / 10).toString() }.orEmpty()
    var text by rememberSaveable(key) { mutableStateOf(initial) }
    val toman = text.toLatinDigits().filter { it in '0'..'9' }.take(12).toLongOrNull()
    val averageSuggestion = averageSpendRial?.let { suggestedBudgetRial(it) }?.takeIf { it != initialRial }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text(stringResource(R.string.unit_toman)) },
                    supportingText = {
                        toman?.let { Text(Money.toman(it * 10)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                suggestionRial?.let { sum ->
                    SuggestionRow(stringResource(R.string.overall_dialog_sum, Money.toman(sum))) { text = (sum / 10).toString() }
                }
                averageSuggestion?.let { average ->
                    SuggestionRow(stringResource(R.string.overall_dialog_average, Money.toman(average))) { text = (average / 10).toString() }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(toman?.let { it * 10 }) },
                enabled = toman != null && toman > 0,
            ) { Text(stringResource(R.string.budget_dialog_save)) }
        },
        dismissButton = {
            Row {
                if (initialRial != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text(stringResource(R.string.budget_dialog_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.budget_dialog_cancel)) }
            }
        },
    )
}

/** یک پیشنهاد زیر خانه‌ی مبلغ با «همین رو بذار» */
@Composable
private fun SuggestionRow(label: String, onUse: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onUse) { Text(stringResource(R.string.overall_dialog_use_sum)) }
    }
}

internal fun String.toLatinDigits(): String = buildString {
    for (ch in this@toLatinDigits) {
        append(
            when (ch) {
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                else -> ch
            }
        )
    }
}

internal fun String?.toColorOrNull(): Color? = try {
    this?.let { Color(it.toColorInt()) }
} catch (e: IllegalArgumentException) {
    null
}
