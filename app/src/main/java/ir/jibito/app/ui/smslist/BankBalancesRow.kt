package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.domain.BankBalance
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/** مانده‌ای که از این قدیمی‌تر باشد، کم‌رنگ نشان داده می‌شود (ممکن است دیگر درست نباشد) */
private const val STALE_DAYS = 30
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * «چقد دارم؟»: ردیف افقی کارت‌ها — آخرین مانده‌ی هر بانک، از خود پیامک‌ها (بدون هیچ کار کاربر).
 * اگر بیش از یک بانک باشد، اولین کارت جمع مانده‌هاست.
 */
@Composable
fun BankBalancesRow(balances: List<BankBalance>) {
    val colors = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    Column {
        Text(
            stringResource(R.string.balances_title),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = colors.onBackground,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            if (balances.size > 1) {
                item(key = "total") {
                    BalanceCard(
                        title = stringResource(R.string.balances_total),
                        amountRial = balances.sumOf { it.balanceRial },
                        subtitle = stringResource(R.string.balances_total_hint, balances.size),
                        highlighted = true,
                        stale = false,
                    )
                }
            }
            items(balances, key = { it.bank.id }) { b ->
                val ageDays = ((now - b.dateMillis) / DAY_MILLIS).toInt()
                BalanceCard(
                    title = b.bank.name,
                    amountRial = b.balanceRial,
                    subtitle = when {
                        ageDays <= 0 -> stringResource(R.string.balances_today)
                        ageDays == 1 -> stringResource(R.string.balances_yesterday)
                        ageDays < STALE_DAYS -> Jalali.toPersianDigits(stringResource(R.string.balances_days_ago, ageDays))
                        else -> Jalali.format(b.dateMillis).substringBefore(" ")
                    },
                    highlighted = false,
                    stale = ageDays >= STALE_DAYS,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun BalanceCard(title: String, amountRial: Long, subtitle: String, highlighted: Boolean, stale: Boolean) {
    val colors = MaterialTheme.colorScheme
    val container = if (highlighted) colors.primary else colors.surface
    val content = if (highlighted) colors.onPrimary else colors.onSurface
    val muted = if (highlighted) colors.onPrimary.copy(alpha = 0.8f) else colors.onSurfaceVariant
    Column(
        Modifier
            .widthIn(min = 140.dp, max = 220.dp)
            .alpha(if (stale) 0.6f else 1f)
            .background(container, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                Money.tomanNumber(amountRial),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = content,
                maxLines = 1,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(R.string.unit_toman),
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            maxLines = 1,
        )
    }
}
