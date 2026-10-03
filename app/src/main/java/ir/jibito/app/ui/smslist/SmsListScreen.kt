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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.material3.FloatingActionButton
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.JibitoApplication
import ir.jibito.app.data.category.CreateCategoryResult
import ir.jibito.app.util.Jalali
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import ir.jibito.app.data.repository.UndoSnapshot
import ir.jibito.app.ui.common.rememberHaptics
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.CategoryTint
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.ui.common.BobbingMascot
import ir.jibito.app.ui.common.JibiToast
import ir.jibito.app.ui.common.ToastMessage
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.CategoryTree
import ir.jibito.app.domain.Transaction
import ir.jibito.app.data.parser.FlowType
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmsListScreen(
    /** فقط خرج‌های بی‌دسته (از «کارهای لازم» در خلاصه) */
    onlyUncategorized: Boolean = false,
    /** فیلتر «همه» / «بی‌دسته» */
    onFilterChange: (Boolean) -> Unit = {},
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

    // «رفت تو کافه» بالای صفحه، با «برگردون»؛ و لرزش کوتاه موقع تأیید
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    val haptics = rememberHaptics()
    val undoLabel = stringResource(R.string.undo)
    val categorizedMessage = stringResource(R.string.undo_categorized)
    val uncategorizedMessage = stringResource(R.string.undo_uncategorized)
    val deletedMessage = stringResource(R.string.undo_deleted)
    fun offerUndo(message: String, snapshot: UndoSnapshot) {
        toast = ToastMessage(message, undoLabel, onAction = {
            haptics.tick()
            viewModel.undo(snapshot)
        })
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(TOAST_MILLIS)
            toast = null
        }
    }
    val t = JibitoTheme.colors
    val byId = remember(categories) { categories.associateBy { it.id } }
    fun pickCategory(sms: Transaction, categoryId: Long?) {
        haptics.confirm()
        val name = categoryId?.let { byId[it]?.name }
        viewModel.setCategory(sms, categoryId) { before ->
            offerUndo(if (name != null) categorizedMessage.format(name) else uncategorizedMessage, before)
        }
    }
    val uncategorizedCount = remember(messages) {
        messages.orEmpty().count {
            it.categoryId == null && !it.isSelfTransfer && !it.isFailedPurchase && it.transaction.type == FlowType.WITHDRAWAL
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.list_title),
                    modifier = Modifier.weight(1f),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.onBackground,
                )
                // جست‌وجو (تاریخ، مبلغ، اسم)
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (search.isActive) colors.primaryContainer else t.chip)
                        .clickable(onClickLabel = stringResource(if (showSearch) R.string.cd_close_search else R.string.cd_search)) {
                            if (showSearch) viewModel.setSearch(TxSearch())
                            showSearch = !showSearch
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (showSearch) JibitoIcons.Close else DesignIcons.Search,
                        contentDescription = null,
                        tint = colors.onBackground,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (isSyncing && !messages.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.list_syncing),
                    fontSize = 12.sp,
                    color = t.muted,
                )
            }
            // «همه» / «بی‌دسته»
            Row(Modifier.padding(top = 14.dp)) {
                FilterPill(stringResource(R.string.filter_all), selected = !onlyUncategorized, onClick = { onFilterChange(false) })
                Spacer(Modifier.width(8.dp))
                FilterPill(
                    stringResource(R.string.filter_uncategorized),
                    selected = onlyUncategorized,
                    badge = uncategorizedCount,
                    onClick = { onFilterChange(true) },
                )
            }
        }
        // فیلتر، جست‌وجو و گروه‌بندی روزانه در ViewModel و بیرون از رشته‌ی اصلی انجام می‌شود
        val list = visible?.list
        val groups = visible?.groups.orEmpty()
        if (showSearch) {
            Box(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                SearchPanel(search = search, onChange = viewModel::setSearch, results = list)
            }
        }
        when {
            list == null || (list.isEmpty() && isSyncing) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(text = stringResource(R.string.list_loading), fontSize = 14.sp, color = t.muted)
                }
            }
            list.isEmpty() && onlyUncategorized && !search.isActive -> AllCategorized()
            list.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.list_empty),
                    fontSize = 15.sp,
                    color = t.muted,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(top = 14.dp, bottom = 88.dp + LocalBottomBarSpace.current),
            ) {
                // «چقد دارم؟»: آخرین مانده‌ی هر بانک
                if (bankBalances.isNotEmpty() && !search.isActive) {
                    item(key = "balances") { BankBalancesRow(bankBalances) }
                }
                // پیشنهاد «انتقال بین حساب‌های خودم»: یکی‌یکی، بالای فهرست
                transferSuggestions.firstOrNull()?.takeIf { !search.isActive }?.let { suggestion ->
                    item(key = "transfer-suggestion") {
                        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
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
                // روزبه‌روز: سرتیتر چسبان «امروز ━━━ ۶۰۵ هزار» و تراکنش‌های آن روز
                val maxDay = groups.maxOfOrNull { it.spendRial }?.coerceAtLeast(1L) ?: 1L
                val spendDays = groups.filter { it.spendRial > 0 }
                val avgDay = if (spendDays.isEmpty()) 0L else spendDays.sumOf { it.spendRial } / spendDays.size
                groups.forEach { group ->
                    stickyHeader(key = "day-${group.dayStartMillis}") {
                        Box(Modifier.padding(horizontal = 20.dp)) {
                            DayHeader(
                                group,
                                fraction = group.spendRial.toFloat() / maxDay,
                                heavy = spendDays.size > 1 && group.spendRial > avgDay * 5 / 4,
                            )
                        }
                    }
                    items(group.items, key = { it.id }) { sms ->
                        val suggestedId = sms.suggestedCategory?.let { name ->
                            categories.firstOrNull { it.name == name && it.flowType == sms.transaction.type.code }?.id
                        }
                        Box(Modifier.padding(horizontal = 20.dp)) {
                            TransactionRow(
                                sms = sms,
                                tint = tintOf(sms, byId),
                                onClick = { selectedId = sms.id },
                                onAcceptSuggestion = suggestedId?.let { id -> { pickCategory(sms, id) } },
                            )
                        }
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
        containerColor = t.btnBg,
        contentColor = t.btnFg,
        shape = RoundedCornerShape(20.dp),
    ) {
        Icon(JibitoIcons.Plus, contentDescription = stringResource(R.string.cd_add_manual), modifier = Modifier.size(26.dp))
    }

    JibiToast(
        message = toast,
        onDismiss = { toast = null },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
    )
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
                pickCategory(selected, categoryId)
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

/** چند میلی‌ثانیه پیام «رفت تو کافه» می‌ماند */
private const val TOAST_MILLIS = 4_000L

/** ظاهر دسته‌ی اصلیِ یک تراکنش (برای کاشی رنگی ردیف) */
@Composable
private fun tintOf(sms: Transaction, byId: Map<Long, Category>): CategoryTint? {
    val c = sms.categoryId?.let { byId[it] } ?: return null
    val root = CategoryTree.rootOf(c, byId)
    return categoryTint(root.colorHex, root.icon)
}

/** کپسول فیلتر «همه» / «بی‌دسته» (انتخاب‌شده: تیره) */
@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit, badge: Int = 0) {
    val t = JibitoTheme.colors
    val bg by animateColorAsState(if (selected) t.onBg else t.chip, tween(300), label = "pillBg")
    val fg by animateColorAsState(if (selected) t.onFg else MaterialTheme.colorScheme.onBackground, tween(300), label = "pillFg")
    Row(
        Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = if (badge > 0) 14.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (badge > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .height(22.dp)
                    .defaultMinSize(minWidth = 22.dp)
                    .clip(CircleShape)
                    .background(t.badge)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Jalali.toPersianDigits(if (badge > 99) "99+" else badge.toString()),
                    color = t.badgeFg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** فیلتر «بی‌دسته» خالی است: جیبی خوشحال */
@Composable
private fun AllCategorized() {
    val t = JibitoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 30.dp, end = 30.dp, top = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BobbingMascot(120.dp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.uncat_empty_title), fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.uncat_empty_body), fontSize = 14.sp, color = t.muted, textAlign = TextAlign.Center)
    }
}
