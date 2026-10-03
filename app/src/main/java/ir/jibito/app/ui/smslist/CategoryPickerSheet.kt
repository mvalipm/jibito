package ir.jibito.app.ui.smslist

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import ir.jibito.app.data.sms.SmsSenderLookup
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.CategoryTint
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.ui.common.CategoryIconTile
import ir.jibito.app.ui.common.amount
import androidx.compose.foundation.layout.width
import ir.jibito.app.domain.CategoryTree
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import ir.jibito.app.data.category.CreateCategoryResult
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import androidx.compose.material3.Icon
import ir.jibito.app.ui.theme.JibitoIcons
import android.content.Intent
import android.widget.Toast
import android.net.Uri
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.ui.platform.LocalContext
import ir.jibito.app.data.review.WrongReadingReport

/**
 * برگه‌ای که از پایین صفحه باز می‌شود: «این خرج مال چی بود؟» — خلوت و سریع:
 * ۱) پیشنهاد اپ + پرکاربردهای کاربر (یک لمس).
 * ۲) شبکه‌ی آیکونِ دسته‌های اصلی (۴ ستون). لمس یک دسته، زیردسته‌هایش را همان زیر باز می‌کند (دو لمس).
 * ۳) جست‌وجو برای کسی که اسم را بلد است.
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
    onCreate: (String, Long?, String?, (CreateCategoryResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    /** فقط برای تراکنش دستی: حذف آن */
    onDelete: (() -> Unit)? = null,
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                // خلاصه‌ی تراکنش: طرف حساب · ساعت · بانک، مبلغ درشت، و سؤال
                val t = transaction.transaction
                val jt = JibitoTheme.colors
                Text(
                    text = listOfNotNull(
                        transaction.merchant,
                        Jalali.time(transaction.dateMillis),
                        transaction.bank?.name?.let(::shortBankName),
                    ).joinToString(" · "),
                    fontSize = 13.sp,
                    color = jt.muted,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    val amountColor = if (isDeposit) jt.income else colors.onSurface
                    Text(if (isDeposit) "+" else "−", fontSize = 30.sp, fontWeight = FontWeight.Black, color = amountColor)
                    Spacer(Modifier.width(6.dp))
                    Text(amount(Money.tomanNumber(t.amountRial)), fontSize = 30.sp, fontWeight = FontWeight.Black, color = amountColor)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.unit_toman),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = jt.muted,
                        modifier = Modifier.padding(bottom = 7.dp),
                    )
                }
                Text(
                    text = stringResource(if (isDeposit) R.string.sheet_title_income else R.string.sheet_title),
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(10.dp))

                // انتقال بین حساب‌های خودم (نه خرج است نه درآمد)
                if (!transaction.isManual) {
                    FilterChip(
                        selected = transaction.isSelfTransfer,
                        onClick = { onSelfTransfer(!transaction.isSelfTransfer) },
                        label = { Text(stringResource(R.string.sheet_self_transfer), fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JibitoTheme.colors.transfer,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }

                // جست‌وجو (خرج‌ها)
                if (!isDeposit) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.sheet_search_hint)) },
                        leadingIcon = { Icon(JibitoIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))

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
                                    onClick = { onPick(c.id) },
                                )
                            }
                        }
                    }
                } else {
                    // ۱) پیشنهاد و پرکاربردها
                    if (quick.isNotEmpty()) {
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
                                    onClick = { onPick(c.id) },
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
                    Text(
                        stringResource(R.string.sheet_all_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    val tiles: List<Category?> = roots + listOf(null) // null = «＋ دسته‌ی جدید»
                    tiles.chunked(GRID_COLUMNS).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { root ->
                                Box(Modifier.weight(1f)) {
                                    if (root == null) {
                                        CategoryTile(
                                            tint = CategoryTint(JibitoTheme.colors.chip, colors.primary, JibitoIcons.Plus, null),
                                            name = stringResource(if (isDeposit) R.string.custom_add_income_short else R.string.custom_add_root_short),
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
                                            onClick = {
                                                // فقط دسته‌ی اصلی (یا بی‌زیردسته) ← همین لمس = ثبت
                                                if (depth == 1 || (!hasSubs && isDeposit)) {
                                                    onPick(root.id)
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
                                onPick = onPick,
                                onAdd = { parentId -> creating = CreateTarget(parentId = parentId) },
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                if (transaction.merchant != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.sheet_learning_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (transaction.categoryId != null) {
                        TextButton(onClick = { onPick(null) }) {
                            Text(stringResource(R.string.sheet_clear))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text(stringResource(R.string.manual_delete), color = colors.error)
                        }
                    }
                    if (transaction.body.isNotBlank()) TextButton(onClick = { showSms = !showSms }) {
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
                    // مبلغ یا نوع اشتباه خوانده شده؟ اول «چه چیزی غلطه؟»، بعد گزارش (با رقم‌های پوشیده) برای بهتر شدن پارسر
                    if (!transaction.isManual) {
                        var reporting by rememberSaveable { mutableStateOf(false) }
                        TextButton(onClick = { reporting = true }) { Text(stringResource(R.string.sheet_report_wrong)) }
                        if (reporting) {
                            WrongReadingDialog(transaction, onDismiss = { reporting = false })
                        }
                        Text(
                            stringResource(R.string.review_share_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
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
            onConfirm = { name, parentId, icon, onResult -> onCreate(name, parentId, icon, onResult) },
            onDismiss = { creating = null },
        )
    }
}

private const val GRID_COLUMNS = 4

/** کاشیِ یک دسته‌ی اصلی در شبکه (طرح «جیبی»): مربع گرد‌گوشه‌ی رنگی با آیکون خطی + اسم */
@Composable
private fun CategoryTile(tint: CategoryTint, name: String, selected: Boolean, open: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (open) JibitoTheme.colors.chip else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryIconTile(
            tint,
            size = 60.dp,
            radius = 20.dp,
            iconSize = 28.dp,
            modifier = if (selected) Modifier.border(2.5.dp, tint.fg, RoundedCornerShape(20.dp)) else Modifier,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            fontSize = 13.sp,
            fontWeight = if (selected || open) FontWeight.Bold else FontWeight.Medium,
            color = colors.onSurface,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
        )
    }
}

/** زیردسته‌های دسته‌ی باز (و جزئیاتِ زیردسته‌ی باز) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubPanel(
    root: Category,
    subs: List<Category>,
    childrenOf: Map<Long?, List<Category>>,
    selectedId: Long?,
    openSubId: Long?,
    onOpenSub: (Long) -> Unit,
    onPick: (Long?) -> Unit,
    onAdd: (parentId: Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(colors.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(10.dp)
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip(
                label = stringResource(R.string.sheet_all_of, root.name),
                selected = root.id == selectedId,
                onClick = { onPick(root.id) },
            )
            subs.forEach { sub ->
                val details = childrenOf[sub.id].orEmpty()
                val isOpen = openSubId == sub.id
                CategoryChip(
                    label = sub.name + if (details.isNotEmpty()) (if (isOpen) "  ⌄" else "  ›") else "",
                    selected = sub.id == selectedId || details.any { it.id == selectedId },
                    onClick = { if (details.isEmpty()) onPick(sub.id) else onOpenSub(sub.id) },
                )
            }
            AddChip("＋") { onAdd(root.id) }
        }
        subs.firstOrNull { it.id == openSubId }?.let { open ->
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryChip(
                    label = stringResource(R.string.sheet_all_of, open.name),
                    selected = open.id == selectedId,
                    onClick = { onPick(open.id) },
                )
                childrenOf[open.id].orEmpty().forEach { d ->
                    CategoryChip(label = d.name, selected = d.id == selectedId, onClick = { onPick(d.id) })
                }
                AddChip("＋") { onAdd(open.id) }
            }
        }
        if (!root.countsAsSpend) {
            Text(
                stringResource(R.string.sheet_not_spend_hint),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
internal fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected || highlighted) FontWeight.Bold else FontWeight.Normal) },
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(17.dp),
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = if (highlighted) JibitoTheme.colors.sugBg else JibitoTheme.colors.chip,
            labelColor = if (highlighted) JibitoTheme.colors.sugFg else colors.onSurface,
            iconColor = colors.primary,
            selectedContainerColor = colors.primary,
            selectedLabelColor = colors.onPrimary,
            selectedLeadingIconColor = colors.onPrimary,
        ),
    )
}

/** برای جست‌وجو: ی/ک عربی، نیم‌فاصله و فاصله یکسان می‌شوند */
internal fun String.normalizedForSearch(): String =
    trim().replace('ي', 'ی').replace('ك', 'ک').replace("‌", "").replace(" ", "").lowercase()

internal fun String?.toColorOrNull(): Color? = try {
    this?.let { Color(android.graphics.Color.parseColor(it)) }
} catch (e: IllegalArgumentException) {
    null
}

@Composable
private fun AddChip(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = colors.primary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
}

/** «چه چیزی غلط خونده شده؟» و بعد ارسال گزارش (رقم‌ها پوشیده، به‌علاوه‌ی علت، سرشماره و نسخه‌ی اپ) */
@Composable
private fun WrongReadingDialog(transaction: Transaction, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.review_share_title)
    var reason by rememberSaveable { mutableStateOf<WrongReadingReport.Reason?>(null) }
    val labels = mapOf(
        WrongReadingReport.Reason.AMOUNT to R.string.report_reason_amount,
        WrongReadingReport.Reason.TYPE to R.string.report_reason_type,
        WrongReadingReport.Reason.MERCHANT to R.string.report_reason_merchant,
        WrongReadingReport.Reason.SHOULD_BE_TRANSFER to R.string.report_reason_transfer,
        WrongReadingReport.Reason.OTHER to R.string.report_reason_other,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_reason_title), fontWeight = FontWeight.Bold) },
        text = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(Modifier.selectableGroup()) {
                labels.forEach { (r, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(selected = reason == r, role = Role.RadioButton, onClick = { reason = r })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(label))
                    }
                }
            }
            }
        },
        confirmButton = {
            // اصلی: چت پشتیبانی در بله (متن گزارش کپی می‌شود تا کاربر فقط بچسباند و بفرستد)؛ فرعی: هر راه دیگری
            val copiedHint = stringResource(R.string.report_copied_paste)
            Row {
                TextButton(
                    enabled = reason != null,
                    onClick = {
                        val text = reportText(context, transaction, reason)
                        val shared = Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                            chooserTitle,
                        )
                        context.startActivity(shared)
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.report_send_other)) }
                TextButton(
                    enabled = reason != null,
                    onClick = {
                        val text = reportText(context, transaction, reason)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("jibito report", text))
                        val opened = runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_BALE_URL)))
                        }.isSuccess
                        if (opened) {
                            Toast.makeText(context, copiedHint, Toast.LENGTH_LONG).show()
                        } else {
                            // بله (و مرورگر) نیست: همان صفحه‌ی اشتراک‌گذاری، تا گزارش از دست نرود
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                                    chooserTitle,
                                )
                            )
                        }
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.report_send_bale), fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.budget_dialog_cancel)) } },
    )
}

/** چت پشتیبانی جیبیتو در پیام‌رسان بله */
private const val SUPPORT_BALE_URL = "https://ble.ir/jibito_support"

private fun reportText(context: Context, transaction: Transaction, reason: WrongReadingReport.Reason?): String {
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    val sender = SmsSenderLookup.sender(context, transaction.smsId)
    return WrongReadingReport.text(transaction, reason, sender, version)
}

