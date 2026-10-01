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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
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
import ir.jibito.app.R
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.sms.TransactionItem
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

@Composable
fun SmsListScreen() {
    val context = LocalContext.current
    // null یعنی «هنوز در حال خواندن»
    val messages by produceState<List<TransactionItem>?>(initialValue = null) {
        value = SmsReader(context).readTransactions()
    }
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
            messages?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = Jalali.toPersianDigits(stringResource(R.string.list_count, it.size)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        val list = messages
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                items(list, key = { it.id }) { sms -> SmsCard(sms) }
            }
        }
    }
}

private val DepositGreen = Color(0xFF1E9E6A)

@Composable
private fun SmsCard(sms: TransactionItem) {
    val colors = MaterialTheme.colorScheme
    val t = sms.transaction
    val isDeposit = t.type == FlowType.DEPOSIT
    val failed = sms.refundDateMillis != null
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
    var expanded by rememberSaveable(sms.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
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
                        text = if (failed && sms.merchant != null) "${sms.merchant} · ${sms.bank.name}" else sms.bank.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = (if (failed) "" else if (isDeposit) "+" else "−") + Money.toman(t.amountRial),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = accent,
                        textDecoration = if (failed) TextDecoration.LineThrough else null,
                    )
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
            if (!failed && sms.suggestedCategory != null) {
                Spacer(Modifier.height(10.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.tx_suggested_category, sms.suggestedCategory)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = colors.secondaryContainer,
                        labelColor = colors.onSecondaryContainer,
                    ),
                    border = null,
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
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = sms.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
