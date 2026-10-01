package ir.jibito.app.ui.review

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.repository.ReviewItem
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

private val DepositGreen = Color(0xFF1E9E6A)

/**
 * «صندوق بررسی»: پیامک‌هایی که شبیه تراکنش‌اند ولی خودکار خوانده نشدند، یکی‌یکی.
 * کاربر با چند لمس تعیین تکلیف می‌کند: نوع + «روی مبلغ بزن» + ثبت، یا «تراکنش نیست».
 */
@Composable
fun ReviewScreen(onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val viewModel: ReviewViewModel = viewModel(factory = ReviewViewModel.factory(app.container.reviewRepository))
    val pending by viewModel.pending.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
    ) {
        // سرصفحه
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.review_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = colors.onBackground,
                )
                pending?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        Jalali.toPersianDigits(stringResource(R.string.review_count, it.size)),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.review_later)) }
        }

        val list = pending
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> AllDone(onClose)
            else -> ReviewCard(
                item = list.first(),
                onConfirm = { type, amount, balance -> viewModel.confirm(list.first(), type, amount, balance) },
                onDismiss = { ignore -> viewModel.dismiss(list.first(), ignore) },
                onShare = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, viewModel.shareText(list.first()))
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.review_share_title)))
                },
            )
        }
    }
}

@Composable
private fun AllDone(onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✓", fontSize = 48.sp, color = DepositGreen, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.review_all_done),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onClose, shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.review_back)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(
    item: ReviewItem,
    onConfirm: (FlowType, Long, Long?) -> Unit,
    onDismiss: (ignoreSender: Boolean) -> Unit,
    onShare: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val g = item.guess
    // انتخاب‌ها برای هر پیامک جدا نگه داشته می‌شوند؛ مقدار اول = حدس اپ
    var type by rememberSaveable(item.smsId) { mutableStateOf(g.type) }
    var amountIndex by rememberSaveable(item.smsId) { mutableStateOf(g.amountIndex) }
    var balanceIndex by rememberSaveable(item.smsId) { mutableStateOf(g.balanceIndex) }
    var ignoreSender by rememberSaveable(item.smsId) { mutableStateOf(false) }
    val factor = if (g.inToman) 10 else 1

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            stringResource(R.string.review_explain),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        // متن پیامک
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(20.dp))
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

        // نوع
        SectionTitle(stringResource(R.string.review_type))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeChip(stringResource(R.string.tx_withdrawal), "↑", type == FlowType.WITHDRAWAL, colors.primary) { type = FlowType.WITHDRAWAL }
            TypeChip(stringResource(R.string.tx_deposit), "↓", type == FlowType.DEPOSIT, DepositGreen) { type = FlowType.DEPOSIT }
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
                color = if (type == FlowType.DEPOSIT) DepositGreen else colors.primary,
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
        Button(
            onClick = {
                if (chosenType != null && chosenAmount != null) {
                    onConfirm(chosenType, g.numbers[chosenAmount].value, balanceIndex?.let { g.numbers[it].value })
                }
            },
            enabled = chosenType != null && chosenAmount != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(stringResource(R.string.review_confirm), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { onDismiss(ignoreSender) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(stringResource(R.string.review_not_transaction))
        }
        if (item.bankName == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = ignoreSender, onCheckedChange = { ignoreSender = it })
                Text(
                    stringResource(R.string.review_ignore_sender),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
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
private fun TypeChip(label: String, icon: String, selected: Boolean, color: Color, onClick: () -> Unit) {
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
