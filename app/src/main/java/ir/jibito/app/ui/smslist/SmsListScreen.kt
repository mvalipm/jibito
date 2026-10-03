package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.R
import ir.jibito.app.ui.common.MascotEmptyState
import ir.jibito.app.ui.common.MascotFace
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.material3.FloatingActionButton
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.JibitoApplication
import ir.jibito.app.data.category.CreateCategoryResult
import ir.jibito.app.util.Jalali
import androidx.compose.foundation.lazy.items
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
import ir.jibito.app.ui.common.rememberHaptics
import ir.jibito.app.ui.theme.JibitoIcons
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmsListScreen(
    /** فقط خرج‌های بی‌دسته (از «کارهای لازم» در خلاصه) */
    onlyUncategorized: Boolean = false,
    onClearFilter: () -> Unit = {},
    /** تراکنشی که برگه‌ی دسته‌اش باید باز شود (از نوتیفیکیشن) */
    openTransactionId: Long? = null,
    onOpened: () -> Unit = {},
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
    // از نوتیفیکیشن: وقتی تراکنش‌ها آمدند، برگه‌ی دسته‌ی همان تراکنش باز می‌شود
    LaunchedEffect(openTransactionId, messages) {
        val id = openTransactionId ?: return@LaunchedEffect
        val all = messages ?: return@LaunchedEffect
        if (all.any { it.id == id }) selectedId = id
        onOpened()
    }
    val colors = MaterialTheme.colorScheme

    var addingManual by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val search by viewModel.search.collectAsState()
    val visible by viewModel.visible.collectAsState()
    LaunchedEffect(onlyUncategorized) { viewModel.setOnlyUncategorized(onlyUncategorized) }
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
                        if (showSearch) viewModel.setSearch(TxSearch())
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
        // فیلتر، جست‌وجو و گروه‌بندی روزانه در ViewModel و بیرون از رشته‌ی اصلی انجام می‌شود
        val list = visible?.list
        val groups = visible?.groups.orEmpty()
        if (showSearch) {
            SearchPanel(search = search, onChange = viewModel::setSearch, results = list)
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
            // فهرست خالی: جیبی، با جمله‌ی مناسب همان حالت
            list.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(bottom = bottomSpace),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    search.isActive -> MascotEmptyState(
                        MascotFace.CURIOUS,
                        stringResource(R.string.list_search_empty_title),
                        stringResource(R.string.list_search_empty),
                        mascotSize = 104.dp,
                    )
                    onlyUncategorized && !messages.isNullOrEmpty() -> MascotEmptyState(
                        MascotFace.HAPPY,
                        stringResource(R.string.list_all_categorized_title),
                        stringResource(R.string.list_all_categorized),
                    )
                    else -> MascotEmptyState(
                        MascotFace.CURIOUS,
                        stringResource(R.string.list_empty_title),
                        stringResource(R.string.list_empty),
                    )
                }
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
                    items(group.items, key = { sms -> sms.id }) { sms ->
                        // پیشنهاد دسته با یک لمس (همان دسته‌ای که برگه هم اول پیشنهاد می‌دهد)
                        val suggested = sms.suggestedCategory?.takeIf { sms.categoryId == null }?.let { name ->
                            categories.firstOrNull { it.name == name && it.flowType == sms.transaction.type.code }
                        }
                        TransactionRow(
                            sms = sms,
                            onClick = { selectedId = sms.id },
                            onAcceptSuggestion = suggested?.let { category ->
                                {
                                    haptics.confirm()
                                    viewModel.setCategory(sms, category.id) { before ->
                                        offerUndo(categorizedMessage.format(category.name), before)
                                    }
                                }
                            },
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
