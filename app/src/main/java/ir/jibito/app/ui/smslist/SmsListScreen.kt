package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
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
import ir.jibito.app.domain.BankBalance
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
    // حساب‌هایی که کاربر از «موجودی همه‌ی حساب‌ها» کنار گذاشته
    val walletSettings = app.container.walletSettings
    val excludedAccounts by walletSettings.excluded.collectAsState()
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
    val toggledOffMessage = stringResource(R.string.balances_toggled_off)
    val toggledOnMessage = stringResource(R.string.balances_toggled_on)
    // لمس کپسول حساب: بیرون/درون جمع موجودی، با «برگردون»
    fun toggleWallet(b: BankBalance) {
        haptics.tick()
        val before = excludedAccounts
        val include = isExcluded(b, before)
        walletSettings.setIncluded(b, include, bankBalances)
        toast = ToastMessage(
            (if (include) toggledOnMessage else toggledOffMessage).format(accountLabel(b)),
            undoLabel,
            onAction = { walletSettings.set(before) },
        )
    }
    val uncategorizedCount = remember(messages) {
        messages.orEmpty().count(::isUncategorizedSpend)
    }

    // فهرست؛ و کارت «کیف پول» که با اسکرول رو به بالا جمع می‌شود (جمعش کنار عنوان می‌ماند) و با کشیدن در بالای فهرست باز می‌شود
    val listState = rememberLazyListState()
    val walletFull = remember { IntArray(1) }
    var collapse by remember { mutableFloatStateOf(0f) }
    val collapseConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f) return Offset.Zero
                val old = collapse
                collapse = (old - available.y).coerceIn(0f, walletFull[0].toFloat())
                return Offset(0f, old - collapse)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                val old = collapse
                collapse = (old - available.y).coerceAtLeast(0f)
                return Offset(0f, old - collapse)
            }
        }
    }
    val collapsed by remember { derivedStateOf { walletFull[0] > 0 && collapse >= walletFull[0] * 0.7f } }
    val showWallet = bankBalances.isNotEmpty() && !showSearch
    // بی کارت، چیزی برای جمع شدن نیست (اسکرول نباید صرف کارت پنهان شود)
    if (!showWallet) walletFull[0] = 0
    val titleSize by animateFloatAsState(if (collapsed && showWallet) 21f else 30f, tween(220), label = "titleSize")

    // اگر کاربر هنوز دست به فهرست نزده، بالای آن دیده شود (کارت انتقال که دیرتر از دیتابیس می‌رسد، بالای فهرست پنهان نماند)
    var userScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) userScrolled = true }
    val hasSuggestion = transferSuggestions.isNotEmpty() && !showSearch
    LaunchedEffect(hasSuggestion, visible?.list?.isNotEmpty()) { if (!userScrolled) listState.scrollToItem(0) }
    // فیلتر یا جست‌وجوی تازه: از اول فهرست
    LaunchedEffect(onlyUncategorized, search) { listState.scrollToItem(0) }

    fun closeSearch() {
        viewModel.setSearch(TxSearch())
        showSearch = false
    }
    BackHandler(enabled = showSearch) { closeSearch() }

    Box(Modifier.fillMaxSize().background(colors.background)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .nestedScroll(collapseConnection),
    ) {
        if (showSearch) {
            SearchTopBar(
                search = search,
                onChange = viewModel::setSearch,
                onClose = { closeSearch() },
                onlyUncategorized = onlyUncategorized,
                onUncategorizedChange = onFilterChange,
                results = visible?.list,
                countFor = { s -> countMatching(messages.orEmpty(), onlyUncategorized, s) },
            )
        } else {
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.list_title),
                    modifier = Modifier.weight(1f),
                    fontSize = titleSize.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.onBackground,
                    maxLines = 1,
                )
                AnimatedVisibility(
                    visible = collapsed && showWallet,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    Row {
                        BalanceMiniPill(bankBalances, excludedAccounts)
                        Spacer(Modifier.width(8.dp))
                    }
                }
                // جست‌وجو (تاریخ، مبلغ، اسم)
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(t.chip)
                        .clickable(onClickLabel = stringResource(R.string.cd_search)) { showSearch = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(DesignIcons.Search, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(22.dp))
                }
            }
            if (showWallet) {
                Box(
                    Modifier
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                            walletFull[0] = p.height
                            val h = (p.height - collapse.roundToInt()).coerceIn(0, p.height)
                            layout(p.width, h) { p.place(0, h - p.height) }
                        }
                        .graphicsLayer { alpha = if (walletFull[0] > 0) (1f - collapse / walletFull[0]).coerceIn(0f, 1f) else 1f },
                ) {
                    WalletCard(
                        balances = bankBalances,
                        excluded = excludedAccounts,
                        onToggle = { b -> toggleWallet(b) },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                }
            }
            // «همه | بی‌دسته»: سوییچ دوقسمتی که همیشه بالای فهرست می‌ماند
            SegmentedFilter(
                onlyUncategorized = onlyUncategorized,
                uncategorizedCount = uncategorizedCount,
                onChange = onFilterChange,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
        }
        if (isSyncing && !messages.isNullOrEmpty()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(text = stringResource(R.string.list_syncing), fontSize = 12.sp, color = t.muted)
            }
        }
        // فیلتر، جست‌وجو و گروه‌بندی روزانه در ViewModel و بیرون از رشته‌ی اصلی انجام می‌شود
        val list = visible?.list
        val groups = visible?.groups.orEmpty()
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
                state = listState,
                contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp + LocalBottomBarSpace.current),
            ) {
                // پیشنهاد «انتقال بین حساب‌های خودم»: یکی‌یکی و فشرده، بالای فهرست
                transferSuggestions.firstOrNull()?.takeIf { !showSearch }?.let { suggestion ->
                    item(key = "transfer-suggestion") {
                        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
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
                val highlight = search.text.takeIf { showSearch }
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
                        // پیشنهاد اپ در عمق و با دسته‌های اصلی‌ای که کاربر در تنظیمات گذاشته («سوخت» ← «حمل‌ونقل»)
                        val suggestion = CategoryTree.suggestionAt(
                            sms.suggestedCategory, sms.transaction.type.code, categories, byId, displayDepth, hiddenRoots,
                        )
                        Box(Modifier.padding(horizontal = 20.dp)) {
                            TransactionRow(
                                sms = sms,
                                tint = tintOf(sms, byId),
                                onClick = { selectedId = sms.id },
                                onAcceptSuggestion = suggestion?.let { c -> { pickCategory(sms, c.id) } },
                                suggestionName = suggestion?.name,
                                highlight = highlight,
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
            onOneOff = { isOneOff ->
                haptics.confirm()
                viewModel.setOneOff(selected.id, isOneOff)
                selectedId = null
            },
            // تراکنش دستی «برای چی بود؟» خودش را دارد (همان طرف حساب)
            onNote = if (selected.isManual) {
                null
            } else {
                { note ->
                    haptics.confirm()
                    viewModel.setNote(selected.id, note)
                }
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
internal fun tintOf(sms: Transaction, byId: Map<Long, Category>): CategoryTint? {
    val c = sms.categoryId?.let { byId[it] } ?: return null
    val root = CategoryTree.rootOf(c, byId)
    return categoryTint(root.colorHex, CategoryTree.iconOf(c, byId))
}

/** سوییچ دوقسمتی «همه | بی‌دسته ۹۹+» (قسمت انتخاب‌شده: کارت روشن روی ریل) */
@Composable
private fun SegmentedFilter(
    onlyUncategorized: Boolean,
    uncategorizedCount: Int,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = JibitoTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(t.chip)
            .padding(4.dp),
    ) {
        Segment(stringResource(R.string.filter_all), selected = !onlyUncategorized, onClick = { onChange(false) }, modifier = Modifier.weight(1f))
        Segment(
            stringResource(R.string.filter_uncategorized),
            selected = onlyUncategorized,
            badge = uncategorizedCount,
            onClick = { onChange(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, badge: Int = 0) {
    val t = JibitoTheme.colors
    val bg by animateColorAsState(if (selected) t.sheet else t.chip, tween(250), label = "segBg")
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onBackground else t.muted, tween(250), label = "segFg")
    Row(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (badge > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .height(20.dp)
                    .defaultMinSize(minWidth = 20.dp)
                    .clip(CircleShape)
                    .background(t.badge)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Jalali.toPersianDigits(if (badge > 99) "99+" else badge.toString()),
                    color = t.badgeFg,
                    fontSize = 11.sp,
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
