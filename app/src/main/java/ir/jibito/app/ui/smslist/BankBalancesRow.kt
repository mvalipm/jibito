package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.domain.BankBalance
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Money

/** مانده‌ای که از این قدیمی‌تر باشد، کم‌رنگ نشان داده می‌شود (ممکن است دیگر درست نباشد) */
private const val STALE_DAYS = 30
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** رنگ نقطه‌ی هر بانک (نزدیک به رنگ خودش)؛ [روشن, تیره] */
private val BANK_COLORS: Map<Int, Pair<Long, Long>> = mapOf(
    11 to (0xFFB91C1C to 0xFFF87171), // ملت
    15 to (0xFF1D4ED8 to 0xFF60A5FA), // سامان
    40 to (0xFF0891B2 to 0xFF22D3EE), // بلو
    1 to (0xFF1E3A8A to 0xFF93C5FD), // ملی
    12 to (0xFFCA8A04 to 0xFFFACC15), // پاسارگاد
    7 to (0xFF1E40AF to 0xFF818CF8), // صادرات
    3 to (0xFF2563EB to 0xFF7DD3FC), // تجارت
    6 to (0xFF15803D to 0xFF4ADE80), // کشاورزی
    24 to (0xFFC2410C to 0xFFFDBA74), // سپه
    14 to (0xFF9F1239 to 0xFFFDA4AF), // پارسیان
)
private val FALLBACK = listOf(0xFF6D3FC0 to 0xFFB79CF2, 0xFF4B6478 to 0xFFA9BCCB, 0xFF8B5A2B to 0xFFD9A878, 0xFF5F6A16 to 0xFFC5D16A)

private fun bankColor(id: Int, dark: Boolean): Color {
    val pair = BANK_COLORS[id] ?: FALLBACK[Math.floorMod(id, FALLBACK.size)]
    return Color(if (dark) pair.second else pair.first)
}

/**
 * «چقد دارم؟»: ردیف افقی کپسول‌ها — نقطه‌ی رنگی بانک، اسمش و آخرین مانده (از خود پیامک‌ها).
 * اگر بیش از یک بانک باشد، اولین کپسول جمع مانده‌هاست. مانده‌ی کهنه کم‌رنگ است.
 */
@Composable
fun BankBalancesRow(balances: List<BankBalance>) {
    val t = JibitoTheme.colors
    val now = System.currentTimeMillis()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (balances.size > 1) {
            item(key = "total") {
                BalanceChip(stringResource(R.string.balances_total), balances.sumOf { it.balanceRial }, null, stale = false)
            }
        }
        items(balances, key = { it.bank.id }) { b ->
            val ageDays = ((now - b.dateMillis) / DAY_MILLIS).toInt()
            BalanceChip(shortBankName(b.bank.name), b.balanceRial, bankColor(b.bank.id, t.dark), stale = ageDays >= STALE_DAYS)
        }
    }
}

@Composable
private fun BalanceChip(name: String, amountRial: Long, dot: Color?, stale: Boolean) {
    val t = JibitoTheme.colors
    val shown = amount(Money.short(amountRial))
    Row(
        Modifier
            .height(44.dp)
            .alpha(if (stale) 0.6f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, t.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp)
            .clearAndSetSemantics { contentDescription = "$name $shown" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
        }
        Text(name, fontSize = 13.sp, color = t.muted, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Text(shown, fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
    }
}
