package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import java.util.Locale
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.common.BankLogos
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Image
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.common.amount
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import androidx.compose.material3.Icon
import ir.jibito.app.ui.theme.JibitoIcons
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import ir.jibito.app.ui.common.rememberFitScale

/**
 * سربرگ برگه: کاشی بانک (لوگوی رنگی، یا آیکون خطی اگر لوگو نداریم) کنار طرف حساب/بانک و روز و ساعت،
 * دکمه‌ی کوچک «پیامک»، مبلغ درشت، و زیرش نوع تراکنش و مانده‌ی حساب (اگر پیامک داشت).
 */
@Composable
internal fun SheetHeader(transaction: Transaction, isDeposit: Boolean, showSms: Boolean, onToggleSms: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val jt = JibitoTheme.colors
    val t = transaction.transaction
    val bankName = transaction.bank?.name?.let(::shortBankName)
    val manualSource = stringResource(R.string.tx_manual_source)
    val title = transaction.merchant ?: transaction.bank?.name ?: manualSource.takeIf { transaction.isManual }.orEmpty()
    val subtitle = listOfNotNull(
        bankName?.takeIf { transaction.merchant != null },
        "${Jalali.dayTitle(transaction.dateMillis)} · ${Jalali.time(transaction.dateMillis)}",
    ).joinToString(" · ")

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (transaction.bank != null) {
            BankBadge(transaction.bank.id)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = jt.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (transaction.body.isNotBlank()) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (showSms) jt.chip else Color.Transparent)
                    .border(1.dp, jt.border, RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleSms)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DesignIcons.Message, contentDescription = null, tint = colors.primary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(if (showSms) R.string.sheet_hide_sms else R.string.sheet_show_sms),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
            }
        }
    }

    // مبلغ بزرگ با فونت درشت گوشی: همه کمی کوچک می‌شوند تا در یک خط بمانند
    BoxWithConstraints(Modifier.padding(top = 10.dp).fillMaxWidth()) {
        val sign = if (isDeposit) "+" else "−"
        val number = amount(Money.tomanNumber(t.amountRial))
        val toman = stringResource(R.string.unit_toman)
        val big = LocalTextStyle.current.merge(TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Black))
        val small = LocalTextStyle.current.merge(TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium))
        val fit = rememberFitScale(maxWidth, sign to big, number to big, toman to small, gap = 6.dp)
        Row(verticalAlignment = Alignment.Bottom) {
            val amountColor = if (isDeposit) jt.income else colors.onSurface
            Text(sign, fontSize = 30.sp * fit, fontWeight = FontWeight.Black, color = amountColor, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(6.dp))
            Text(number, fontSize = 30.sp * fit, fontWeight = FontWeight.Black, color = amountColor, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(6.dp))
            Text(
                toman,
                fontSize = 15.sp * fit,
                fontWeight = FontWeight.Medium,
                color = jt.muted,
                modifier = Modifier.padding(bottom = 7.dp * fit),
                maxLines = 1,
                softWrap = false,
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            (if (isDeposit) "↓ " else "↑ ") + stringResource(if (isDeposit) R.string.tx_deposit else R.string.tx_withdrawal),
            modifier = Modifier
                .background(if (isDeposit) jt.tealTint else jt.chip, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            color = if (isDeposit) jt.income else colors.onSurface,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
        )
        t.balanceRial?.let { balance ->
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.tx_balance, amount(Money.toman(balance))),
                fontSize = 12.sp,
                color = jt.muted,
            )
        }
    }
}

/** کاشی ۴۴ تایی بانک: لوگوی رنگی روی زمینه‌ی ساده؛ بانکی که لوگو ندارد ← آیکون خطی بانک */
@Composable
private fun BankBadge(bankId: Int) {
    val jt = JibitoTheme.colors
    val logo = BankLogos.of(bankId)
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(44.dp)
            .clip(shape)
            .background(if (logo != null && !jt.dark) Color.White else jt.chip)
            .then(if (logo != null) Modifier.border(1.dp, jt.border, shape) else Modifier)
            .padding(if (logo != null) 6.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(painterResource(logo), contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(DesignIcons.Bank, contentDescription = null, tint = jt.muted, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * کارت پیامک: اول چیزهایی که اپ از پیامک فهمید (مبلغ، زمان، کارمزد، مانده)، بعد متن خام پیامک
 * (چپ‌چین و با فونت ثابت تا عددها به‌هم نریزند)، و آخر «اشتباه خونده شده؟» با یادداشت حریم خصوصی.
 */
@Composable
internal fun SmsReceipt(transaction: Transaction) {
    val colors = MaterialTheme.colorScheme
    val jt = JibitoTheme.colors
    val t = transaction.transaction
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(jt.chip)
            .border(1.dp, jt.border, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        val rows = listOfNotNull(
            stringResource(R.string.sheet_receipt_amount) to stringResource(R.string.sheet_receipt_rial, amount(rialNumber(t.amountRial))),
            stringResource(R.string.sheet_receipt_time) to Jalali.format(transaction.dateMillis),
            transaction.feeRial?.let { stringResource(R.string.sheet_receipt_fee) to stringResource(R.string.sheet_receipt_rial, amount(rialNumber(it))) },
            t.balanceRial?.let { stringResource(R.string.sheet_receipt_balance) to stringResource(R.string.sheet_receipt_rial, amount(rialNumber(it))) },
        )
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 13.sp, color = jt.muted)
                Spacer(Modifier.weight(1f))
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Spacer(Modifier.width(6.dp))
                Icon(DesignIcons.Check, contentDescription = null, tint = jt.income, modifier = Modifier.size(14.dp))
            }
        }

        // متن خام پیامک: چپ‌به‌راست، چون بیشترش عدد است
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = transaction.body,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(jt.sheet, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = colors.onSurfaceVariant,
            )
        }

        // مبلغ یا نوع اشتباه خوانده شده؟ اول «چه چیزی غلطه؟»، بعد گزارش (با رقم‌های پوشیده) برای بهتر شدن پارسر
        if (!transaction.isManual) {
            var reporting by rememberSaveable { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sheet_report_wrong),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { reporting = true }
                        .padding(vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
                Spacer(Modifier.weight(1f))
                Icon(JibitoIcons.Lock, contentDescription = null, tint = jt.muted, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.sheet_report_privacy), fontSize = 11.sp, color = jt.muted)
            }
            if (reporting) {
                WrongReadingDialog(transaction, onDismiss = { reporting = false })
            }
        }
    }
}

/** مبلغ ریالی با جداکننده‌ی هزارگان، همان واحدی که پیامک گفته (مثلاً ۳۰٬۰۰۰٬۰۰۰) */
private fun rialNumber(rial: Long): String =
    Jalali.toPersianDigits(String.format(Locale.US, "%,d", rial).replace(',', '٬'))
