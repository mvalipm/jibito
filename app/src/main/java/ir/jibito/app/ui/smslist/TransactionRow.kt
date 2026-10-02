package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.theme.JibitoTheme
import androidx.compose.ui.draw.clip
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import androidx.compose.material3.Icon
import ir.jibito.app.ui.theme.JibitoIcons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/** جای یک ردیف در سطح مشترک روز: فقط ردیف اول و آخر گوشه‌ی گرد دارند (internal: برای تست اسکرین‌شات) */
internal enum class GroupPosition { Single, First, Middle, Last }

internal fun groupPosition(index: Int, size: Int): GroupPosition = when {
    size == 1 -> GroupPosition.Single
    index == 0 -> GroupPosition.First
    index == size - 1 -> GroupPosition.Last
    else -> GroupPosition.Middle
}

private fun GroupPosition.shape(radius: Dp = 20.dp): Shape = when (this) {
    GroupPosition.Single -> RoundedCornerShape(radius)
    GroupPosition.First -> RoundedCornerShape(topStart = radius, topEnd = radius)
    GroupPosition.Last -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    GroupPosition.Middle -> RectangleShape
}

/** سرتیتر چسبان هر روز: «امروز» … «۴۵۰ هزار تومان خرج» */
@Composable
internal fun DayHeader(group: DayGroup, nowMillis: Long = System.currentTimeMillis()) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(start = 6.dp, end = 6.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Jalali.dayTitle(group.dayStartMillis, nowMillis),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = colors.onBackground,
        )
        if (group.spendRial > 0) {
            Text(
                stringResource(R.string.day_spend, Money.compact(group.spendRial)),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * یک تراکنش در فهرست روز: آیکون دسته (یا جهت پول)، عنوان، بانک و ساعت، برچسب دسته؛ مبلغ و مانده در سمت دیگر.
 * رنگ فقط برای پولی است که آمده (سبز)، انتقال به خودم (آبی) یا خرید ناموفق (کم‌رنگ)؛ خرج عادی رنگ متن است.
 */
@Composable
internal fun TransactionRow(sms: Transaction, position: GroupPosition, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val t = sms.transaction
    val bankName = if (sms.isManual) stringResource(R.string.tx_manual_source) else sms.bank?.name ?: stringResource(R.string.bank_unknown)
    val isDeposit = t.type == FlowType.DEPOSIT
    val failed = sms.isFailedPurchase
    val selfTransfer = sms.isSelfTransfer && !failed
    val accent = when {
        failed -> colors.outline
        selfTransfer -> JibitoTheme.colors.transfer
        isDeposit -> JibitoTheme.colors.income
        else -> colors.primary
    }
    val amountColor = when {
        failed -> colors.outline
        selfTransfer -> JibitoTheme.colors.transfer
        isDeposit -> JibitoTheme.colors.income
        else -> colors.onSurface
    }
    val title = when {
        failed -> stringResource(R.string.tx_failed_purchase)
        selfTransfer -> stringResource(R.string.tx_self_transfer)
        sms.isManual -> sms.merchant ?: stringResource(if (isDeposit) R.string.tx_manual_income else R.string.tx_manual_expense)
        // تراکنش پیامکی: عنوان فقط «برداشت» یا «واریز»؛ طرف حساب در خط دوم می‌آید
        isDeposit -> stringResource(R.string.tx_deposit)
        else -> stringResource(R.string.tx_withdrawal)
    }
    val categoryEmoji = sms.categoryIcon?.takeIf { !failed && !selfTransfer && sms.categoryId != null }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(position.shape())
            .background(colors.surface)
            .clickable(onClickLabel = stringResource(R.string.cd_pick_category), onClick = onClick)
    ) {
        if (position == GroupPosition.Middle || position == GroupPosition.Last) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                thickness = 0.8.dp,
                color = colors.outlineVariant.copy(alpha = 0.6f),
            )
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (categoryEmoji != null) colors.surfaceVariant else accent.copy(alpha = 0.13f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (categoryEmoji != null) {
                    Text(categoryEmoji, fontSize = 18.sp)
                } else {
                    Icon(
                        when {
                            failed -> JibitoIcons.Refund
                            selfTransfer -> JibitoIcons.Transfer
                            isDeposit -> JibitoIcons.ArrowDown
                            else -> JibitoIcons.ArrowUp
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        if (!sms.isManual && sms.merchant != null) "${sms.merchant} · $bankName" else bankName,
                        Jalali.time(sms.dateMillis),
                        sms.feeRial?.let { stringResource(R.string.tx_fee, Money.toman(it)) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    failed -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.tx_refunded),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    selfTransfer -> {
                        Spacer(Modifier.height(6.dp))
                        Pill(
                            stringResource(R.string.tx_self_transfer_chip),
                            container = JibitoTheme.colors.transfer.copy(alpha = 0.13f),
                            content = JibitoTheme.colors.transfer,
                            leading = JibitoIcons.Transfer,
                        )
                    }
                    sms.categoryName != null -> {
                        Spacer(Modifier.height(6.dp))
                        Pill(
                            if (sms.isAutoCategorized) stringResource(R.string.tx_auto_category, sms.categoryName) else sms.categoryName,
                            container = colors.primaryContainer,
                            content = colors.onPrimaryContainer,
                        )
                    }
                    sms.suggestedCategory != null -> {
                        Spacer(Modifier.height(6.dp))
                        Pill(
                            stringResource(R.string.tx_suggested_category, sms.suggestedCategory),
                            container = colors.secondaryContainer,
                            content = colors.onSecondaryContainer,
                        )
                    }
                    else -> {
                        Spacer(Modifier.height(6.dp))
                        Pill(
                            stringResource(R.string.tx_add_category),
                            container = colors.primary.copy(alpha = 0.10f),
                            content = colors.primary,
                            leading = JibitoIcons.Plus,
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                // علامت، عدد و «تومان» سه تکه‌ی جدا هستند تا جهت‌نویسی راست‌به‌چپ جای علامت را جابه‌جا نکند.
                // در Row راست‌به‌چپ، اولین تکه سمت راست می‌نشیند: «− ۱۲۵٬۰۰۰ تومان»
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!failed) {
                        Text(
                            text = if (isDeposit) "+" else "−",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = amountColor,
                        )
                        Spacer(Modifier.size(2.dp))
                    }
                    Text(
                        text = Money.tomanNumber(t.amountRial),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = amountColor,
                        textDecoration = if (failed) TextDecoration.LineThrough else null,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.unit_toman),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = amountColor.copy(alpha = 0.8f),
                    )
                }
                t.balanceRial?.takeIf { !failed }?.let { balance ->
                    Text(
                        text = stringResource(R.string.tx_balance, Money.tomanNumber(balance)),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** برچسب کوچک زیر عنوان تراکنش (دسته، پیشنهاد، انتقال) */
@Composable
private fun Pill(text: String, container: Color, content: Color, leading: ImageVector? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(start = if (leading != null) 8.dp else 10.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(4.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
