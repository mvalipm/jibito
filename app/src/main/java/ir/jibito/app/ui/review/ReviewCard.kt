package ir.jibito.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.theme.JibitoTheme
import androidx.compose.foundation.layout.size
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.review.NumberToken
import ir.jibito.app.data.repository.ReviewItem
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import ir.jibito.app.ui.theme.JibitoIcons

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
    val g = item.guess
    // انتخاب‌ها برای هر پیامک جدا نگه داشته می‌شوند؛ مقدار اول = حدس اپ
    var type by rememberSaveable(item.smsId) { mutableStateOf(g.type) }
    var amountIndex by rememberSaveable(item.smsId) { mutableStateOf(g.amountIndex) }
    var balanceIndex by rememberSaveable(item.smsId) { mutableStateOf(g.balanceIndex) }
    var ignoreSender by rememberSaveable(item.smsId) { mutableStateOf(false) }
    var bankId by rememberSaveable(item.smsId) { mutableStateOf<Int?>(null) }
    var pickingBank by rememberSaveable(item.smsId) { mutableStateOf(false) }
    val needsBank = item.bankName == null
    val factor = if (g.inToman) 10 else 1

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {

        // متن پیامک
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.bankName ?: item.sender,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    Jalali.format(item.dateMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (item.bankName == null) {
                Text(
                    stringResource(R.string.review_unknown_sender),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(item.body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        }

        // فرستنده‌ی ناشناس: مال کدام بانک/موسسه است؟ (یک بار؛ از این به بعد خودکار شناخته می‌شود)
        if (needsBank) {
            SectionTitle(stringResource(R.string.review_which_bank))
            OutlinedButton(
                onClick = { pickingBank = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    BankDirectory.byId(bankId)?.name ?: stringResource(R.string.review_pick_bank),
                    fontWeight = if (bankId != null) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        // نوع
        SectionTitle(stringResource(R.string.review_type))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeChip(stringResource(R.string.tx_withdrawal), JibitoIcons.ArrowUp, type == FlowType.WITHDRAWAL, colors.primary) { type = FlowType.WITHDRAWAL }
            TypeChip(stringResource(R.string.tx_deposit), JibitoIcons.ArrowDown, type == FlowType.DEPOSIT, JibitoTheme.colors.income) { type = FlowType.DEPOSIT }
        }

        // مبلغ
        SectionTitle(stringResource(R.string.review_pick_amount))
        NumberChips(g.numbers.map { it.raw }, selected = amountIndex, disabled = balanceIndex) { i ->
            amountIndex = if (amountIndex == i) null else i
        }
        amountIndex?.let { i ->
            Text(
                "= " + Money.toman(g.numbers[i].value * factor),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (type == FlowType.DEPOSIT) JibitoTheme.colors.income else colors.primary,
            )
        }

        // مانده (اختیاری)
        if (g.numbers.size > 1) {
            SectionTitle(stringResource(R.string.review_pick_balance))
            NumberChips(g.numbers.map { it.raw }, selected = balanceIndex, disabled = amountIndex) { i ->
                balanceIndex = if (balanceIndex == i) null else i
            }
        }

        Spacer(Modifier.height(22.dp))
        val chosenType = type
        val chosenAmount = amountIndex
        val ready = chosenType != null && chosenAmount != null && (!needsBank || bankId != null)
        Button(
            onClick = {
                if (chosenType != null && chosenAmount != null) {
                    onConfirm(chosenType, g.numbers[chosenAmount], balanceIndex?.let { g.numbers[it] }, bankId)
                }
            },
            enabled = ready,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(stringResource(R.string.review_confirm), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            stringResource(R.string.review_learn_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(14.dp))
        // فرستنده‌ی ناشناس: «این سرشماره اصلاً بانکی نیست» ← همه‌ی پیامک‌هایش با یک لمس کنار می‌روند
        if (item.bankName == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .clickable { ignoreSender = !ignoreSender }
                    .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = ignoreSender, onCheckedChange = { ignoreSender = it })
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.review_ignore_sender),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Text(
                        if (sameSenderOthers > 0) {
                            Jalali.toPersianDigits(stringResource(R.string.review_ignore_sender_more, sameSenderOthers))
                        } else {
                            stringResource(R.string.review_ignore_sender_hint)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = { onDismiss(ignoreSender) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(stringResource(R.string.review_not_transaction))
        }

        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onShare, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.review_share))
        }
        Text(
            stringResource(R.string.review_share_hint),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.review_explain),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun TypeChip(label: String, icon: ImageVector, selected: Boolean, color: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Bold) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = RoundedCornerShape(14.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
            iconColor = color,
        ),
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
