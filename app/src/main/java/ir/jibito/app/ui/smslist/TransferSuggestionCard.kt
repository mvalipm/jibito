package ir.jibito.app.ui.smslist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.domain.TransferSuggestion
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import androidx.compose.material3.Icon
import ir.jibito.app.ui.theme.JibitoIcons

/** کارت پیشنهاد: «این برداشت و این واریز، انتقال بین حساب‌های خودت بود؟» */
@Composable
internal fun TransferSuggestionCard(
    suggestion: TransferSuggestion,
    total: Int,
    onYes: () -> Unit,
    onNo: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val w = suggestion.withdrawal
    val d = suggestion.deposit
    val unknown = stringResource(R.string.bank_unknown)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = JibitoTheme.colors.transfer.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(JibitoIcons.Transfer, contentDescription = null, tint = JibitoTheme.colors.transfer, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.transfer_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = colors.onSurface,
                )
                if (total > 1) {
                    Text(
                        Jalali.toPersianDigits(stringResource(R.string.transfer_count, total)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = JibitoTheme.colors.transfer,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.transfer_explain),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                Money.toman(w.transaction.amountRial),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = JibitoTheme.colors.transfer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.transfer_withdrawal_line, listOfNotNull(w.bank?.name ?: unknown, w.merchant).joinToString(" ← ")) +
                    "  ·  " + Jalali.format(w.dateMillis),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
            )
            Text(
                stringResource(R.string.transfer_deposit_line, d.bank?.name ?: unknown) + "  ·  " + Jalali.format(d.dateMillis),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onYes,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = JibitoTheme.colors.transfer),
                ) { Text(stringResource(R.string.transfer_yes), fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = onNo, shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.transfer_no))
                }
            }
        }
    }
}
