package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.material3.FloatingActionButton
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.JibitoApplication
import ir.jibito.app.domain.Transaction
import ir.jibito.app.data.category.CreateCategoryResult
import ir.jibito.app.domain.TransferSuggestion
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

@Composable
fun SmsListScreen(
    /** فقط خرج‌های بی‌دسته (از «کارهای لازم» در خلاصه) */
    onlyUncategorized: Boolean = false,
    onClearFilter: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val viewModel: TransactionsViewModel = viewModel(
        factory = TransactionsViewModel.factory(app.container.transactionRepository)
    )
    // null یعنی «هنوز چیزی از دیتابیس نیامده»
    val messages by viewModel.transactions.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val transferSuggestions by viewModel.transferSuggestions.collectAsState()
    val bankBalances by viewModel.bankBalances.collectAsState()
    // تنظیم «نمایش دسته‌ها»: چند لایه، و کدام دسته‌های اصلی پنهان‌اند
    val displayDepth by app.container.categoryDisplay.depth.collectAsState()
    val hiddenRoots by app.container.categoryDisplay.hiddenRoots.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val colors = MaterialTheme.colorScheme

    var addingManual by rememberSaveable { mutableStateOf(false) }
    val bottomSpace = LocalBottomBarSpace.current

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
            Text(
                text = stringResource(R.string.list_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
            )
            messages?.takeIf { it.isNotEmpty() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = Jalali.toPersianDigits(stringResource(R.string.list_count, it.size)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (isSyncing && !messages.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.list_syncing),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        if (onlyUncategorized) {
            Text(
                stringResource(R.string.tx_filter_uncategorized),
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.primary.copy(alpha = 0.14f))
                    .clickable(onClick = onClearFilter)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                color = colors.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        val list = if (onlyUncategorized) {
            messages?.filter {
                it.categoryId == null && !it.isSelfTransfer && !it.isFailedPurchase &&
                    it.transaction.type == FlowType.WITHDRAWAL
            }
        } else {
            messages
        }
        when {
            list == null || (list.isEmpty() && isSyncing) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.list_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            list.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp + LocalBottomBarSpace.current),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // «چقد دارم؟»: آخرین مانده‌ی هر بانک
                if (bankBalances.isNotEmpty()) {
                    item(key = "balances") { BankBalancesRow(bankBalances) }
                }
                // پیشنهاد «انتقال بین حساب‌های خودم»: یکی‌یکی، بالای فهرست
                transferSuggestions.firstOrNull()?.let { suggestion ->
                    item(key = "transfer-suggestion") {
                        TransferSuggestionCard(
                            suggestion = suggestion,
                            total = transferSuggestions.size,
                            onYes = { viewModel.confirmTransfer(suggestion) },
                            onNo = { viewModel.rejectTransfer(suggestion) },
                        )
                    }
                }
                items(list, key = { it.id }) { sms -> SmsCard(sms, onClick = { selectedId = sms.id }) }
            }
        }
    }


    // «+» ثبت دستی: پایین صفحه، بالای نوار شناور (روی چیزی نمی‌افتد؛ فهرست جای خالی دارد)
    FloatingActionButton(
        onClick = { addingManual = true },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 20.dp, bottom = bottomSpace + 12.dp),
        containerColor = colors.primary,
        contentColor = colors.onPrimary,
        shape = RoundedCornerShape(20.dp),
    ) {
        Text("+", fontSize = 28.sp, fontWeight = FontWeight.Black)
    }
    }

    // برگه‌ی انتخاب دسته (از پایین صفحه)
    val selected = messages?.firstOrNull { it.id == selectedId }
    if (selected != null) {
        var frequent by remember(selected.id) { mutableStateOf<List<Long>>(emptyList()) }
        LaunchedEffect(selected.id) {
            viewModel.loadQuickCategories(selected.transaction.type.code) { frequent = it }
        }
        CategoryPickerSheet(
            transaction = selected,
            categories = categories,
            frequentIds = frequent,
            depth = displayDepth,
            hiddenRoots = hiddenRoots,
            onPick = { categoryId ->
                viewModel.setCategory(selected, categoryId)
                selectedId = null
            },
            onSelfTransfer = { isTransfer ->
                viewModel.setSelfTransfer(selected.id, isTransfer)
                selectedId = null
            },
            onDelete = if (selected.isManual) {
                {
                    viewModel.deleteManual(selected.id)
                    selectedId = null
                }
            } else {
                null
            },
            onCreate = { name, parentId, icon, onResult ->
                viewModel.createCategoryAndPick(selected, name, parentId, icon) { result ->
                    if (result is CreateCategoryResult.Created) selectedId = null
                    onResult(result)
                }
            },
            onDismiss = { selectedId = null },
        )
    }

    if (addingManual) {
        ManualEntrySheet(
            categories = categories,
            depth = displayDepth,
            hiddenRoots = hiddenRoots,
            loadQuick = viewModel::loadQuickCategories,
            onSave = { type, amountRial, categoryId, note, date, openPicker ->
                viewModel.addManual(type, amountRial, categoryId, note, date) { newId ->
                    addingManual = false
                    // «دسته‌ی دیگر…»: برگه‌ی کامل دسته‌ها برای همین تراکنش تازه
                    if (openPicker) selectedId = newId
                }
            },
            onDismiss = { addingManual = false },
        )
    }
}

private val DepositGreen = Color(0xFF1E9E6A)
private val TransferBlue = Color(0xFF3A6FD8)

/** کارت پیشنهاد: «این برداشت و این واریز، انتقال بین حساب‌های خودت بود؟» */
@Composable
private fun TransferSuggestionCard(
    suggestion: TransferSuggestion,
    total: Int,
    onYes: () -> Unit,
    onNo: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val w = suggestion.withdrawal
    val d = suggestion.deposit
    val unknown = stringResource(R.string.bank_unknown)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TransferBlue.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⇄", color = TransferBlue, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.transfer_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = colors.onSurface,
                )
                if (total > 1) {
                    Text(
                        Jalali.toPersianDigits(stringResource(R.string.transfer_count, total)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TransferBlue,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.transfer_explain),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                Money.toman(w.transaction.amountRial),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = TransferBlue,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.transfer_withdrawal_line, listOfNotNull(w.bank?.name ?: unknown, w.merchant).joinToString(" ← ")) +
                    "  ·  " + Jalali.format(w.dateMillis),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
            )
            Text(
                stringResource(R.string.transfer_deposit_line, d.bank?.name ?: unknown) + "  ·  " + Jalali.format(d.dateMillis),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onYes,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TransferBlue),
                ) { Text(stringResource(R.string.transfer_yes), fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = onNo, shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.transfer_no))
                }
            }
        }
    }
}

@Composable
private fun SmsCard(sms: Transaction, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val t = sms.transaction
    val bankName = if (sms.isManual) stringResource(R.string.tx_manual_source) else sms.bank?.name ?: stringResource(R.string.bank_unknown)
    val isDeposit = t.type == FlowType.DEPOSIT
    val failed = sms.isFailedPurchase
    val selfTransfer = sms.isSelfTransfer && !failed
    val accent = when {
        failed -> colors.outline
        selfTransfer -> TransferBlue
        isDeposit -> DepositGreen
        else -> colors.primary
    }
    val title = when {
        failed -> stringResource(R.string.tx_failed_purchase)
        selfTransfer -> stringResource(R.string.tx_self_transfer)
        sms.isManual -> sms.merchant ?: stringResource(if (isDeposit) R.string.tx_manual_income else R.string.tx_manual_expense)
        sms.merchant != null -> stringResource(R.string.tx_purchase_from, sms.merchant)
        isDeposit -> stringResource(R.string.tx_deposit)
        else -> stringResource(R.string.tx_withdrawal)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(accent.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            failed -> "↺"
                            selfTransfer -> "⇄"
                            isDeposit -> "↓"
                            else -> "↑"
                        },
                        color = accent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Text(
                        text = if ((failed || selfTransfer) && sms.merchant != null) "${sms.merchant} · ${bankName}" else bankName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    // علامت، عدد و «تومان» سه تکه‌ی جدا هستند تا جهت‌نویسی راست‌به‌چپ جای علامت را جابه‌جا نکند.
                    // در Row راست‌به‌چپ، اولین تکه سمت راست می‌نشیند: «− ۱۲۵٬۰۰۰ تومان»
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!failed) {
                            Text(
                                text = if (isDeposit) "+" else "−",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = accent,
                            )
                            Spacer(Modifier.size(2.dp))
                        }
                        Text(
                            text = Money.tomanNumber(t.amountRial),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = accent,
                            textDecoration = if (failed) TextDecoration.LineThrough else null,
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = stringResource(R.string.unit_toman),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                    }
                    Text(
                        text = Jalali.format(sms.dateMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (failed) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.tx_refunded),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            if (selfTransfer) {
                Spacer(Modifier.height(10.dp))
                AssistChip(
                    onClick = onClick,
                    label = { Text(stringResource(R.string.tx_self_transfer_chip), fontWeight = FontWeight.Bold) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = TransferBlue.copy(alpha = 0.14f),
                        labelColor = TransferBlue,
                    ),
                    border = null,
                )
            } else if (!failed && sms.categoryName != null) {
                Spacer(Modifier.height(10.dp))
                AssistChip(
                    onClick = onClick,
                    label = {
                        val name = listOfNotNull(sms.categoryIcon, sms.categoryName).joinToString(" ")
                        Text(
                            if (sms.isAutoCategorized) stringResource(R.string.tx_auto_category, name) else name,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = colors.primaryContainer,
                        labelColor = colors.onPrimaryContainer,
                    ),
                    border = null,
                )
            } else if (!failed && sms.suggestedCategory != null) {
                Spacer(Modifier.height(10.dp))
                AssistChip(
                    onClick = onClick,
                    label = { Text(stringResource(R.string.tx_suggested_category, sms.suggestedCategory)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = colors.secondaryContainer,
                        labelColor = colors.onSecondaryContainer,
                    ),
                    border = null,
                )
            } else if (!failed) {
                Spacer(Modifier.height(10.dp))
                AssistChip(
                    onClick = onClick,
                    label = { Text(stringResource(R.string.tx_add_category)) },
                )
            }
            t.balanceRial?.takeIf { !failed }?.let { balance ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.tx_balance, Money.toman(balance)),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
