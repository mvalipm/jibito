package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.R
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.JibitoApplication
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

@Composable
fun SmsListScreen() {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val viewModel: TransactionsViewModel = viewModel(
        factory = TransactionsViewModel.factory(app.container.transactionRepository)
    )
    // null یعنی «هنوز چیزی از دیتابیس نیامده»
    val messages by viewModel.transactions.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
            Text(
                text = stringResource(R.string.list_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
            )
            messages?.takeIf { it.isNotEmpty() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = Jalali.toPersianDigits(stringResource(R.string.list_count, it.size)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (isSyncing && !messages.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.list_syncing),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        val list = messages
        when {
            list == null || (list.isEmpty() && isSyncing) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.list_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            list.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { sms -> SmsCard(sms, onClick = { selectedId = sms.id }) }
            }
        }
    }

    // برگه‌ی انتخاب دسته (از پایین صفحه)
    val selected = messages?.firstOrNull { it.id == selectedId }
    if (selected != null) {
        CategoryPickerSheet(
            transaction = selected,
            categories = categories,
            onPick = { categoryId ->
                viewModel.setCategory(selected.id, categoryId)
                selectedId = null
            },
            onDismiss = { selectedId = null },
        )
    }
}

private val DepositGreen = Color(0xFF1E9E6A)

@Composable
private fun SmsCard(sms: Transaction, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val t = sms.transaction
    val bankName = sms.bank?.name ?: stringResource(R.string.bank_unknown)
    val isDeposit = t.type == FlowType.DEPOSIT
    val failed = sms.isFailedPurchase
    val accent = when {
        failed -> colors.outline
        isDeposit -> DepositGreen
        else -> colors.primary
    }
    val title = when {
        failed -> stringResource(R.string.tx_failed_purchase)
        sms.merchant != null -> stringResource(R.string.tx_purchase_from, sms.merchant)
        isDeposit -> stringResource(R.string.tx_deposit)
        else -> stringResource(R.string.tx_withdrawal)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(accent.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            failed -> "↺"
                            isDeposit -> "↓"
                            else -> "↑"
                        },
                        color = accent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Text(
                        text = if (failed && sms.merchant != null) "${sms.merchant} · ${bankName}" else bankName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    // علامت، عدد و «تومان» سه تکه‌ی جدا هستند تا جهت‌نویسی راست‌به‌چپ جای علامت را جابه‌جا نکند.
                    // در Row راست‌به‌چپ، اولین تکه سمت راست می‌نشیند: «− ۱۲۵٬۰۰۰ تومان»
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!failed) {
                            Text(
                                text = if (isDeposit) "+" else "−",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = accent,
                            )
                            Spacer(Modifier.size(2.dp))
                        }
                        Text(
                            text = Money.tomanNumber(t.amountRial),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = accent,
                            textDecoration = if (failed) TextDecoration.LineThrough else null,
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = stringResource(R.string.unit_toman),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                    }
                    Text(
                        text = Jalali.format(sms.dateMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (failed) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.tx_refunded),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            if (!failed && sms.categoryName != null) {
                Spacer(Modifier.height(10.dp))
                AssistChip(
                    onClick = onClick,
                    label = { Text(sms.categoryName, fontWeight = FontWeight.Bold) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = colors.primaryContainer,
                        labelColor = colors.onPrimaryContainer,
                    ),
                    border = null,
                )
            } else if (!failed && sms.suggestedCategory != null) {
                Spacer(Modifier.height(10.dp))
                AssistChip(
                    onClick = onClick,
                    label = { Text(stringResource(R.string.tx_suggested_category, sms.suggestedCategory)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = colors.secondaryContainer,
                        labelColor = colors.onSecondaryContainer,
                    ),
                    border = null,
                )
            } else if (!failed && !isDeposit) {
                Spacer(Modifier.height(10.dp))
                AssistChip(
                    onClick = onClick,
                    label = { Text(stringResource(R.string.tx_add_category)) },
                )
            }
            t.balanceRial?.takeIf { !failed }?.let { balance ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.tx_balance, Money.toman(balance)),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
