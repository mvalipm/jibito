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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import ir.jibito.app.domain.CategoryTree
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import ir.jibito.app.data.category.CreateCategoryResult
import ir.jibito.app.data.category.CustomCategories
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import androidx.compose.material3.Icon
import ir.jibito.app.ui.theme.JibitoIcons


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
                Text(
                    text = listOfNotNull(
                        "$sign ${Money.toman(t.amountRial)}",
                        transaction.merchant ?: transaction.bank?.name,
                        Jalali.format(transaction.dateMillis),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
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
                                        root.icon,
                                        c.name,
                                    ).joinToString(" "),
                                    selected = c.id == selectedId,
                                    highlighted = c.id == suggested?.id,
                                    onClick = { onPick(c.id) },
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
                                            icon = "＋",
                                            name = stringResource(if (isDeposit) R.string.custom_add_income_short else R.string.custom_add_root_short),
                                            color = colors.primary,
                                            selected = false,
                                            open = false,
                                            onClick = { creating = CreateTarget(parentId = null) },
                                        )
                                    } else {
                                        val hasSubs = childrenOf[root.id].orEmpty().isNotEmpty()
                                        CategoryTile(
                                            icon = root.icon ?: "•",
                                            name = root.name,
                                            color = root.colorHex.toColorOrNull() ?: colors.primary,
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

/** کاشیِ یک دسته‌ی اصلی در شبکه: آیکون در دایره‌ی رنگی + اسم */
@Composable
private fun CategoryTile(icon: String, name: String, color: Color, selected: Boolean, open: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (open) colors.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (selected) colors.primary else color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Text(icon, fontSize = 20.sp, color = if (selected) colors.onPrimary else colors.onSurface) }
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected || open) FontWeight.Bold else FontWeight.Normal,
            color = colors.onSurface,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
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
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit, highlighted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected || highlighted) FontWeight.Bold else FontWeight.Normal) },
        shape = RoundedCornerShape(14.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = if (highlighted) colors.primary.copy(alpha = 0.10f) else Color.Transparent,
            labelColor = if (highlighted) colors.primary else colors.onSurface,
            selectedContainerColor = colors.primary,
            selectedLabelColor = colors.onPrimary,
        ),
    )
}

/** برای جست‌وجو: ی/ک عربی، نیم‌فاصله و فاصله یکسان می‌شوند */
private fun String.normalizedForSearch(): String =
    trim().replace('ي', 'ی').replace('ك', 'ک').replace("‌", "").replace(" ", "").lowercase()

private fun String?.toColorOrNull(): Color? = try {
    this?.let { Color(android.graphics.Color.parseColor(it)) }
} catch (e: IllegalArgumentException) {
    null
}

/** کجا دسته‌ی شخصی ساخته شود */
private data class CreateTarget(
    val parentId: Long?,
    val prefill: String = "",
    /** از جست‌وجو آمده ← کاربر جایش را انتخاب می‌کند */
    val chooseParent: Boolean = false,
)

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

/**
 * ساختن دسته‌ی شخصی: اسم، (از جست‌وجو: جایش)، و برای دسته‌ی اصلی یک آیکون.
 * بعد از ساختن، همان لحظه برای تراکنش انتخاب می‌شود.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewCategoryDialog(
    target: CreateTarget,
    roots: List<Category>,
    byId: Map<Long, Category>,
    onConfirm: (String, Long?, String?, (CreateCategoryResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(target.prefill) }
    var parentId by remember { mutableStateOf(target.parentId) }
    var icon by remember { mutableStateOf(CustomCategories.ICONS.first()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val errEmpty = stringResource(R.string.custom_error_empty)
    val errLong = stringResource(R.string.custom_error_long)
    val errDup = stringResource(R.string.custom_error_duplicate)
    val parentName = parentId?.let { byId[it]?.name }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (parentName == null) stringResource(R.string.custom_title_root)
                else stringResource(R.string.custom_title_sub, parentName),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        singleLine = true,
                        label = { Text(stringResource(R.string.custom_name)) },
                        isError = error != null,
                        supportingText = { error?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (target.chooseParent && roots.isNotEmpty()) {
                        Text(
                            stringResource(R.string.custom_where),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CategoryChip(
                                label = stringResource(R.string.custom_as_root),
                                selected = parentId == null,
                                onClick = { parentId = null },
                            )
                            roots.forEach { r ->
                                CategoryChip(
                                    label = listOfNotNull(r.icon, r.name).joinToString(" "),
                                    selected = parentId == r.id,
                                    onClick = { parentId = r.id },
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (parentId == null) {
                        Text(
                            stringResource(R.string.custom_icon),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CustomCategories.ICONS.forEach { e ->
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (e == icon) colors.primary.copy(alpha = 0.18f) else colors.surfaceVariant)
                                        .clickable { icon = e },
                                    contentAlignment = Alignment.Center,
                                ) { Text(e) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    onConfirm(name, parentId, if (parentId == null) icon else null) { result ->
                        saving = false
                        if (result is CreateCategoryResult.Invalid) {
                            error = when (result.reason) {
                                CreateCategoryResult.Reason.EMPTY -> errEmpty
                                CreateCategoryResult.Reason.TOO_LONG -> errLong
                                CreateCategoryResult.Reason.DUPLICATE -> errDup
                            }
                        }
                    }
                },
            ) { Text(stringResource(R.string.custom_save), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.budget_dialog_cancel)) } },
    )
}
