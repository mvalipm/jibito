package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
import java.util.Calendar
import androidx.compose.material3.Icon
import ir.jibito.app.ui.theme.JibitoIcons
import androidx.compose.material3.IconButton

/** پنل جست‌وجو (زیر عنوان صفحه‌ی تراکنش‌ها) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchPanel(
    search: TxSearch,
    onChange: (TxSearch) -> Unit,
    results: List<Transaction>?,
) {
    val colors = MaterialTheme.colorScheme
    var pickingMonth by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = search.text,
            onValueChange = { onChange(search.copy(text = it)) },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(JibitoIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = if (search.text.isNotEmpty()) {
                {
                    IconButton(onClick = { onChange(search.copy(text = "")) }) {
                        Icon(JibitoIcons.Close, contentDescription = stringResource(R.string.cd_clear_text), modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                null
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        // تاریخ
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                DatePreset.ALL to R.string.search_date_all,
                DatePreset.TODAY to R.string.search_date_today,
                DatePreset.YESTERDAY to R.string.search_date_yesterday,
                DatePreset.WEEK to R.string.search_date_week,
                DatePreset.THIS_MONTH to R.string.search_date_this_month,
                DatePreset.LAST_MONTH to R.string.search_date_last_month,
            ).forEach { (preset, label) ->
                Pill(stringResource(label), search.preset == preset) { onChange(search.copy(preset = preset, monthKey = null, day = null)) }
            }
            val customLabel = if (search.preset == DatePreset.CUSTOM && search.monthKey != null) {
                val m = JalaliMonth(search.monthKey / 100, search.monthKey % 100)
                listOfNotNull(search.day?.let { Jalali.toPersianDigits(it.toString()) }, m.title).joinToString(" ")
            } else {
                stringResource(R.string.search_date_custom)
            }
            Pill(customLabel, search.preset == DatePreset.CUSTOM) { pickingMonth = true }
        }
        Spacer(Modifier.height(8.dp))

        // مبلغ از … تا …
        Row(verticalAlignment = Alignment.CenterVertically) {
            AmountField(
                value = search.minToman,
                label = stringResource(R.string.search_amount_from),
                onChange = { onChange(search.copy(minToman = it)) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(8.dp))
            AmountField(
                value = search.maxToman,
                label = stringResource(R.string.search_amount_to),
                onChange = { onChange(search.copy(maxToman = it)) },
                modifier = Modifier.weight(1f),
            )
        }

        // نتیجه
        if (search.isActive && results != null) {
            val spent = results.filter { it.transaction.type == FlowType.WITHDRAWAL && !it.isFailedPurchase }.sumOf { it.transaction.amountRial }
            val income = results.filter { it.transaction.type == FlowType.DEPOSIT }.sumOf { it.transaction.amountRial }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Jalali.toPersianDigits(
                        stringResource(R.string.search_result, results.size, Money.compact(spent), Money.compact(income))
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
                TextButton(onClick = { onChange(TxSearch()) }) { Text(stringResource(R.string.search_clear)) }
            }
        }
    }

    if (pickingMonth) {
        MonthDayDialog(
            initialKey = search.monthKey ?: JalaliMonth.current().key,
            initialDay = search.day,
            onPick = { key, day ->
                onChange(search.copy(preset = DatePreset.CUSTOM, monthKey = key, day = day))
                pickingMonth = false
            },
            onDismiss = { pickingMonth = false },
        )
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.primary else Color.Transparent)
            .border(1.dp, if (selected) colors.primary else colors.outlineVariant, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (selected) colors.onPrimary else colors.onSurface,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
private fun AmountField(value: Long?, label: String, onChange: (Long?) -> Unit, modifier: Modifier) {
    var text by rememberSaveable(label) { mutableStateOf(value?.toString().orEmpty()) }
    if (value == null && text.isNotEmpty() && TxSearch.toLatinDigits(text).filter { it.isDigit() }.isEmpty()) text = ""
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v
            onChange(TxSearch.toLatinDigits(v).filter { it.isDigit() }.take(12).toLongOrNull())
        },
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    )
}

/** انتخاب یک ماه شمسی (۱۸ ماه اخیر) و اگر بخواهد یک روز از آن */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthDayDialog(initialKey: Int, initialDay: Int?, onPick: (Int, Int?) -> Unit, onDismiss: () -> Unit) {
    var key by rememberSaveable { mutableStateOf(initialKey) }
    var dayText by rememberSaveable { mutableStateOf(initialDay?.toString().orEmpty()) }
    val months = (0 until 18).map { JalaliMonth.current().plus(-it) }
    val day = TxSearch.toLatinDigits(dayText).filter { it.isDigit() }.toIntOrNull()?.takeIf { it in 1..31 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_pick_month), fontWeight = FontWeight.Bold) },
        text = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        months.forEach { m -> Pill(m.title, m.key == key) { key = m.key } }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = dayText,
                        onValueChange = { dayText = it.take(2) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.search_day_optional)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(key, day) }) { Text(stringResource(R.string.search_apply), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.budget_dialog_cancel)) } },
    )
}
