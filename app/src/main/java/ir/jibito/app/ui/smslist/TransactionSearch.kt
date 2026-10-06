package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
internal fun SearchChip(
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

internal fun customLabel(search: TxSearch): String? {
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
