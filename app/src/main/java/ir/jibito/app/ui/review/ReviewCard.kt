package ir.jibito.app.ui.review

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.repository.ReviewItem
import ir.jibito.app.data.review.NumberToken
import ir.jibito.app.data.review.ReviewDetector
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/**
 * یک پیامک منتظر بررسی.
 * عددهای داخل خود پیامک قابل لمس‌اند: لمس اول = مبلغ، لمس دوم = مانده، لمس سوم = هیچ‌کدام
 * (اگر جای عددها در متن پیدا نشود، همان دکمه‌های جدای عددها نشان داده می‌شوند).
 * زیرش یک کارت خلاصه (مبلغ، نوع، بانک) تا کاربر قبل از تأیید نتیجه را ببیند،
 * و دکمه‌ها همیشه پایین کارت‌اند. «ارسال نمونه» و «این سرشماره بانکی نیست» در منوی ⋯ هستند.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReviewCard(
    item: ReviewItem,
    /** چند پیامک منتظرِ دیگر از همین سرشماره هست */
    sameSenderOthers: Int,
    onConfirm: (FlowType, NumberToken, NumberToken?, Int?) -> Unit,
    onDismiss: (ignoreSender: Boolean) -> Unit,
    onAddInstitution: (String) -> Int?,
    onShare: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val jt = JibitoTheme.colors
    val g = item.guess
    // انتخاب‌ها برای هر پیامک جدا نگه داشته می‌شوند؛ مقدار اول = حدس اپ
    var type by rememberSaveable(item.smsId) { mutableStateOf(g.type) }
    var amountIndex by rememberSaveable(item.smsId) { mutableStateOf(g.amountIndex) }
    var balanceIndex by rememberSaveable(item.smsId) { mutableStateOf(g.balanceIndex) }
    var bankId by rememberSaveable(item.smsId) { mutableStateOf<Int?>(null) }
    var pickingBank by rememberSaveable(item.smsId) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val needsBank = item.bankName == null
    val factor = if (g.inToman) 10 else 1
    val ranges = remember(item.smsId) { ReviewDetector.locate(item.body, g.numbers) }

    /** لمس یک عدد: هیچ‌کدام ← مبلغ ← مانده ← هیچ‌کدام */
    fun tap(i: Int) {
        when (i) {
            amountIndex -> {
                amountIndex = null
                balanceIndex = i
            }
            balanceIndex -> balanceIndex = null
            else -> amountIndex = i
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 12.dp)
        ) {
            // متن پیامک، مثل حباب پیام؛ عددها قابل لمس
            val amountStyle = SpanStyle(background = jt.btnBg, color = jt.btnFg, fontWeight = FontWeight.Black)
            val balanceStyle = SpanStyle(background = jt.tealTint, color = jt.tealTintFg, fontWeight = FontWeight.Black)
            val idleStyle = SpanStyle(background = jt.uncatBg, color = jt.uncatFg, fontWeight = FontWeight.Bold)
            val text = remember(item.smsId, ranges, amountIndex, balanceIndex, jt) {
                buildAnnotatedString {
                    if (ranges == null) {
                        append(item.body)
                    } else {
                        var at = 0
                        ranges.forEachIndexed { i, r ->
                            append(item.body.substring(at, r.first))
                            val style = when (i) {
                                amountIndex -> amountStyle
                                balanceIndex -> balanceStyle
                                else -> idleStyle
                            }
                            withLink(LinkAnnotation.Clickable("n$i") { tap(i) }) {
                                withStyle(style) { append(" " + item.body.substring(r.first, r.last + 1) + " ") }
                            }
                            at = r.last + 1
                        }
                        append(item.body.substring(at))
                    }
                }
            }
            Text(
                text,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (jt.dark) Modifier.border(1.dp, jt.border, BubbleShape) else Modifier.shadow(1.dp, BubbleShape))
                    .clip(BubbleShape)
                    .background(jt.smsBg)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                fontSize = 15.sp,
                lineHeight = 30.sp,
                color = colors.onBackground,
            )
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(item.bankName ?: item.sender, Jalali.format(item.dateMillis)).joinToString(" · ") +
                        if (item.bankName == null) " · " + stringResource(R.string.review_unknown_sender) else "",
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = jt.muted,
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(DesignIcons.Dots, contentDescription = stringResource(R.string.review_more), tint = jt.muted)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.review_share)) },
                            onClick = {
                                menuOpen = false
                                onShare()
                            },
                        )
                        if (item.bankName == null) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(stringResource(R.string.review_ignore_sender))
                                        Text(
                                            if (sameSenderOthers > 0) {
                                                Jalali.toPersianDigits(stringResource(R.string.review_ignore_sender_more, sameSenderOthers))
                                            } else {
                                                stringResource(R.string.review_ignore_sender_hint)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = jt.muted,
                                        )
                                    }
                                },
                                onClick = {
                                    menuOpen = false
                                    onDismiss(true)
                                },
                            )
                        }
                    }
                }
            }

            if (ranges == null) {
                // جای عددها در متن پیدا نشد: دکمه‌های جدا
                SectionTitle(stringResource(R.string.review_pick_amount))
                NumberChips(g.numbers.map { it.raw }, selected = amountIndex, disabled = balanceIndex) { i ->
                    amountIndex = if (amountIndex == i) null else i
                }
                if (g.numbers.size > 1) {
                    SectionTitle(stringResource(R.string.review_pick_balance))
                    NumberChips(g.numbers.map { it.raw }, selected = balanceIndex, disabled = amountIndex) { i ->
                        balanceIndex = if (balanceIndex == i) null else i
                    }
                }
            } else {
                Text(
                    stringResource(if (g.numbers.size > 1) R.string.review_tap_hint else R.string.review_tap_hint_single),
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 12.sp,
                    color = jt.muted,
                )
            }

            // خلاصه‌ی نتیجه
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(jt.sheet)
                    .border(1.dp, jt.border, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                SummaryRow(stringResource(R.string.review_row_amount)) {
                    val chosen = amountIndex
                    Text(
                        if (chosen != null) Money.toman(g.numbers[chosen].value * factor) else stringResource(R.string.review_not_chosen),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = when {
                            chosen == null -> jt.muted
                            type == FlowType.DEPOSIT -> jt.income
                            else -> jt.btnBg
                        },
                    )
                }
                HorizontalDivider(color = jt.border)
                SummaryRow(stringResource(R.string.review_row_type)) {
                    TypeSwitch(type) { type = it }
                }
                balanceIndex?.let { b ->
                    HorizontalDivider(color = jt.border)
                    SummaryRow(stringResource(R.string.review_row_balance)) {
                        Text(Money.toman(g.numbers[b].value * factor), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = jt.tealTintFg)
                    }
                }
                // فرستنده‌ی ناشناس: مال کدام بانک/موسسه است؟ (یک بار؛ از این به بعد خودکار شناخته می‌شود)
                if (needsBank) {
                    HorizontalDivider(color = jt.border)
                    SummaryRow(stringResource(R.string.review_row_bank)) {
                        Text(
                            BankDirectory.byId(bankId)?.name ?: stringResource(R.string.review_pick_bank),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (bankId == null) jt.btnBg else jt.chip)
                                .clickable(role = Role.Button) { pickingBank = true }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (bankId == null) jt.btnFg else colors.onBackground,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.review_learn_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = jt.muted,
                textAlign = TextAlign.Center,
            )
        }

        // دکمه‌ها همیشه پایین
        val chosenType = type
        val chosenAmount = amountIndex
        val ready = chosenType != null && chosenAmount != null && (!needsBank || bankId != null)
        BigPill(
            text = stringResource(if (type == FlowType.DEPOSIT) R.string.review_yes_income else R.string.review_yes_spend),
            container = jt.btnBg,
            content = jt.btnFg,
            bold = true,
            enabled = ready,
        ) {
            if (chosenType != null && chosenAmount != null) {
                onConfirm(chosenType, g.numbers[chosenAmount], balanceIndex?.let { g.numbers[it] }, bankId)
            }
        }
        Text(
            stringResource(R.string.review_not_transaction),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(role = Role.Button) { onDismiss(false) }
                .wrapContentHeight(Alignment.CenterVertically),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
    }

    if (pickingBank) {
        BankPickerDialog(
            senderHint = item.sender,
            onAdd = onAddInstitution,
            onPick = {
                bankId = it
                pickingBank = false
            },
            onDismiss = { pickingBank = false },
        )
    }
}

/** یک ردیف کارت خلاصه: برچسب کم‌رنگ و مقدار */
@Composable
private fun SummaryRow(label: String, value: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp, color = JibitoTheme.colors.muted)
        value()
    }
}

/** «برداشت | واریز»: سوییچ دوقسمتی مثل بقیه‌ی اپ */
@Composable
private fun TypeSwitch(type: FlowType?, onChange: (FlowType) -> Unit) {
    val t = JibitoTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(t.chip)
            .padding(3.dp),
    ) {
        listOf(
            FlowType.WITHDRAWAL to stringResource(R.string.tx_withdrawal),
            FlowType.DEPOSIT to stringResource(R.string.tx_deposit),
        ).forEach { (value, label) ->
            val selected = type == value
            Text(
                label,
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) t.sheet else t.chip)
                    .clickable(role = Role.Tab) { onChange(value) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    !selected -> t.muted
                    value == FlowType.DEPOSIT -> t.income
                    else -> MaterialTheme.colorScheme.onBackground
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberChips(raws: List<String>, selected: Int?, disabled: Int?, onClick: (Int) -> Unit) {
    if (raws.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        raws.forEachIndexed { i, raw ->
            FilterChip(
                selected = selected == i,
                enabled = disabled != i,
                onClick = { onClick(i) },
                label = { Text(Jalali.toPersianDigits(raw), fontWeight = FontWeight.Bold) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

/** حباب پیامک: گوشه‌ی پایینِ سمت راست تیز (مثل پیامی که آمده) */
private val BubbleShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp)

/** دکمه‌ی کپسولی بزرگ (۵۶) طرح «جیبی» */
@Composable
private fun BigPill(text: String, container: Color, content: Color, bold: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .wrapContentHeight(Alignment.CenterVertically),
        color = content,
        fontSize = if (bold) 17.sp else 16.sp,
        fontWeight = if (bold) FontWeight.Black else FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}
