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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import androidx.compose.foundation.layout.offset
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.CategoryTint
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.ui.common.CategoryIconTile
import androidx.compose.foundation.layout.width
import ir.jibito.app.domain.CategoryTree
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import ir.jibito.app.data.category.CreateCategoryResult
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.repository.TransactionNotes
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.Transaction
import androidx.compose.material3.Icon
import ir.jibito.app.ui.theme.JibitoIcons

/**
 * برگه‌ای که از پایین صفحه باز می‌شود: «این خرج مال چی بود؟» / «این پول از کجا اومد؟» — خلوت و سریع:
 * سربرگ با لوگوی بانک، مبلغ، نوع و مانده؛ دکمه‌ی «پیامک» کارت پیامک را زیر سربرگ باز می‌کند.
 * ۱) خرج‌ها: جست‌وجو برای کسی که اسم را بلد است، و پرکاربردهای کاربر (یک لمس).
 * ۲) شبکه‌ی آیکونِ دسته‌های اصلی (۴ ستون)؛ پیشنهاد اپ روی خود کاشی. لمس یک دسته، زیردسته‌هایش را همان زیر باز می‌کند (دو لمس).
 *    درآمد چند دسته بیشتر ندارد: فقط همین شبکه.
 * ۳) «انتقال بین حساب‌های خودم» (کلید)، و نوار پایین: بدون دسته / حذف / بستن.
 * تنظیم «نمایش دسته‌ها» (تنظیمات): depth = ۱ یعنی لمس دسته‌ی اصلی = ثبت؛ دسته‌های پنهان دیده نمی‌شوند.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryPickerSheet(
    transaction: Transaction,
    categories: List<Category>,
    onPick: (categoryId: Long?) -> Unit,
    /** علامت زدن/برداشتن «انتقال بین حساب‌های خودم» */
    onSelfTransfer: (Boolean) -> Unit,
    /** ساختن دسته‌ی شخصی (و انتخابش برای همین تراکنش): اسم، دسته‌ی بالاتر، آیکون، نتیجه */
    onCreate: (String, Long?, String?, Int, (CreateCategoryResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    /** فقط برای تراکنش دستی: حذف آن */
    onDelete: (() -> Unit)? = null,
    /** ذخیره‌ی یادداشت خود کاربر (برگه بسته نمی‌شود)؛ null یعنی جعبه‌ی یادداشت نشان داده نشود */
    onNote: ((String) -> Unit)? = null,
    /** علامت زدن/برداشتن «خرج یک‌باره»؛ null یعنی ردیفش نشان داده نشود */
    onOneOff: ((Boolean) -> Unit)? = null,
    /** پرکاربردترین دسته‌های کاربر برای این نوع (بیشترین اول) */
    frequentIds: List<Long> = emptyList(),
    /** چند لایه دیده شود (۱ تا ۳) */
    depth: Int = 3,
    /** دسته‌های اصلی پنهان */
    hiddenRoots: Set<Long> = emptySet(),
) {
    var creating by remember { mutableStateOf<CreateTarget?>(null) }
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSms by rememberSaveable(transaction.id) { mutableStateOf(false) }
    var query by rememberSaveable(transaction.id) { mutableStateOf("") }
    // یادداشت در حال نوشتن؛ با انتخاب دسته یا بستن برگه هم ذخیره می‌شود تا نوشته گم نشود
    var noteText by rememberSaveable(transaction.id) { mutableStateOf(transaction.note.orEmpty()) }
    val noteChanged = TransactionNotes.clean(noteText) != transaction.note
    val saveNote: () -> Unit = { if (onNote != null && noteChanged) onNote(noteText) }
    val pickAndSave: (Long?) -> Unit = { saveNote(); onPick(it) }

    // برداشت ← دسته‌های خرج؛ واریز ← دسته‌های درآمد
    val isDeposit = transaction.transaction.type == FlowType.DEPOSIT
    val matching = categories.filter { it.flowType == transaction.transaction.type.code }
    val byId = matching.associateBy { it.id }
    // دیدنی‌ها: دسته‌ی اصلی‌شان پنهان نیست و از عمق مجاز پایین‌تر نیستند
    val visible = matching.filter {
        CategoryTree.rootOf(it, byId).id !in hiddenRoots && CategoryTree.depthOf(it, byId) <= depth
    }
    val childrenOf = visible.groupBy { it.parentId }
    val roots = childrenOf[null].orEmpty()
    val selectedId = transaction.categoryId
    val selectedRootId = selectedId?.let { id -> byId[id]?.let { CategoryTree.rootOf(it, byId).id } }

    // دسته‌ی اصلیِ باز در شبکه؛ از اول همانی که تراکنش دارد
    var openRootId by rememberSaveable(transaction.id) { mutableStateOf(selectedRootId?.takeIf { depth > 1 }) }
    // جزئیاتِ باز (لایه‌ی ۳)
    val initialDetail = selectedId?.let { byId[it]?.parentId }?.takeIf { byId[it]?.parentId != null }
    var openSubId by rememberSaveable(transaction.id) { mutableStateOf(initialDetail) }

    // پیشنهاد اپ + پرکاربردها، در عمق مجاز، بدون دسته‌های پنهان
    val suggested = matching.firstOrNull { it.name == transaction.suggestedCategory }
        ?.let { CategoryTree.atDepth(it, byId, depth) }
        ?.takeIf { CategoryTree.rootOf(it, byId).id !in hiddenRoots }
    val quick = (listOfNotNull(suggested) + frequentIds.mapNotNull { byId[it] }.map { CategoryTree.atDepth(it, byId, depth) })
        .filter { CategoryTree.rootOf(it, byId).id !in hiddenRoots && it.countsAsSpend }
        .distinctBy { it.id }
        .take(6)
    // اپ پیشنهادی ندارد (مثلاً بیشتر واریزها که طرف حساب ندارند) ← پرکاربردترین دسته‌ی کاربر برای همین نوع،
    // همان که دکمه‌ی اول نوتیفیکیشن است (NotificationButtons: بدون «سایر…» و دسته‌های پنهان)
    val frequentTop = if (suggested != null) null else frequentIds.asSequence()
        .mapNotNull { byId[it] }
        .filter { !it.name.startsWith("سایر") }
        .map { CategoryTree.atDepth(it, byId, depth) }
        .firstOrNull { CategoryTree.rootOf(it, byId).id !in hiddenRoots }

    ModalBottomSheet(
        onDismissRequest = { saveNote(); onDismiss() },
        sheetState = sheetState,
        containerColor = JibitoTheme.colors.sheet,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        scrimColor = JibitoTheme.colors.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 12.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(JibitoTheme.colors.handle)
            )
        },
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
                val jt = JibitoTheme.colors
                // سربرگ: بانک (لوگو) · طرف حساب · روز و ساعت، دکمه‌ی پیامک، مبلغ درشت، نوع و مانده
                SheetHeader(transaction, isDeposit, showSms = showSms, onToggleSms = { showSms = !showSms })

                // پیامک: اول چیزهایی که از آن خواندیم، بعد متن خامش، بعد «اشتباه خونده شده؟»
                if (showSms) {
                    Spacer(Modifier.height(12.dp))
                    SmsReceipt(transaction)
                }

                Text(
                    text = stringResource(if (isDeposit) R.string.sheet_title_income else R.string.sheet_title),
                    modifier = Modifier.padding(top = 16.dp, bottom = 10.dp),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.onSurface,
                )

                // جست‌وجو (خرج‌ها؛ درآمد چند دسته بیشتر ندارد)
                if (!isDeposit) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.sheet_search_hint)) },
                        leadingIcon = { Icon(JibitoIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                }

                val q = query.normalizedForSearch()
                if (q.isNotEmpty()) {
                    // نتیجه‌ی جست‌وجو: فقط دسته‌های دیدنی، با اسم دسته‌ی بالاتر
                    val results = visible.filter { it.name.normalizedForSearch().contains(q) }
                    if (results.isEmpty()) {
                        Text(
                            stringResource(R.string.sheet_search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        AddChip(stringResource(R.string.custom_create_named, query.trim())) {
                            creating = CreateTarget(parentId = null, prefill = query.trim(), chooseParent = depth > 1)
                        }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            results.forEach { c ->
                                val parent = c.parentId?.let { byId[it]?.name }
                                CategoryChip(
                                    label = listOfNotNull(c.name, parent).joinToString(" · "),
                                    selected = c.id == selectedId,
                                    onClick = { pickAndSave(c.id) },
                                )
                            }
                        }
                    }
                } else {
                    // ۱) پرکاربردها (فقط خرج‌ها؛ دسته‌های درآمد همه در شبکه پیدا هستند)
                    if (!isDeposit && quick.isNotEmpty()) {
                        Text(
                            stringResource(R.string.sheet_quick_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            quick.forEach { c ->
                                val root = CategoryTree.rootOf(c, byId)
                                CategoryChip(
                                    label = listOfNotNull(
                                        if (c.id == suggested?.id) "●" else null,
                                        c.name,
                                    ).joinToString(" "),
                                    selected = c.id == selectedId,
                                    highlighted = c.id == suggested?.id,
                                    onClick = { pickAndSave(c.id) },
                                    leadingIcon = {
                                        CategoryIconTile(
                                            categoryTint(root.colorHex, CategoryTree.iconOf(c, byId)),
                                            size = 24.dp, radius = 8.dp, iconSize = 15.dp,
                                        )
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // ۲) شبکه‌ی دسته‌های اصلی؛ زیردسته‌ها زیر همان ردیفی که لمس شده باز می‌شوند
                    if (!isDeposit) {
                        Text(
                            stringResource(R.string.sheet_all_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    // پیشنهاد اپ (یا اگر نداشت، پرکاربردترین دسته) روی خود کاشی: حلقه + برچسب، تا وقتی دسته‌ای انتخاب نشده
                    val hint = if (selectedId != null) null else suggested ?: frequentTop
                    val hintRootId = hint?.let { CategoryTree.rootOf(it, byId).id }
                    val hintLabel = stringResource(if (suggested != null) R.string.sheet_suggested else R.string.sheet_frequent)
                    val tiles: List<Category?> = roots + listOf(null) // null = «＋ دسته‌ی جدید»
                    tiles.chunked(GRID_COLUMNS).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { root ->
                                Box(Modifier.weight(1f)) {
                                    if (root == null) {
                                        CategoryTile(
                                            tint = CategoryTint(JibitoTheme.colors.chip, colors.primary, JibitoIcons.Plus, null),
                                            name = stringResource(R.string.custom_add_root_short),
                                            selected = false,
                                            open = false,
                                            onClick = { creating = CreateTarget(parentId = null) },
                                        )
                                    } else {
                                        val hasSubs = childrenOf[root.id].orEmpty().isNotEmpty()
                                        CategoryTile(
                                            tint = categoryTint(root.colorHex, root.icon),
                                            name = root.name,
                                            selected = root.id == selectedRootId,
                                            open = root.id == openRootId,
                                            badge = hintLabel.takeIf { root.id == hintRootId },
                                            onClick = {
                                                // فقط دسته‌ی اصلی (یا بی‌زیردسته) ← همین لمس = ثبت
                                                if (depth == 1 || (!hasSubs && isDeposit)) {
                                                    pickAndSave(root.id)
                                                } else {
                                                    openRootId = if (openRootId == root.id) null else root.id
                                                    openSubId = null
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            // جای خالی آخرین ردیف
                            repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        // پنل زیردسته‌ها، اگر دسته‌ی باز در همین ردیف است
                        row.firstOrNull { it != null && it.id == openRootId }?.let { open ->
                            SubPanel(
                                root = open,
                                subs = childrenOf[open.id].orEmpty(),
                                childrenOf = childrenOf,
                                selectedId = selectedId,
                                openSubId = openSubId,
                                onOpenSub = { openSubId = if (openSubId == it) null else it },
                                onPick = pickAndSave,
                                onAdd = { parentId -> creating = CreateTarget(parentId = parentId) },
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                // یادداشت (مثلاً همان که در جعبه‌ی «بنویس» نوتیفیکیشن نوشته شده)؛ با «ذخیره» یا دکمه‌ی تمام کیبورد
                if (onNote != null) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { if (it.length <= TransactionNotes.MAX_LENGTH) noteText = it },
                        label = { Text(stringResource(R.string.tx_note_label)) },
                        placeholder = { Text(stringResource(R.string.tx_note_placeholder)) },
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { saveNote() }),
                        trailingIcon = if (noteChanged) {
                            { TextButton(onClick = saveNote) { Text(stringResource(R.string.tx_note_save)) } }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // انتقال بین حساب‌های خودم (نه خرج است نه درآمد)
                if (!transaction.isManual) {
                    Spacer(Modifier.height(10.dp))
                    SelfTransferRow(checked = transaction.isSelfTransfer, onChange = { saveNote(); onSelfTransfer(it) })
                }

                // خرج یک‌باره (خرید خانه، ماشین…): فقط برای خرجی که حساب می‌شود
                val countsAsSpend = transaction.transaction.type == FlowType.WITHDRAWAL &&
                    !transaction.isSelfTransfer && !transaction.isFailedPurchase
                if (onOneOff != null && countsAsSpend) {
                    Spacer(Modifier.height(10.dp))
                    OneOffRow(checked = transaction.isOneOff, onChange = { saveNote(); onOneOff(it) })
                }

                if (transaction.merchant != null) {
                    Text(
                        text = stringResource(R.string.sheet_learning_hint),
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }

                // نوار پایین: «بدون دسته» · «حذف» (فقط دستی) · «بستن»
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(JibitoTheme.colors.border))
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (transaction.categoryId != null) {
                        TextButton(onClick = { pickAndSave(null) }) {
                            Text(stringResource(R.string.sheet_clear), color = jt.muted)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text(stringResource(R.string.manual_delete), color = colors.error)
                        }
                    }
                    TextButton(onClick = { saveNote(); onDismiss() }) {
                        Text(stringResource(R.string.sheet_close), color = jt.muted)
                    }
                }
            }
        }
    }

    creating?.let { target ->
        NewCategoryDialog(
            target = target,
            roots = if (isDeposit) emptyList() else roots,
            byId = byId,
            onConfirm = { name, parentId, icon, nature, onResult -> saveNote(); onCreate(name, parentId, icon, nature, onResult) },
            onDismiss = { creating = null },
            askNature = !isDeposit,
        )
    }
}

private const val GRID_COLUMNS = 4

/**
 * کاشیِ یک دسته‌ی اصلی در شبکه (طرح «جیبی»): مربع گرد‌گوشه‌ی رنگی با آیکون خطی + اسم یک‌خطی.
 * [badge]: پیشنهاد اپ (یا پرکاربردترین دسته) ← حلقه‌ی رنگی دور کاشی و برچسب «پیشنهاد»/«پرکاربرد» بالایش.
 */
@Composable
private fun CategoryTile(
    tint: CategoryTint,
    name: String,
    selected: Boolean,
    open: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
) {
    val suggested = badge != null
    val colors = MaterialTheme.colorScheme
    val jt = JibitoTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (open) jt.chip else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            val ring = when {
                selected -> Modifier.border(2.5.dp, tint.fg, shape)
                suggested -> Modifier.border(2.dp, jt.coral, shape)
                else -> Modifier
            }
            CategoryIconTile(tint, size = 54.dp, radius = 18.dp, iconSize = 26.dp, modifier = ring)
            if (suggested && !selected) {
                Text(
                    badge.orEmpty(),
                    modifier = Modifier
                        .offset(y = (-7).dp)
                        .background(jt.coral, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp),
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 14.sp,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            fontSize = 12.5.sp,
            fontWeight = if (selected || open || suggested) FontWeight.Bold else FontWeight.Medium,
            color = colors.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
