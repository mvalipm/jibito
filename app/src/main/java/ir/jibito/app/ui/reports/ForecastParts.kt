package ir.jibito.app.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.wallet.BalanceForecast
import ir.jibito.app.data.wallet.ForecastBasis
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** اسم ماهِ یک روز («مهر») */
internal fun monthNameOf(dayMillis: Long): String = Jalali.MONTH_NAMES[JalaliMonth.of(dayMillis).month - 1]

/** چند روز قبل از حقوق پول تمام می‌شود؛ null وقتی تمام نمی‌شود یا حقوقی نیست */
internal fun BalanceForecast.daysShortBeforePayday(): Int? {
    val zero = zeroDay ?: return null
    val payday = paydayMillis ?: return null
    return ((payday - zero + DAY_MILLIS / 2) / DAY_MILLIS).toInt()
}

/**
 * جمله‌ی پیش‌بینی زیر نمودار موجودی: «تا حقوقِ ۱ آبان حدود ۱۲ میلیون می‌مونه» یا «۲۳ مهر موجودیت تموم می‌شه».
 * رنگش هم حال پیش‌بینی است (فیروزه‌ای: می‌رسد، مرجانی: کم می‌آید).
 */
@Composable
internal fun ForecastNote(f: BalanceForecast) {
    val t = JibitoTheme.colors
    val lastDay = f.days.last()
    val text = when {
        f.enough -> {
            val main = f.paydayMillis?.let { stringResource(R.string.forecast_ok_salary, dateLabel(it), amount(Money.compact(f.endRial))) }
                ?: stringResource(R.string.forecast_ok_month, monthNameOf(lastDay), amount(Money.compact(f.endRial)))
            // کف وسط راه (مثلاً بعد از قسط) جدا گفته می‌شود؛ اگر همان روز آخر است، تکراری است
            if (f.lowDay != lastDay && f.lowRial < f.endRial) {
                main + " " + stringResource(R.string.forecast_low, dateLabel(f.lowDay), amount(Money.compact(f.lowRial)))
            } else main
        }
        else -> {
            val zero = dateLabel(f.zeroDay!!)
            f.daysShortBeforePayday()?.let { stringResource(R.string.forecast_short_salary, zero, it) }
                ?: stringResource(R.string.forecast_short_month, zero, monthNameOf(lastDay), amount(Money.compact(-f.endRial)))
        }
    }
    Note(Jalali.toPersianDigits(text), bg = if (f.enough) t.sugBg else t.uncatBg, fg = if (f.enough) t.sugFg else t.uncatFg)
}

/**
 * «چطور حساب شد؟»: موجودی امروز، خرج روزمره‌ی پیش رو، پرداخت‌های ثابت، آنچه می‌ماند و حقوق؛
 * با دکمه‌ی «این حقوقم نیست» برای کسی که اشتباهی حقوق را تأیید کرده.
 */
@Composable
internal fun ForecastBreakdown(f: BalanceForecast, onNotSalary: (() -> Unit)?, startOpen: Boolean = false) {
    val t = JibitoTheme.colors
    var open by rememberSaveable { mutableStateOf(startOpen) }
    Text(
        stringResource(if (open) R.string.forecast_how_close else R.string.forecast_how),
        modifier = Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { open = !open }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        fontSize = 12.sp,
        color = t.muted,
        textDecoration = TextDecoration.Underline,
    )
    if (!open) return
    val days = f.days.size - 1
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BreakdownRow(stringResource(R.string.forecast_row_today), stringResource(R.string.forecast_row_today_note), f.startRial, null)
        BreakdownRow(
            stringResource(R.string.forecast_row_routine),
            Jalali.toPersianDigits(
                if (f.basis == ForecastBasis.PATTERN) {
                    stringResource(R.string.forecast_row_routine_pattern, days, Jalali.MONTH_NAMES[JalaliMonth.of(f.days.first()).plus(-1).month - 1])
                } else {
                    stringResource(R.string.forecast_row_routine_rhythm, days)
                }
            ),
            -f.routineRial,
            null,
        )
        f.payments.forEach { p ->
            BreakdownRow(
                stringResource(R.string.forecast_row_payment, p.title, dateLabel(p.dayMillis)),
                stringResource(R.string.forecast_row_payment_note),
                -p.amountRial,
                null,
            )
        }
        HorizontalDivider(color = t.border, thickness = 1.dp)
        BreakdownRow(
            stringResource(if (f.paydayMillis != null) R.string.forecast_row_end_salary else R.string.forecast_row_end_month),
            null,
            f.endRial,
            if (f.endRial < 0) t.alert else null,
            bold = true,
        )
        val salary = f.salary
        val payday = f.paydayMillis
        if (salary != null && payday != null) {
            BreakdownRow(
                stringResource(R.string.forecast_row_salary, dateLabel(payday)),
                stringResource(R.string.forecast_row_salary_note),
                salary.amountRial,
                t.income,
            )
            if (onNotSalary != null) {
                Text(
                    stringResource(R.string.forecast_not_salary),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onNotSalary)
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.navOn,
                )
            }
        }
        Text(stringResource(R.string.forecast_footnote), fontSize = 11.sp, lineHeight = 18.sp, color = t.faint)
    }
}

@Composable
private fun BreakdownRow(label: String, note: String?, rial: Long, color: Color?, bold: Boolean = false) {
    val t = JibitoTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(label, fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            if (note != null) Text(note, fontSize = 11.sp, lineHeight = 17.sp, color = t.muted)
        }
        val sign = if (rial > 0 && color == t.income) "+" else if (rial < 0) "−" else ""
        Text(
            amount(sign + Money.compact(kotlin.math.abs(rial))),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
