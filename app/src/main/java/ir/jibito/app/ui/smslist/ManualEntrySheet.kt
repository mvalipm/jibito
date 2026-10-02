package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.CategoryTree
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

private val DepositGreen = Color(0xFF1E9E6A)

/** حداکثر ۱۲ رقم تومان (هزار میلیارد) — جلوی عددهای اشتباهی بزرگ */
private const val MAX_DIGITS = 12

/** چند دسته‌ی سریع نشان داده شود */
const val QUICK_CATEGORY_COUNT = 6

/**
 * ثبت دستی خرج یا درآمد (نقدی، یا کارتی که پیامکش نمی‌آید) با کمترین لمس:
 * مبلغ با صفحه‌کلید بزرگ (دکمه‌ی «۰۰۰» برای هزارها) ← لمس یک دسته = ثبت.
 * یادداشت و «دیروز» اختیاری‌اند. «دسته‌ی دیگر…» ثبت می‌کند و برگه‌ی کامل دسته‌ها را باز می‌کند.
 *
 * @param loadQuick پرکاربردترین دسته‌ها برای این نوع (خرج/درآمد)
 * @param onSave نوع، مبلغ به ریال، دسته، یادداشت، تاریخ، و اینکه بعدش برگه‌ی کامل دسته‌ها باز شود یا نه
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManualEntrySheet(
    categories: List<Category>,
    loadQuick: (flowType: Int, onResult: (List<Long>) -> Unit) -> Unit,
    onSave: (FlowType, Long, Long?, String?, Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
    /** تنظیم «نمایش دسته‌ها»: چند لایه، و کدام دسته‌های اصلی پنهان‌اند */
    depth: Int = 3,
    hiddenRoots: Set<Long> = emptySet(),
) {
    val colors = MaterialTheme.colorScheme
    var isDeposit by rememberSaveable { mutableStateOf(false) }
    var digits by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var yesterday by rememberSaveable { mutableStateOf(false) }
    var quickIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val type = if (isDeposit) FlowType.DEPOSIT else FlowType.WITHDRAWAL
    val accent = if (isDeposit) DepositGreen else colors.primary

    LaunchedEffect(type) { loadQuick(type.code) { quickIds = it } }

    val toman = digits.toLongOrNull() ?: 0L
    val ready = toman > 0

    // دسته‌های سریع: اول پرکاربردهای خود کاربر، بعد (برای کاربر تازه) دسته‌های اصلی به ترتیب پیش‌فرض
    val byId = categories.associateBy { it.id }
    val ofType = categories.filter { it.flowType == type.code && it.countsAsSpend }
    val quick = (quickIds.mapNotNull { byId[it] }.filter { it.flowType == type.code }
        .map { CategoryTree.atDepth(it, byId, depth) } +
        ofType.filter { it.parentId == null })
        .filter { CategoryTree.rootOf(it, byId).id !in hiddenRoots && it.countsAsSpend }
        .distinctBy { it.id }
        .take(QUICK_CATEGORY_COUNT)

    fun save(categoryId: Long?, openPicker: Boolean) {
        if (!ready) return
        val day = 24L * 60 * 60 * 1000
        val date = System.currentTimeMillis() - if (yesterday) day else 0L
        onSave(type, toman * 10, categoryId, note.takeIf { it.isNotBlank() }, date, openPicker)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    stringResource(R.string.manual_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(12.dp))

                // خرج یا درآمد
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeToggle(stringResource(R.string.manual_expense), "↑", !isDeposit, colors.primary) { isDeposit = false }
                    TypeToggle(stringResource(R.string.manual_income), "↓", isDeposit, DepositGreen) { isDeposit = true }
                }

                // مبلغ
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        if (ready) Money.tomanNumber(toman * 10) else stringResource(R.string.manual_amount_hint),
                        fontSize = if (ready) 38.sp else 24.sp,
                        fontWeight = FontWeight.Black,
                        color = if (ready) accent else colors.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    if (ready) {
                        Spacer(Modifier.padding(start = 6.dp))
                        Text(
                            stringResource(R.string.unit_toman),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }

                // صفحه‌کلید (ترتیب ماشین‌حسابی، چپ‌به‌راست)
                Spacer(Modifier.height(10.dp))
                Keypad(
                    onDigit = { d -> if (digits.length < MAX_DIGITS && !(digits.isEmpty() && d == "0")) digits += d },
                    onThousand = { if (digits.isNotEmpty()) digits = (digits + "000").take(MAX_DIGITS) },
                    onBackspace = { digits = digits.dropLast(1) },
                    onClear = { digits = "" },
                )

                // یادداشت و روز
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(40) },
                    singleLine = true,
                    placeholder = { Text(stringResource(if (isDeposit) R.string.manual_note_hint_income else R.string.manual_note_hint)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallChoice(stringResource(R.string.manual_today), !yesterday) { yesterday = false }
                    SmallChoice(stringResource(R.string.manual_yesterday), yesterday) { yesterday = true }
                }

                // دسته = ثبت
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(if (ready) R.string.manual_pick_to_save else R.string.manual_enter_amount_first),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = if (ready) colors.onSurface else colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { c ->
                        FilterChip(
                            selected = false,
                            enabled = ready,
                            onClick = { save(c.id, openPicker = false) },
                            label = {
                                Text(
                                    listOfNotNull(rootIcon(c, byId), c.name).joinToString(" "),
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = accent.copy(alpha = 0.10f),
                                labelColor = colors.onSurface,
                            ),
                        )
                    }
                    FilterChip(
                        selected = false,
                        enabled = ready,
                        onClick = { save(null, openPicker = true) },
                        label = { Text(stringResource(R.string.manual_other_category)) },
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { save(null, openPicker = false) },
                    enabled = ready,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text(stringResource(R.string.manual_save_without_category)) }
            }
        }
    }
}

/** آیکون دسته‌ی اصلی (زیردسته‌ها آیکون ندارند) */
private fun rootIcon(c: Category, byId: Map<Long, Category>): String? {
    var current = c
    var steps = 0
    while (current.parentId != null && steps < 10) {
        current = byId[current.parentId] ?: break
        steps++
    }
    return current.icon
}

@Composable
private fun TypeToggle(label: String, icon: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text("$icon  $label", fontWeight = FontWeight.Bold) },
        shape = RoundedCornerShape(14.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color.White,
        ),
    )
}

@Composable
private fun SmallChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.primary.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (selected) colors.primary else colors.outlineVariant, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        color = if (selected) colors.primary else colors.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
}

/** صفحه‌کلید عددی بزرگ؛ ترتیب ماشین‌حسابی (۱۲۳ از چپ)، مثل صفحه‌کلید عددی گوشی */
@Composable
private fun Keypad(onDigit: (String) -> Unit, onThousand: () -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("000", "0", "⌫"))
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        KeypadKey(
                            label = when (key) {
                                "000" -> "۰۰۰"
                                "⌫" -> "⌫"
                                else -> Jalali.toPersianDigits(key)
                            },
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when (key) {
                                    "000" -> onThousand()
                                    "⌫" -> onBackspace()
                                    else -> onDigit(key)
                                }
                            },
                            onLongClick = if (key == "⌫") onClear else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, modifier: Modifier, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.7f))
            .combinedClickableCompat(onClick, onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** لمس عادی؛ و برای ⌫، نگه داشتن = پاک کردن کل عدد */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    this.then(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick))
