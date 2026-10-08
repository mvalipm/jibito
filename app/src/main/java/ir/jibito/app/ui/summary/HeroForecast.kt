package ir.jibito.app.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.wallet.BalanceForecast
import ir.jibito.app.ui.reports.daysShortBeforePayday
import ir.jibito.app.ui.reports.dateLabel
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoText
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/**
 * یک خط زیر جمله‌ی حال جیب در سرصفحه‌ی «خلاصه»: «پولت تا حقوق می‌رسه؛ حدود ۱۲ میلیون می‌مونه»
 * یا (کم می‌آید) «با همین ریتم ۲۳ مهر موجودیت تموم می‌شه». لمسش کارت موجودی در «گزارش‌ها» را باز می‌کند.
 * فقط برای ماه جاری و وقتی مبلغ‌ها پنهان نیستند (سرصفحه همین را چک می‌کند).
 */
@Composable
internal fun HeroForecastLine(f: BalanceForecast, onClick: () -> Unit) {
    val t = JibitoTheme.colors
    val text = when {
        f.enough && f.paydayMillis != null -> stringResource(R.string.hero_forecast_ok_salary, Money.compact(f.endRial))
        f.enough -> stringResource(R.string.hero_forecast_ok_month, Money.compact(f.endRial))
        else -> {
            val zero = dateLabel(f.zeroDay!!)
            f.daysShortBeforePayday()?.let { stringResource(R.string.hero_forecast_short_salary, zero, it) }
                ?: stringResource(R.string.hero_forecast_short_month, zero)
        }
    }
    // کم می‌آید: کپسول روشن با متن مرجانی، تا روی هر رنگ سرصفحه خوانا و متفاوت از جمله‌ی بودجه باشد
    // می‌رسد: مثل دکمه‌های سرصفحه، در حالت روشن سیاه کم‌رنگ تا متن سفید کنتراست کافی داشته باشد
    val calmBg = if (t.dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.14f)
    val bg = if (f.enough) calmBg else Color.White.copy(alpha = 0.94f)
    val fg = if (f.enough) Color.White else t.alert
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClickLabel = stringResource(R.string.cd_hero_forecast), role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(10.dp))
        Text(
            Jalali.toPersianDigits(text),
            modifier = Modifier.weight(1f),
            color = fg,
            style = JibitoText.body,
            fontWeight = FontWeight.Bold,
            lineHeight = 21.sp,
        )
        Icon(JibitoIcons.ChevronForward, contentDescription = null, tint = fg.copy(alpha = 0.8f), modifier = Modifier.padding(start = 6.dp).size(18.dp))
    }
}
