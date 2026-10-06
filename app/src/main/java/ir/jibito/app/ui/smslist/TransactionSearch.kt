package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Transaction
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money

/**
 * حالت جست‌وجو (جای عنوان صفحه را می‌گیرد): «برگشت»، کادر متن (کیبورد خودکار باز می‌شود)،
 * یک ردیف چیپ فیلتر (تاریخ، مبلغ، فقط خرج/درآمد، بی‌دسته) و نوار باریک خلاصه‌ی نتیجه.
 * تاریخ و مبلغ در برگه‌ی پایین صفحه انتخاب می‌شوند ([FilterSheet]).
 * @param countFor تعداد نتیجه‌ی یک جست‌وجوی پیشنهادی (پیش از اعمال، روی دکمه‌ی برگه)
 */
@Composable
fun SearchTopBar(
    search: TxSearch,
    onChange: (TxSearch) -> Unit,
    onClose: () -> Unit,
    onlyUncategorized: Boolean,
    onUncategorizedChange: (Boolean) -> Unit,
    results: List<Transaction>?,
    countFor: (TxSearch) -> Int,
) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column {
        Row(
            Modifier.padding(start = 6.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(JibitoIcons.Back, contentDescription = stringResource(R.string.cd_close_search), tint = colors.onBackground)
            }
            OutlinedTextField(
                value = search.text,
                onValueChange = { onChange(search.copy(text = it)) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1) },
                leadingIcon = { Icon(DesignIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = if (search.text.isNotEmpty()) {
                    {
                        IconButton(onClick = { onChange(search.copy(text = "")) }) {
                            Icon(JibitoIcons.Close, contentDescription = stringResource(R.string.cd_clear_text), modifier = Modifier.size(20.dp))
                        }
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = t.btnBg,
                    unfocusedBorderColor = t.border,
                    focusedLeadingIconColor = t.btnBg,
                    cursorColor = t.btnBg,
                    focusedContainerColor = t.sheet,
                    unfocusedContainerColor = t.sheet,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
            )
        }

        // چیپ‌ها: روشن = تیره با ✕
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            item(key = "date") {
                SearchChip(
                    label = if (search.hasDate) dateLabel(search) else stringResource(R.string.search_chip_date),
                    selected = search.hasDate,
                    dropdown = true,
                    onClick = { sheetOpen = true },
                    onClear = { onChange(search.copy(preset = DatePreset.ALL, monthKey = null, day = null)) },
                )
            }
            item(key = "amount") {
                SearchChip(
                    label = if (search.hasAmount) amountLabel(search.minToman, search.maxToman) else stringResource(R.string.search_chip_amount),
                    selected = search.hasAmount,
                    dropdown = true,
                    onClick = { sheetOpen = true },
                    onClear = { onChange(search.copy(minToman = null, maxToman = null)) },
                )
            }
            item(key = "spend") {
                SearchChip(
                    label = stringResource(R.string.search_only_spend),
                    selected = search.flow == FlowType.WITHDRAWAL,
                    onClick = { onChange(search.copy(flow = if (search.flow == FlowType.WITHDRAWAL) null else FlowType.WITHDRAWAL)) },
                )
            }
            item(key = "income") {
                SearchChip(
                    label = stringResource(R.string.search_only_income),
                    selected = search.flow == FlowType.DEPOSIT,
                    onClick = { onChange(search.copy(flow = if (search.flow == FlowType.DEPOSIT) null else FlowType.DEPOSIT)) },
                )
            }
            item(key = "uncat") {
                SearchChip(
                    label = stringResource(R.string.filter_uncategorized),
                    selected = onlyUncategorized,
                    onClick = { onUncategorizedChange(!onlyUncategorized) },
                )
            }
        }

        // خلاصه‌ی نتیجه
        if ((search.isActive || onlyUncategorized) && results != null) {
            val spent = results.filter { it.transaction.type == FlowType.WITHDRAWAL && !it.isFailedPurchase && !it.isSelfTransfer }
                .sumOf { it.transaction.amountRial }
            val income = results.filter { it.transaction.type == FlowType.DEPOSIT && !it.isSelfTransfer }.sumOf { it.transaction.amountRial }
            Row(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(t.sheet)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Jalali.toPersianDigits(stringResource(R.string.search_result_count, results.size)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.onBackground,
                )
                ResultFigure(stringResource(R.string.search_result_spent), amount(Money.compact(spent)))
                ResultFigure(stringResource(R.string.search_result_income), amount(Money.compact(income)))
            }
        }
    }

    if (sheetOpen) {
        FilterSheet(
            initial = search,
            countFor = countFor,
            onApply = {
                onChange(it)
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun ResultFigure(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = JibitoTheme.colors.muted)
        Spacer(Modifier.width(4.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
    }
}

/** چیپ فیلتر: خاموش = حاشیه‌دار، روشن = تیره (و اگر onClear دارد، ✕ برای حذف همان فیلتر) */
@Composable
private fun SearchChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    dropdown: Boolean = false,
    onClear: (() -> Unit)? = null,
) {
    val t = JibitoTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val fg = if (selected) t.onFg else MaterialTheme.colorScheme.onBackground
    Row(
        Modifier
            .height(36.dp)
            .clip(shape)
            .background(if (selected) t.onBg else Color.Transparent)
            .border(1.dp, if (selected) t.onBg else t.border, shape)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = 12.dp, end = if (selected && onClear != null) 4.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        if (selected && onClear != null) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = stringResource(R.string.cd_clear_filter), onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(JibitoIcons.Close, contentDescription = stringResource(R.string.cd_clear_filter), tint = fg.copy(alpha = 0.75f), modifier = Modifier.size(15.dp))
            }
        } else if (dropdown) {
            Spacer(Modifier.width(4.dp))
            Text("▾", color = fg.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun dateLabel(search: TxSearch): String = when (search.preset) {
    DatePreset.ALL -> stringResource(R.string.search_chip_date)
    DatePreset.TODAY -> stringResource(R.string.search_date_today)
    DatePreset.YESTERDAY -> stringResource(R.string.search_date_yesterday)
    DatePreset.WEEK -> stringResource(R.string.search_date_week)
    DatePreset.THIS_MONTH -> stringResource(R.string.search_date_this_month)
    DatePreset.LAST_MONTH -> stringResource(R.string.search_date_last_month)
    DatePreset.CUSTOM -> customLabel(search) ?: stringResource(R.string.search_date_custom)
}

private fun customLabel(search: TxSearch): String? {
    val key = search.monthKey ?: return null
    val m = JalaliMonth(key / 100, key % 100)
    return listOfNotNull(search.day?.let { Jalali.toPersianDigits(it.toString()) }, m.title).joinToString(" ")
}

/** «۱۰۰ هزار تا ۱ میلیون»، «از ۵۰ هزار»، «تا ۲ میلیون» */
@Composable
private fun amountLabel(minToman: Long?, maxToman: Long?): String {
    fun short(toman: Long) = Money.short(toman * 10)
    return when {
        minToman != null && maxToman != null -> stringResource(R.string.search_amount_range, short(minToman), short(maxToman))
        minToman != null -> stringResource(R.string.search_amount_min, short(minToman))
        maxToman != null -> stringResource(R.string.search_amount_max, short(maxToman))
        else -> stringResource(R.string.search_chip_amount)
    }
}

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
private fun FilterSheet(
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
