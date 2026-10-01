package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import ir.jibito.app.R
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/**
 * برگه‌ای که از پایین صفحه باز می‌شود: همه‌ی دسته‌ها برای انتخاب، + متن پیامک.
 * دسته‌ی پیشنهادی (از مقصد خرید) اول و با برچسب «پیشنهاد» می‌آید.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryPickerSheet(
    transaction: Transaction,
    categories: List<Category>,
    onPick: (categoryId: Long?) -> Unit,
    /** علامت زدن/برداشتن «انتقال بین حساب‌های خودم» */
    onSelfTransfer: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSms by rememberSaveable(transaction.id) { mutableStateOf(false) }

    // برداشت ← دسته‌های خرج؛ واریز ← دسته‌های درآمد
    val isDeposit = transaction.transaction.type == FlowType.DEPOSIT
    val matching = categories.filter { it.flowType == transaction.transaction.type.code }
    // پیشنهادی اول، بقیه به ترتیب خودشان
    val suggested = matching.firstOrNull { it.name == transaction.suggestedCategory }
    val ordered = listOfNotNull(suggested) + matching.filter { it.id != suggested?.id }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        // برگه در لایه‌ی جدایی کشیده می‌شود؛ جهت راست‌به‌چپ را دوباره تنظیم می‌کنیم
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                // خلاصه‌ی تراکنش
                val t = transaction.transaction
                val sign = if (t.type == FlowType.DEPOSIT) "+" else "−"
                Text(
                    text = stringResource(if (isDeposit) R.string.sheet_title_income else R.string.sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = listOfNotNull(
                        "$sign ${Money.toman(t.amountRial)}",
                        transaction.merchant ?: transaction.bank?.name,
                        Jalali.format(transaction.dateMillis),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                // انتقال بین حساب‌های خودم: جدا از دسته‌ها، چون نه خرج است نه درآمد
                FilterChip(
                    selected = transaction.isSelfTransfer,
                    onClick = { onSelfTransfer(!transaction.isSelfTransfer) },
                    label = { Text(stringResource(R.string.sheet_self_transfer), fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(14.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF3A6FD8),
                        selectedLabelColor = Color.White,
                    ),
                )
                Text(
                    text = stringResource(R.string.sheet_self_transfer_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                // دسته‌ها
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ordered.forEach { category ->
                        val isSelected = category.id == transaction.categoryId
                        val dot = category.colorHex.toColorOrNull() ?: colors.primary
                        FilterChip(
                            selected = isSelected,
                            onClick = { onPick(category.id) },
                            label = {
                                Text(
                                    text = listOfNotNull(category.icon, category.name).joinToString(" "),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            leadingIcon = if (category.id == suggested?.id && !isSelected) {
                                {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .background(dot, CircleShape)
                                    )
                                }
                            } else {
                                null
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = colors.onPrimary,
                            ),
                        )
                    }
                }

                if (suggested != null && suggested.id != transaction.categoryId) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "● " + stringResource(R.string.sheet_suggested),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (transaction.categoryId != null) {
                        TextButton(onClick = { onPick(null) }) {
                            Text(stringResource(R.string.sheet_clear))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showSms = !showSms }) {
                        Text(stringResource(if (showSms) R.string.sheet_hide_sms else R.string.sheet_show_sms))
                    }
                }

                if (showSms) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = transaction.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun String?.toColorOrNull(): Color? = try {
    this?.let { Color(android.graphics.Color.parseColor(it)) }
} catch (e: IllegalArgumentException) {
    null
}
