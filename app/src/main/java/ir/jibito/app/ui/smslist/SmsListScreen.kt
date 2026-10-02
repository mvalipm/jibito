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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import ir.jibito.app.data.repository.UndoSnapshot
import ir.jibito.app.domain.CategoryTree
import ir.jibito.app.ui.common.rememberHaptics
import ir.jibito.app.ui.theme.JibitoIcons
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalFoundationApi::class)
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
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var search by remember { mutableStateOf(TxSearch()) }
    val bottomSpace = LocalBottomBarSpace.current

    // «برگردان» بعد از دسته‌بندی یا حذف، و لرزش کوتاه موقع تأیید
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val undoLabel = stringResource(R.string.undo)
    val categorizedMessage = stringResource(R.string.undo_categorized)
    val uncategorizedMessage = stringResource(R.string.undo_uncategorized)
    val deletedMessage = stringResource(R.string.undo_deleted)
    fun offerUndo(message: String, snapshot: UndoSnapshot) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                haptics.tick()
                viewModel.undo(snapshot)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.list_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = colors.onBackground,
                )
                // جست‌وجو (تاریخ، مبلغ، اسم)
                IconButton(
                    onClick = {
                        if (showSearch) search = TxSearch()
                        showSearch = !showSearch
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (search.isActive) colors.primary.copy(alpha = 0.15f) else colors.surface),
                ) {
                    Icon(
                        if (showSearch) JibitoIcons.Close else JibitoIcons.Search,
                        contentDescription = stringResource(if (showSearch) R.string.cd_close_search else R.string.cd_search),
                        tint = colors.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
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
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.primary.copy(alpha = 0.14f))
                    .clickable(onClickLabel = stringResource(R.string.cd_clear_filter), onClick = onClearFilter)
                    .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.tx_filter_uncategorized),
                    color = colors.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(6.dp))
                Icon(JibitoIcons.Close, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            }
        }
        val base = if (onlyUncategorized) {
            messages?.filter {
                it.categoryId == null && !it.isSelfTransfer && !it.isFailedPurchase &&
                    it.transaction.type == FlowType.WITHDRAWAL
            }
        } else {
            messages
        }
        val range = search.range()
        val list = if (search.isActive) base?.filter { search.matches(it, range) } else base
        // دسته‌هایی مثل پس‌انداز در جمع خرج روز حساب نمی‌شوند (مثل صفحه‌ی خلاصه)
        val nonSpendIds = remember(categories) {
            val byId = categories.associateBy { it.id }
            categories.filter { !it.countsAsSpend || !CategoryTree.rootOf(it, byId).countsAsSpend }.map { it.id }.toSet()
        }
        val groups = remember(list, nonSpendIds) { list?.let { groupByDay(it, nonSpendIds) } ?: emptyList() }
        if (showSearch) {
            SearchPanel(search = search, onChange = { search = it }, results = list)
            Spacer(Modifier.height(6.dp))
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
            ) {
                // «چقد دارم؟»: آخرین مانده‌ی هر بانک
                if (bankBalances.isNotEmpty() && !search.isActive) {
                    item(key = "balances") { Box(Modifier.padding(bottom = 10.dp)) { BankBalancesRow(bankBalances) } }
                }
                // پیشنهاد «انتقال بین حساب‌های خودم»: یکی‌یکی، بالای فهرست
                transferSuggestions.firstOrNull()?.takeIf { !search.isActive }?.let { suggestion ->
                    item(key = "transfer-suggestion") {
                        Box(Modifier.padding(bottom = 4.dp)) {
                            TransferSuggestionCard(
                                suggestion = suggestion,
                                total = transferSuggestions.size,
                                onYes = {
                                    haptics.confirm()
                                    viewModel.confirmTransfer(suggestion)
                                },
                                onNo = {
                                    haptics.reject()
                                    viewModel.rejectTransfer(suggestion)
                                },
                            )
                        }
                    }
                }
                // روزبه‌روز: سرتیتر چسبان «امروز · ۴۵۰ هزار خرج» و تراکنش‌های آن روز در یک سطح
                groups.forEach { group ->
                    stickyHeader(key = "day-${group.dayStartMillis}") { DayHeader(group) }
                    itemsIndexed(group.items, key = { _, sms -> sms.id }) { index, sms ->
                        TransactionRow(
                            sms = sms,
                            position = groupPosition(index, group.items.size),
                            onClick = { selectedId = sms.id },
                        )
                    }
                }
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
        Icon(JibitoIcons.Plus, contentDescription = stringResource(R.string.cd_add_manual), modifier = Modifier.size(26.dp))
    }

    // جای «برگردان»: کنار دکمه‌ی «+»، بالای نوار شناور
    SnackbarHost(
        hostState = snackbar,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .padding(start = 12.dp, end = 20.dp + 56.dp + 8.dp, bottom = bottomSpace + 12.dp),
    ) { data -> Snackbar(data, shape = RoundedCornerShape(16.dp)) }
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
                haptics.confirm()
                val name = categories.firstOrNull { it.id == categoryId }?.name
                viewModel.setCategory(selected, categoryId) { before ->
                    offerUndo(if (name != null) categorizedMessage.format(name) else uncategorizedMessage, before)
                }
                selectedId = null
            },
            onSelfTransfer = { isTransfer ->
                haptics.confirm()
                viewModel.setSelfTransfer(selected.id, isTransfer)
                selectedId = null
            },
            onDelete = if (selected.isManual) {
                {
                    haptics.reject()
                    viewModel.deleteManual(selected.id) { before -> offerUndo(deletedMessage, before) }
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
                Icon(JibitoIcons.Transfer, contentDescription = null, tint = TransferBlue, modifier = Modifier.size(22.dp))
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

/** جای یک ردیف در سطح مشترک روز: فقط ردیف اول و آخر گوشه‌ی گرد دارند */
private enum class GroupPosition { Single, First, Middle, Last }

private fun groupPosition(index: Int, size: Int): GroupPosition = when {
    size == 1 -> GroupPosition.Single
    index == 0 -> GroupPosition.First
    index == size - 1 -> GroupPosition.Last
    else -> GroupPosition.Middle
}

private fun GroupPosition.shape(radius: Dp = 20.dp): Shape = when (this) {
    GroupPosition.Single -> RoundedCornerShape(radius)
    GroupPosition.First -> RoundedCornerShape(topStart = radius, topEnd = radius)
    GroupPosition.Last -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    GroupPosition.Middle -> RectangleShape
}

/** سرتیتر چسبان هر روز: «امروز» … «۴۵۰ هزار تومان خرج» */
@Composable
private fun DayHeader(group: DayGroup) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(start = 6.dp, end = 6.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Jalali.dayTitle(group.dayStartMillis),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = colors.onBackground,
        )
        if (group.spendRial > 0) {
            Text(
                stringResource(R.string.day_spend, Money.compact(group.spendRial)),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * یک تراکنش در فهرست روز: آیکون دسته (یا جهت پول)، عنوان، بانک و ساعت، برچسب دسته؛ مبلغ و مانده در سمت دیگر.
 * رنگ فقط برای پولی است که آمده (سبز)، انتقال به خودم (آبی) یا خرید ناموفق (کم‌رنگ)؛ خرج عادی رنگ متن است.
 */
@Composable
private fun TransactionRow(sms: Transaction, position: GroupPosition, onClick: () -> Unit) {
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
    val amountColor = when {
        failed -> colors.outline
        selfTransfer -> TransferBlue
        isDeposit -> DepositGreen
        else -> colors.onSurface
    }
    val title = when {
        failed -> stringResource(R.string.tx_failed_purchase)
        selfTransfer -> stringResource(R.string.tx_self_transfer)
        sms.isManual -> sms.merchant ?: stringResource(if (isDeposit) R.string.tx_manual_income else R.string.tx_manual_expense)
        sms.merchant != null -> stringResource(R.string.tx_purchase_from, sms.merchant)
        isDeposit -> stringResource(R.string.tx_deposit)
        else -> stringResource(R.string.tx_withdrawal)
    }
    val categoryEmoji = sms.categoryIcon?.takeIf { !failed && !selfTransfer && sms.categoryId != null }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(position.shape())
            .background(colors.surface)
            .clickable(onClickLabel = stringResource(R.string.cd_pick_category), onClick = onClick)
    ) {
        if (position == GroupPosition.Middle || position == GroupPosition.Last) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                thickness = 0.8.dp,
                color = colors.outlineVariant.copy(alpha = 0.6f),
            )
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (categoryEmoji != null) colors.surfaceVariant else accent.copy(alpha = 0.13f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (categoryEmoji != null) {
                    Text(categoryEmoji, fontSize = 18.sp)
                } else {
                    Icon(
                        when {
                            failed -> JibitoIcons.Refund
                            selfTransfer -> JibitoIcons.Transfer
                            isDeposit -> JibitoIcons.ArrowDown
                            else -> JibitoIcons.ArrowUp
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        if ((failed || selfTransfer) && sms.merchant != null) "${sms.merchant} · $bankName" else bankName,
                        Jalali.time(sms.dateMillis),
                        sms.feeRial?.let { stringResource(R.string.tx_fee, Money.toman(it)) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    failed -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.tx_refunded),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    selfTransfer -> {
                        Spacer(Modifier.height(6.dp))
                        Pill(
                            stringResource(R.string.tx_self_transfer_chip),
                            container = TransferBlue.copy(alpha = 0.13f),
                            content = TransferBlue,
                            leading = JibitoIcons.Transfer,
                        )
                    }
                    sms.categoryName != null -> {
                        Spacer(Modifier.height(6.dp))
                        Pill(
                            if (sms.isAutoCategorized) stringResource(R.string.tx_auto_category, sms.categoryName) else sms.categoryName,
                            container = colors.primaryContainer,
                            content = colors.onPrimaryContainer,
                        )
                    }
                    sms.suggestedCategory != null -> {
                        Spacer(Modifier.height(6.dp))
                        Pill(
                            stringResource(R.string.tx_suggested_category, sms.suggestedCategory),
                            container = colors.secondaryContainer,
                            content = colors.onSecondaryContainer,
                        )
                    }
                    else -> {
                        Spacer(Modifier.height(6.dp))
                        Pill(
                            stringResource(R.string.tx_add_category),
                            container = colors.primary.copy(alpha = 0.10f),
                            content = colors.primary,
                            leading = JibitoIcons.Plus,
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                // علامت، عدد و «تومان» سه تکه‌ی جدا هستند تا جهت‌نویسی راست‌به‌چپ جای علامت را جابه‌جا نکند.
                // در Row راست‌به‌چپ، اولین تکه سمت راست می‌نشیند: «− ۱۲۵٬۰۰۰ تومان»
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!failed) {
                        Text(
                            text = if (isDeposit) "+" else "−",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = amountColor,
                        )
                        Spacer(Modifier.size(2.dp))
                    }
                    Text(
                        text = Money.tomanNumber(t.amountRial),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = amountColor,
                        textDecoration = if (failed) TextDecoration.LineThrough else null,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.unit_toman),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = amountColor.copy(alpha = 0.8f),
                    )
                }
                t.balanceRial?.takeIf { !failed }?.let { balance ->
                    Text(
                        text = stringResource(R.string.tx_balance, Money.tomanNumber(balance)),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** برچسب کوچک زیر عنوان تراکنش (دسته، پیشنهاد، انتقال) */
@Composable
private fun Pill(text: String, container: Color, content: Color, leading: ImageVector? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(start = if (leading != null) 8.dp else 10.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(4.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
