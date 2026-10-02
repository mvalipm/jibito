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
import ir.jibito.app.data.category.CreateCategoryResult
import ir.jibito.app.data.category.CustomCategories
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

private val TransferBlue = Color(0xFF3A6FD8)

/**
 * برگه‌ای که از پایین صفحه باز می‌شود: انتخاب دسته با کمترین لمس.
 * - بالا: جست‌وجو (مثلاً «سوخ» ← سوخت · خودرو شخصی).
 * - پیشنهاد اپ اول می‌آید.
 * - بعد هر دسته‌ی اصلی یک بخش است و زیردسته‌هایش با یک لمس انتخاب می‌شوند.
 *   زیردسته‌ای که خودش جزئیات دارد (مثل «خودرو شخصی ›») با لمس باز می‌شود.
 * - خود دسته‌ی اصلی هم قابل انتخاب است (وقتی زیردسته مهم نیست).
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
    val childrenOf = matching.groupBy { it.parentId }
    val roots = childrenOf[null].orEmpty()
    val suggested = matching.firstOrNull { it.name == transaction.suggestedCategory }
    val selectedId = transaction.categoryId

    // اگر دسته‌ی فعلی یک جزئیات (لایه‌ی ۳) است، زیردسته‌اش از اول باز باشد
    val initialExpanded = selectedId?.let { byId[it]?.parentId }?.takeIf { byId[it]?.parentId != null }
    var expandedId by rememberSaveable(transaction.id) { mutableStateOf(initialExpanded) }

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
                Spacer(Modifier.height(14.dp))

                // انتقال بین حساب‌های خودم: جدا از دسته‌ها، چون نه خرج است نه درآمد
                if (!transaction.isManual) FilterChip(
                    selected = transaction.isSelfTransfer,
                    onClick = { onSelfTransfer(!transaction.isSelfTransfer) },
                    label = { Text(stringResource(R.string.sheet_self_transfer), fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(14.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TransferBlue,
                        selectedLabelColor = Color.White,
                    ),
                )
                if (!transaction.isManual) Text(
                    text = stringResource(R.string.sheet_self_transfer_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))

                // جست‌وجو (فقط برای خرج‌ها؛ درآمد چند دسته بیشتر ندارد)
                if (!isDeposit) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.sheet_search_hint)) },
                        leadingIcon = { Text("🔍") },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                }

                val q = query.normalizedForSearch()
                if (q.isNotEmpty()) {
                    // نتیجه‌ی جست‌وجو: همه‌ی لایه‌ها، با اسم دسته‌ی بالاتر
                    val results = matching.filter { it.name.normalizedForSearch().contains(q) }
                    if (results.isEmpty()) {
                        Text(
                            stringResource(R.string.sheet_search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        AddChip(stringResource(R.string.custom_create_named, query.trim())) {
                            creating = CreateTarget(parentId = null, prefill = query.trim(), chooseParent = true)
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
                    // پیشنهاد اپ
                    if (suggested != null && suggested.id != selectedId) {
                        Text(
                            stringResource(R.string.sheet_suggested_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = colors.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        val parent = suggested.parentId?.let { byId[it]?.name }
                        CategoryChip(
                            label = "● " + listOfNotNull(suggested.name, parent).joinToString(" · "),
                            selected = false,
                            highlighted = true,
                            onClick = { onPick(suggested.id) },
                        )
                        Spacer(Modifier.height(14.dp))
                    }

                    roots.forEach { root ->
                        val subs = childrenOf[root.id].orEmpty()
                        RootHeader(
                            root = root,
                            selected = root.id == selectedId,
                            hasChildren = subs.isNotEmpty(),
                            onClick = { onPick(root.id) },
                        )
                        if (subs.isNotEmpty() || !isDeposit) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                subs.forEach { sub ->
                                    val details = childrenOf[sub.id].orEmpty()
                                    val isOpen = expandedId == sub.id
                                    CategoryChip(
                                        label = sub.name + if (details.isNotEmpty()) (if (isOpen) "  ⌄" else "  ›") else "",
                                        selected = sub.id == selectedId || (details.any { it.id == selectedId }),
                                        onClick = {
                                            if (details.isEmpty()) onPick(sub.id)
                                            else expandedId = if (isOpen) null else sub.id
                                        },
                                    )
                                }
                                // + زیردسته‌ی شخصی در همین دسته‌ی اصلی
                                AddChip(stringResource(R.string.custom_add)) {
                                    creating = CreateTarget(parentId = root.id)
                                }
                            }
                            // جزئیات زیردسته‌ی باز (لایه‌ی ۳)
                            subs.firstOrNull { it.id == expandedId }?.let { open ->
                                Spacer(Modifier.height(8.dp))
                                FlowRow(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(colors.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CategoryChip(
                                        label = stringResource(R.string.sheet_all_of, open.name),
                                        selected = open.id == selectedId,
                                        onClick = { onPick(open.id) },
                                    )
                                    childrenOf[open.id].orEmpty().forEach { d ->
                                        CategoryChip(label = d.name, selected = d.id == selectedId, onClick = { onPick(d.id) })
                                    }
                                    AddChip(stringResource(R.string.custom_add)) {
                                        creating = CreateTarget(parentId = open.id)
                                    }
                                }
                            }
                        }
                        if (!root.countsAsSpend) {
                            Text(
                                stringResource(R.string.sheet_not_spend_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(if (subs.isEmpty() && isDeposit) 4.dp else 14.dp))
                    }

                    // + دسته‌ی اصلی جدید (برای درآمد: دسته‌ی درآمد جدید)
                    AddChip(stringResource(if (isDeposit) R.string.custom_add_income else R.string.custom_add_root)) {
                        creating = CreateTarget(parentId = null)
                    }
                    Spacer(Modifier.height(14.dp))
                }

                if (transaction.merchant != null) {
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

/** سرِ بخش هر دسته‌ی اصلی؛ خودش هم قابل انتخاب است */
@Composable
private fun RootHeader(root: Category, selected: Boolean, hasChildren: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dot = root.colorHex.toColorOrNull() ?: colors.primary
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(
                if (selected) colors.primary else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(dot.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(root.icon ?: "•") }
        Spacer(Modifier.size(8.dp))
        Text(
            root.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = if (selected) colors.onPrimary else colors.onSurface,
        )
        if (hasChildren && !selected) {
            Text(
                stringResource(R.string.sheet_pick_root),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
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
