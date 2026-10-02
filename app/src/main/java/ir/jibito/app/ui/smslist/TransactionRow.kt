package ir.jibito.app.ui.smslist

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Transaction
import ir.jibito.app.ui.summary.ChartColors
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/** سرتیتر چسبان هر روز: «امروز» … «۴۵۰ هزار تومان خرج» */
@Composable
internal fun DayHeader(group: DayGroup, nowMillis: Long = System.currentTimeMillis()) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 6.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Jalali.dayTitle(group.dayStartMillis, nowMillis),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
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
 * یک تراکنش در فهرست روز، بدون کارت و برچسب: آواتار دسته، اسم طرف حساب، یک خط توضیح، مبلغ.
 * - عنوان اسم فروشگاه/طرف حساب است (همان چیزی که کاربر دنبالش است)؛ نوع تراکنش را آواتار و علامت مبلغ می‌گویند.
 * - آواتار به رنگ دسته‌ی اصلی است؛ بی‌دسته‌ها یک «؟» خط‌چین دارند که آرام می‌تپد.
 * - «تومان» یک بار در سرتیتر روز می‌آید، نه در هر ردیف (صفحه‌خوان هنوز «… تومان» می‌خواند).
 * - رنگ مبلغ فقط برای پولی است که آمده (سبز)؛ انتقال به خودم و خرید ناموفق کم‌رنگ‌اند.
 *
 * @param onAcceptSuggestion «آره» روی پیشنهاد دسته؛ null یعنی دکمه‌ی پیشنهاد نشان داده نشود
 */
@Composable
internal fun TransactionRow(
    sms: Transaction,
    onClick: () -> Unit,
    onAcceptSuggestion: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val t = sms.transaction
    val bankName = if (sms.isManual) stringResource(R.string.tx_manual_source) else sms.bank?.name ?: stringResource(R.string.bank_unknown)
    val isDeposit = t.type == FlowType.DEPOSIT
    val failed = sms.isFailedPurchase
    val selfTransfer = sms.isSelfTransfer && !failed
    val uncategorized = !failed && !selfTransfer && sms.categoryId == null
    val muted = failed || selfTransfer

    val title = when {
        selfTransfer -> stringResource(R.string.tx_self_transfer)
        sms.merchant != null -> sms.merchant
        failed -> stringResource(R.string.tx_failed_purchase)
        sms.isManual -> stringResource(if (isDeposit) R.string.tx_manual_income else R.string.tx_manual_expense)
        isDeposit -> stringResource(R.string.tx_deposit_to, bankName)
        else -> stringResource(R.string.tx_withdrawal_from, bankName)
    }
    // خط دوم: اول «چی بود» (دسته / سؤال / وضعیت)، بعد ساعت و بانک
    val status = when {
        failed -> stringResource(R.string.tx_failed_meta)
        selfTransfer -> listOfNotNull(stringResource(R.string.tx_self_transfer_chip), sms.merchant).joinToString(" · ")
        uncategorized -> stringResource(if (isDeposit) R.string.tx_ask_income else R.string.tx_ask_expense)
        sms.isAutoCategorized -> stringResource(R.string.tx_auto_category, sms.categoryName.orEmpty())
        else -> sms.categoryName
    }
    val showBank = sms.isManual || sms.merchant != null || failed || selfTransfer
    val meta = listOfNotNull(
        status,
        Jalali.time(sms.dateMillis),
        bankName.takeIf { showBank },
        sms.feeRial?.let { stringResource(R.string.tx_fee, Money.toman(it)) },
    ).joinToString(" · ")

    val amountColor = when {
        muted -> colors.onSurfaceVariant
        isDeposit -> JibitoTheme.colors.income
        else -> colors.onBackground
    }
    val largeText = LocalDensity.current.fontScale >= LARGE_FONT_SCALE

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClickLabel = stringResource(R.string.cd_pick_category), onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = if (largeText) Alignment.Top else Alignment.CenterVertically,
        ) {
            Avatar(sms, uncategorized, failed, selfTransfer, isDeposit)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (muted) colors.onSurfaceVariant else colors.onBackground,
                    textDecoration = if (failed) TextDecoration.LineThrough else null,
                    maxLines = if (largeText) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uncategorized) colors.primary else colors.onSurfaceVariant,
                    fontWeight = if (uncategorized) FontWeight.Bold else null,
                    maxLines = if (largeText) 4 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // فونت بزرگ گوشی: مبلغ زیر متن می‌آید تا ستون متن جا داشته باشد
                if (largeText) Amount(t.amountRial, failed, muted, isDeposit, amountColor)
            }
            if (!largeText) {
                Spacer(Modifier.size(10.dp))
                Amount(t.amountRial, failed, muted, isDeposit, amountColor)
            }
        }
        if (uncategorized && sms.suggestedCategory != null && onAcceptSuggestion != null) {
            Row(
                Modifier.padding(start = AVATAR_SIZE + 18.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionChip(
                    text = stringResource(R.string.tx_suggest_accept, sms.suggestedCategory),
                    container = colors.secondaryContainer,
                    content = colors.onSecondaryContainer,
                    leadingCheck = true,
                    onClick = onAcceptSuggestion,
                )
                ActionChip(
                    text = stringResource(R.string.tx_suggest_other),
                    container = colors.surfaceVariant,
                    content = colors.onSurfaceVariant,
                    onClick = onClick,
                )
            }
        }
    }
}

private val AVATAR_SIZE = 48.dp
private val AvatarShape = RoundedCornerShape(16.dp)

/** آواتار ردیف: ایموجی دسته روی رنگ دسته، «؟» خط‌چین برای بی‌دسته‌ها، یا آیکون انتقال/ناموفق */
@Composable
private fun Avatar(sms: Transaction, uncategorized: Boolean, failed: Boolean, selfTransfer: Boolean, isDeposit: Boolean) {
    val colors = MaterialTheme.colorScheme
    when {
        uncategorized -> {
            // تپش آرام؛ اگر «حذف انیمیشن‌ها»ی گوشی روشن باشد، ثابت
            val context = LocalContext.current
            val motionOff = remember {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
            val scale = if (motionOff) 1f else {
                val pulse = rememberInfiniteTransition(label = "uncategorized")
                val value by pulse.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.06f,
                    animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "pulse",
                )
                value
            }
            val accent = colors.primary
            Box(
                Modifier
                    .scale(scale)
                    .size(AVATAR_SIZE)
                    .clip(AvatarShape)
                    .background(accent.copy(alpha = 0.10f))
                    .drawBehind {
                        val stroke = 2.dp.toPx()
                        drawRoundRect(
                            color = accent,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(16.dp.toPx() - stroke / 2),
                            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("؟", fontSize = 22.sp, fontWeight = FontWeight.Black, color = accent)
            }
        }
        failed || selfTransfer -> {
            val tint = if (selfTransfer) JibitoTheme.colors.transfer else colors.onSurfaceVariant
            Box(
                Modifier.size(AVATAR_SIZE).background(tint.copy(alpha = 0.13f), AvatarShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (failed) JibitoIcons.Refund else JibitoIcons.Transfer,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        else -> {
            val dark = colors.background.luminance() < 0.5f
            val base = if (sms.categoryColorHex != null) ChartColors.forCategory(sms.categoryColorHex, dark)
            else if (isDeposit) JibitoTheme.colors.income else colors.primary
            Box(
                Modifier.size(AVATAR_SIZE).background(base.copy(alpha = if (dark) 0.22f else 0.14f), AvatarShape),
                contentAlignment = Alignment.Center,
            ) {
                val emoji = sms.categoryIcon
                if (emoji != null) {
                    Text(emoji, fontSize = 22.sp)
                } else {
                    Icon(
                        if (isDeposit) JibitoIcons.ArrowDown else JibitoIcons.ArrowUp,
                        contentDescription = null,
                        tint = base,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** دکمه‌ی کوچک زیر ردیف (قبول پیشنهاد دسته / «یه چیز دیگه») */
@Composable
private fun ActionChip(text: String, container: Color, content: Color, onClick: () -> Unit, leadingCheck: Boolean = false) {
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(50))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingCheck) {
            Icon(JibitoIcons.Check, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** از این اندازه‌ی فونت گوشی به بالا، چیدمان ردیف عمودی می‌شود */
private const val LARGE_FONT_SCALE = 1.5f

/** مبلغ با علامت، بدون «تومان» (واحد در سرتیتر روز است؛ صفحه‌خوان کامل می‌خواند) */
@Composable
private fun Amount(amountRial: Long, failed: Boolean, muted: Boolean, isDeposit: Boolean, color: Color) {
    val spoken = (if (muted) "" else if (isDeposit) "+" else "−") + Money.toman(amountRial)
    // علامت و عدد دو تکه‌ی جدا هستند تا جهت‌نویسی راست‌به‌چپ جای علامت را جابه‌جا نکند.
    // در Row راست‌به‌چپ، اولین تکه سمت راست می‌نشیند: «−۱۲۵٬۰۰۰»
    Row(
        Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!muted) {
            Text(
                text = if (isDeposit) "+" else "−",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = color,
            )
            Spacer(Modifier.size(2.dp))
        }
        Text(
            text = Money.tomanNumber(amountRial),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = color,
            textDecoration = if (failed) TextDecoration.LineThrough else null,
        )
    }
}
