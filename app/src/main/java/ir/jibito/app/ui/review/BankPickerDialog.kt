package ir.jibito.app.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import ir.jibito.app.data.bank.BankDirectory
import androidx.compose.material3.Icon
import ir.jibito.app.ui.theme.JibitoIcons

/**
 * انتخاب بانک یا موسسه: جست‌وجو، «+ افزودن موسسه‌ی جدید» (مثلاً «کارگزاری مفید»)، موسسه‌های کاربر، بانک‌ها، و «سایر».
 */
@Composable
internal fun BankPickerDialog(
    senderHint: String,
    onAdd: (String) -> Int?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val options = (BankDirectory.custom.sortedBy { it.name } + BankDirectory.banks.sortedBy { it.name } + BankDirectory.OTHER)
        .filter { q.isEmpty() || it.name.contains(q) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.review_which_bank), fontWeight = FontWeight.Bold) },
        text = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column {
                    if (adding) {
                        // افزودن موسسه‌ی تازه
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it.take(40) },
                            singleLine = true,
                            label = { Text(stringResource(R.string.institution_name)) },
                            placeholder = { Text(stringResource(R.string.institution_name_hint, senderHint)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Button(
                                onClick = { onAdd(newName)?.let(onPick) },
                                enabled = newName.isNotBlank(),
                                shape = RoundedCornerShape(14.dp),
                            ) { Text(stringResource(R.string.institution_save), fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { adding = false }) { Text(stringResource(R.string.budget_dialog_cancel)) }
                        }
                    } else {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.institution_search_hint)) },
                            leadingIcon = { Icon(JibitoIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.institution_add),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    newName = q
                                    adding = true
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                        )
                        LazyColumn(Modifier.height(320.dp)) {
                            items(options, key = { it.id }) { bank ->
                                Text(
                                    bank.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(bank.id) }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (bank.id >= 1000) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.budget_dialog_cancel)) } },
    )
}
