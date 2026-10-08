package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.R
import ir.jibito.app.ui.common.EmptyStart
import ir.jibito.app.ui.common.Tip
import ir.jibito.app.ui.common.TipCard
import ir.jibito.app.ui.common.rememberTip
import ir.jibito.app.ui.common.rememberSmsAccess
import ir.jibito.app.domain.BankBalance
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.material3.FloatingActionButton
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.JibitoApplication
import ir.jibito.app.data.category.CreateCategoryResult
import androidx.compose.material3.Icon
import ir.jibito.app.data.repository.UndoSnapshot
import ir.jibito.app.ui.common.rememberHaptics
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.common.JibiToast
import ir.jibito.app.ui.common.ToastMessage
import ir.jibito.app.domain.Transaction
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    /** برگه‌ی ثبت دستی باید باز شود (دکمه‌ی «ثبت اولین خرج» در «خلاصه») */
    openManual: Boolean = false,
    onManualOpened: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val viewModel: TransactionsViewModel = viewModel(
        factory = TransactionsViewModel.factory(app.container.transactionRepository)
    )
    // null یعنی «هنوز چیزی از دیتابیس نیامده»
    val messages by viewModel.transactions.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val bankBalances by viewModel.bankBalances.collectAsState()
    // حساب‌هایی که کاربر از «موجودی همه‌ی حساب‌ها» کنار گذاشته
    val walletSettings = app.container.walletSettings
    val excludedAccounts by walletSettings.excluded.collectAsState()
    // تنظیم «نمایش دسته‌ها»: چند لایه، و کدام دسته‌های اصلی پنهان‌اند
    val displayDepth by app.container.categoryDisplay.depth.collectAsState()
    val hiddenRoots by app.container.categoryDisplay.hiddenRoots.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    // «اسم فروشگاه کدومه؟» برای همین تراکنش (روی برگه‌ی دسته)
    var teachingId by rememberSaveable { mutableStateOf<Long?>(null) }
    // از نوتیفیکیشن: وقتی تراکنش‌ها آمدند، برگه‌ی دسته‌ی همان تراکنش باز می‌شود
    LaunchedEffect(openTransactionId, messages) {
        val id = openTransactionId ?: return@LaunchedEffect
        val all = messages ?: return@LaunchedEffect
        if (all.any { it.id == id }) selectedId = id
        onOpened()
    }
    val colors = MaterialTheme.colorScheme

    var addingManual by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openManual) {
        if (openManual) {
            addingManual = true
            onManualOpened()
        }
    }
    val smsAccess = rememberSmsAccess()
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
    // نکته‌های یک‌باره: انجام دادن خود کار هم یعنی «فهمیدم»
    val uncatTip = rememberTip(Tip.UNCATEGORIZED)
    fun pickCategory(sms: Transaction, categoryId: Long?) {
        if (categoryId != null) uncatTip.dismiss()
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
        TransactionListBody(
            visible = visible,
            isSyncing = isSyncing,
            onlyUncategorized = onlyUncategorized,
            searchActive = search.isActive,
            highlight = search.text.takeIf { showSearch },
            listState = listState,
            categories = categories,
            byId = byId,
            displayDepth = displayDepth,
            hiddenRoots = hiddenRoots,
            onOpen = { sms -> selectedId = sms.id },
            onPick = { sms, categoryId -> pickCategory(sms, categoryId) },
            tip = when {
                showSearch -> null
                uncategorizedCount > 0 && uncatTip.visible -> {
                    { TipCard(stringResource(R.string.tip_uncategorized), onDismiss = uncatTip.dismiss) }
                }
                else -> null
            },
            emptyStart = if (messages?.isEmpty() == true && !isSyncing) {
                { EmptyStart(smsAccess.granted, onAddManual = { addingManual = true }, onAllowSms = smsAccess.allow) }
            } else {
                null
            },
        )
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
            onCreate = { name, parentId, icon, nature, onResult ->
                viewModel.createCategoryAndPick(selected, name, parentId, icon, nature) { result ->
                    if (result is CreateCategoryResult.Created) selectedId = null
                    onResult(result)
                }
            },
            onTeachMerchant = { teachingId = selected.id },
            onDismiss = { selectedId = null },
        )
    }
    messages?.firstOrNull { it.id == teachingId }?.let { teaching ->
        TeachMerchantFlow(
            transaction = teaching,
            teach = { name, lesson, onDone -> viewModel.teachMerchant(teaching.id, name, lesson, onDone) },
            onClose = { teachingId = null },
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
