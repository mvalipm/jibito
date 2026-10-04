package ir.jibito.app.ui.transfers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.domain.TransferSuggestion
import ir.jibito.app.ui.common.Mascot
import ir.jibito.app.ui.common.MascotFace
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.common.rememberHaptics
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.ui.smslist.shortBankName
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import kotlinx.coroutines.launch

/**
 * «انتقال‌های احتمالی»: همه‌ی جفت‌های «برداشت ← واریز هم‌مبلغ» که اپ مطمئن نیست انتقال بین حساب‌های خود کاربرند،
 * گروه‌شده بر اساس بانک‌ها، تا صدها مورد (مثلاً بار اول نصب) با چند لمس جواب داده شوند.
 * از تب «کارها» باز می‌شود؛ هر مورد که جواب داده شود از فهرست بیرون می‌رود.
 */
@Composable
fun TransferReviewScreen(onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val repository = app.container.transactionRepository
    val flow = remember { repository.observeTransferSuggestions() }
    // null یعنی هنوز نیامده (تا صفحه‌ی «همه مرور شد» بی‌جا دیده نشود)
    val suggestions by flow.collectAsState(initial = null)
    val groups = remember(suggestions) { suggestions?.let(::groupTransfers) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }

    fun yes(list: List<TransferSuggestion>) {
        haptics.confirm()
        scope.launch { repository.confirmTransfers(list) }
    }
    fun no(list: List<TransferSuggestion>) {
        haptics.reject()
        scope.launch { repository.rejectTransfers(list) }
    }

    TransferReviewContent(
        groups = groups,
        expandedKey = expandedKey,
        onToggle = { key -> expandedKey = if (expandedKey == key) null else key },
        onBack = onClose,
        onYesAll = { yes(suggestions.orEmpty()) },
        onYes = { yes(it) },
        onNo = { no(it) },
    )
}

/** خود صفحه، بدون وابستگی به داده (برای اسکرین‌شات هم) */
@Composable
fun TransferReviewContent(
    groups: List<TransferGroup>?,
    expandedKey: String?,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
    onYesAll: () -> Unit,
    onYes: (List<TransferSuggestion>) -> Unit,
    onNo: (List<TransferSuggestion>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val t = JibitoTheme.colors
    val total = groups?.sumOf { it.items.size } ?: 0

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(JibitoIcons.Back, contentDescription = stringResource(R.string.review_back), tint = colors.onBackground)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.transfers_title),
                    modifier = Modifier.semantics { heading() },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.onBackground,
                )
                if (total > 0) {
                    Text(
                        Jalali.toPersianDigits(stringResource(R.string.transfers_subtitle, total, groups.orEmpty().size)),
                        fontSize = 13.sp,
                        color = t.muted,
                    )
                }
            }
        }

        when {
            groups == null -> Unit
            groups.isEmpty() -> AllReviewed(onBack)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + LocalBottomBarSpace.current),
            ) {
                item(key = "explain") {
                    Text(
                        stringResource(R.string.transfers_explain),
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        color = t.muted,
                    )
                }
                item(key = "yes-all") {
                    PillButton(
                        Jalali.toPersianDigits(stringResource(R.string.transfers_yes_all, total)),
                        filled = true,
                        onClick = onYesAll,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        large = true,
                    )
                }
                groups.forEach { group ->
                    val expanded = group.key == expandedKey
                    item(key = "g-${group.key}") {
                        GroupHeader(
                            group,
                            expanded = expanded,
                            onToggle = { onToggle(group.key) },
                            onYes = { onYes(group.items) },
                            onNo = { onNo(group.items) },
                        )
                    }
                    if (expanded) {
                        itemsIndexed(group.items, key = { _, s -> "i-${s.withdrawal.id}-${s.deposit.id}" }) { i, s ->
                            SuggestionRow(
                                s,
                                last = i == group.items.lastIndex,
                                onYes = { onYes(listOf(s)) },
                                onNo = { onNo(listOf(s)) },
                            )
                        }
                    }
                    item(key = "gap-${group.key}") { Spacer(Modifier.height(10.dp)) }
                }
            }
        }
    }
}

/** سر یک الگو: «ملی ← ملی»، تعداد و جمع، «همه آره»؛ زیرش «دیدن تک‌تک‌شون» و «هیچ‌کدوم نه» */
@Composable
private fun GroupHeader(
    group: TransferGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onYes: () -> Unit,
    onNo: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val t = JibitoTheme.colors
    val unknown = stringResource(R.string.bank_unknown)
    val shape = if (expanded) RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp) else RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .cardBackground(shape, t.sheet, t.border)
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(t.transferBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(DesignIcons.Transfer, contentDescription = null, tint = t.transferFg, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    shortBankName(group.from?.name ?: unknown) + " ← " + shortBankName(group.to?.name ?: unknown),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Jalali.toPersianDigits(
                        stringResource(R.string.transfers_group_meta, group.items.size, amount(Money.compact(group.totalRial)))
                    ),
                    fontSize = 12.sp,
                    color = t.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            PillButton(stringResource(R.string.transfers_group_yes), filled = true, onClick = onYes)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinkText(stringResource(if (expanded) R.string.transfers_hide_items else R.string.transfers_show_items), t.transferFg, onToggle)
            Spacer(Modifier.weight(1f))
            LinkText(stringResource(R.string.transfers_group_no), t.muted, onNo)
        }
    }
}

/** یک جفت داخل الگوی باز: مبلغ، زمان برداشت و واریز، «آره / نه» */
@Composable
private fun SuggestionRow(s: TransferSuggestion, last: Boolean, onYes: () -> Unit, onNo: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val t = JibitoTheme.colors
    val shape = if (last) RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp) else RoundedCornerShape(0.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .cardBackground(shape, t.sheet, t.border)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                amount(Money.tomanNumber(s.withdrawal.transaction.amountRial)) + " " + stringResource(R.string.unit_toman),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
                maxLines = 1,
            )
            Text(
                stringResource(R.string.transfers_item_when, Jalali.format(s.withdrawal.dateMillis), Jalali.time(s.deposit.dateMillis)),
                fontSize = 11.sp,
                color = t.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        PillButton(stringResource(R.string.transfer_yes_short), filled = true, onClick = onYes)
        Spacer(Modifier.width(6.dp))
        PillButton(stringResource(R.string.transfer_no), filled = false, onClick = onNo)
    }
}

/** زمینه‌ی کارت با خط دور؛ تکه‌های یک الگوی باز با همین، یک کارت پیوسته دیده می‌شوند */
private fun Modifier.cardBackground(shape: Shape, bg: Color, border: Color) =
    this
        .clip(shape)
        .background(bg)
        .border(1.dp, border, shape)

@Composable
private fun PillButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val t = JibitoTheme.colors
    Box(
        modifier
            .heightIn(min = if (large) 48.dp else 36.dp)
            .clip(RoundedCornerShape(if (large) 16.dp else 12.dp))
            .background(if (filled) t.btnBg else t.chip)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (large) 16.dp else 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = if (large) 15.sp else 13.sp,
            fontWeight = FontWeight.Black,
            color = if (filled) t.btnFg else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
    }
}

@Composable
private fun LinkText(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 10.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

/** همه جواب داده شد */
@Composable
private fun AllReviewed(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mascot(130.dp, MascotFace.HAPPY)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.transfers_done_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.transfers_done_body),
            fontSize = 15.sp,
            lineHeight = 26.sp,
            textAlign = TextAlign.Center,
            color = JibitoTheme.colors.muted,
        )
        Spacer(Modifier.height(20.dp))
        PillButton(stringResource(R.string.review_back), filled = true, onClick = onBack, large = true)
    }
}
