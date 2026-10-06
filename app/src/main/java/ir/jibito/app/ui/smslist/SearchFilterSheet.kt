package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth

/** بازه‌های آماده‌ی مبلغ (تومان) */
private val AMOUNT_PRESETS = listOf(
    Triple(R.string.search_amount_under, null, 99_999L),
    Triple(R.string.search_amount_mid, 100_000L, 1_000_000L),
    Triple(R.string.search_amount_over, 1_000_000L, null),
)

/**
 * برگه‌ی «کِی؟ / مبلغ»: دکمه‌های بزرگ بازه‌ی تاریخ، دو کادر مبلغ و بازه‌های آماده؛
 * دکمه‌ی پایین تعداد نتیجه را پیش از اعمال نشان می‌دهد.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FilterSheet(
    initial: TxSearch,
    countFor: (TxSearch) -> Int,
    onApply: (TxSearch) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf(initial) }
    var pickingMonth by rememberSaveable { mutableStateOf(false) }
    val count = remember(draft) { countFor(draft) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = t.sheet,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.search_sheet_when),
                        modifier = Modifier.weight(1f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.onSurface,
                    )
                    if (draft.hasDate || draft.hasAmount) {
                        TextButton(onClick = {
                            draft = draft.copy(preset = DatePreset.ALL, monthKey = null, day = null, minToman = null, maxToman = null)
                        }) { Text(stringResource(R.string.search_clear), color = t.btnBg, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val options = listOf(
                    DatePreset.ALL to stringResource(R.string.search_date_all),
                    DatePreset.TODAY to stringResource(R.string.search_date_today),
                    DatePreset.YESTERDAY to stringResource(R.string.search_date_yesterday),
                    DatePreset.WEEK to stringResource(R.string.search_date_week),
                    DatePreset.THIS_MONTH to stringResource(R.string.search_date_this_month),
                    DatePreset.LAST_MONTH to stringResource(R.string.search_date_last_month),
                    DatePreset.CUSTOM to (
                        customLabel(draft).takeIf { draft.preset == DatePreset.CUSTOM } ?: stringResource(R.string.search_date_custom_long)
                        ),
                )
                options.chunked(2).forEach { row ->
                    Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (preset, label) ->
                            BigOption(label, draft.preset == preset, Modifier.weight(1f)) {
                                if (preset == DatePreset.CUSTOM) {
                                    pickingMonth = true
                                } else {
                                    draft = draft.copy(preset = preset, monthKey = null, day = null)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                Text(
                    stringResource(R.string.search_sheet_amount),
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.muted,
                )
                Row {
                    AmountField(
                        value = draft.minToman,
                        label = stringResource(R.string.search_amount_from_short),
                        onChange = { draft = draft.copy(minToman = it) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    AmountField(
                        value = draft.maxToman,
                        label = stringResource(R.string.search_amount_to_short),
                        onChange = { draft = draft.copy(maxToman = it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    AMOUNT_PRESETS.forEach { (label, min, max) ->
                        val on = draft.minToman == min && draft.maxToman == max
                        SearchChip(
                            label = stringResource(label),
                            selected = on,
                            onClick = { draft = if (on) draft.copy(minToman = null, maxToman = null) else draft.copy(minToman = min, maxToman = max) },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .alpha(if (count > 0) 1f else 0.5f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(t.btnBg)
                        .clickable(enabled = count > 0) { onApply(draft) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (count > 0) Jalali.toPersianDigits(stringResource(R.string.search_sheet_show, count)) else stringResource(R.string.search_sheet_none),
                        color = t.btnFg,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }

    if (pickingMonth) {
        MonthDayDialog(
            initialKey = draft.monthKey ?: JalaliMonth.current().key,
            initialDay = draft.day,
            onPick = { key, day ->
                draft = draft.copy(preset = DatePreset.CUSTOM, monthKey = key, day = day)
                pickingMonth = false
            },
            onDismiss = { pickingMonth = false },
        )
    }
}

/** دکمه‌ی بزرگ انتخاب بازه‌ی تاریخ در برگه (برای انگشت شست) */
@Composable
private fun BigOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = JibitoTheme.colors
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) t.onBg else t.chip)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) t.onFg else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** کادر مبلغ (تومان): رقم فارسی و لاتین هر دو قبول است؛ با بازه‌های آماده هم به‌روز می‌شود */
@Composable
private fun AmountField(value: Long?, label: String, onChange: (Long?) -> Unit, modifier: Modifier) {
    val t = JibitoTheme.colors
    OutlinedTextField(
        value = value?.let { Jalali.toPersianDigits(it.toString()) }.orEmpty(),
        onValueChange = { v -> onChange(TxSearch.toLatinDigits(v).filter { it.isDigit() }.take(12).toLongOrNull()) },
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = t.btnBg,
            unfocusedBorderColor = t.border,
            focusedLabelColor = t.btnBg,
            cursorColor = t.btnBg,
        ),
        modifier = modifier,
    )
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val t = JibitoTheme.colors
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) t.onBg else Color.Transparent)
            .border(1.dp, if (selected) t.onBg else t.border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (selected) t.onFg else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
}

/** انتخاب یک ماه شمسی (۱۸ ماه اخیر) و اگر بخواهد یک روز از آن */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthDayDialog(initialKey: Int, initialDay: Int?, onPick: (Int, Int?) -> Unit, onDismiss: () -> Unit) {
    var key by rememberSaveable { mutableIntStateOf(initialKey) }
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
