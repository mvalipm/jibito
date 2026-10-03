package ir.jibito.app.ui.smslist

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.domain.TransferSuggestion
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/**
 * کارت فشرده‌ی پیشنهاد: «انتقال به حساب خودت بوده؟» با مبلغ و دو بانک در یک خط و دکمه‌های «آره / نه».
 * «جزئیات» توضیح و تاریخ برداشت و واریز را باز می‌کند (تا کارت جای اولین تراکنش‌ها را نگیرد).
 */
@Composable
internal fun TransferSuggestionCard(
    suggestion: TransferSuggestion,
    total: Int,
    onYes: () -> Unit,
    onNo: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val t = JibitoTheme.colors
    val w = suggestion.withdrawal
    val d = suggestion.deposit
    val unknown = stringResource(R.string.bank_unknown)
    var expanded by rememberSaveable(w.id, d.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(t.transfer.copy(alpha = 0.10f))
            .animateContentSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(t.transferBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(JibitoIcons.Transfer, contentDescription = null, tint = t.transfer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.transfer_question_short),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        amount(Money.tomanNumber(w.transaction.amountRial)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = t.transfer,
                        maxLines = 1,
                    )
                    Text(
                        " · " + shortBankName(w.bank?.name ?: unknown) + " ← " + shortBankName(d.bank?.name ?: unknown),
                        fontSize = 12.sp,
                        color = t.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    listOfNotNull(
                        if (total > 1) Jalali.toPersianDigits(stringResource(R.string.transfer_progress, total)) else null,
                        stringResource(if (expanded) R.string.transfer_less else R.string.transfer_details),
                    ).joinToString(" · "),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 2.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.transfer,
                )
            }
            Spacer(Modifier.width(8.dp))
            SmallButton(stringResource(R.string.transfer_yes_short), filled = true, onClick = onYes)
            Spacer(Modifier.width(6.dp))
            SmallButton(stringResource(R.string.transfer_no), filled = false, onClick = onNo)
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.transfer_explain), fontSize = 12.sp, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.transfer_withdrawal_line, listOfNotNull(w.bank?.name ?: unknown, w.merchant).joinToString(" ← ")) +
                    "  ·  " + Jalali.format(w.dateMillis),
                fontSize = 12.sp,
                color = colors.onSurface,
            )
            Text(
                stringResource(R.string.transfer_deposit_line, d.bank?.name ?: unknown) + "  ·  " + Jalali.format(d.dateMillis),
                fontSize = 12.sp,
                color = colors.onSurface,
            )
        }
    }
}

@Composable
private fun SmallButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val t = JibitoTheme.colors
    val shape = RoundedCornerShape(11.dp)
    Box(
        Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 36.dp)
            .clip(shape)
            .then(if (filled) Modifier.background(t.transfer) else Modifier.border(1.dp, t.border, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (filled) MaterialTheme.colorScheme.surface else t.muted,
        )
    }
}
