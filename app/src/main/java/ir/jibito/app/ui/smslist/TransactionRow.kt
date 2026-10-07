package ir.jibito.app.ui.smslist

import ir.jibito.app.ui.common.loopingValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Transaction
import ir.jibito.app.ui.common.CategoryIconTile
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.theme.CategoryTint
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/**
 * سرتیتر چسبان هر روز (طرح «جیبی»): «امروز»، یک نوار کوچک که خرج آن روز را با پرخرج‌ترین روز فهرست
 * مقایسه می‌کند (مرجانی اگر از یک روز معمولی سنگین‌تر بوده) و جمع خرج روز.
 * @param fraction خرج این روز نسبت به پرخرج‌ترین روز (۰ تا ۱)
 * @param heavy خرج این روز از میانگین روزها خیلی بیشتر است
 */
@Composable
internal fun DayHeader(
    group: DayGroup,
    nowMillis: Long = System.currentTimeMillis(),
    fraction: Float = if (group.spendRial > 0) 1f else 0f,
    heavy: Boolean = false,
) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(top = 22.dp, bottom = 6.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Jalali.dayTitle(group.dayStartMillis, nowMillis),
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            color = colors.onBackground,
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(t.chip)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (heavy) t.coral else t.teal)
            )
        }
        if (group.spendRial > 0) {
            Spacer(Modifier.width(10.dp))
            Text(amount(Money.compact(group.spendRial)), fontSize = 13.sp, color = t.muted, maxLines = 1)
        }
        // خرج یک‌باره جدا از نوار (نوار روزها را با هم مقایسه می‌کند)
        if (group.oneOffRial > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                amount(stringResource(R.string.day_one_off, Money.compact(group.oneOffRial))),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = t.sugFg,
                maxLines = 1,
            )
        }
    }
}

/** «بانک ملت» ← «ملت» (در فهرست جا کم است) */
internal fun shortBankName(name: String): String = name.removePrefix("بانک ").trim()

/** «سوپرمارکته؟ آره» / «کافه‌ست؟ آره» */
internal fun suggestQuestion(name: String): String {
    val vowelEnd = name.lastOrNull()?.let { it == 'ه' || it == 'ا' || it == 'و' || it == 'ی' } ?: false
    return if (vowelEnd) "${name}‌ست؟ آره" else "${name}ه؟ آره"
}

/**
 * یک تراکنش در فهرست روز (طرح «جیبی»): کاشی دسته (یا «؟» نقطه‌چین که آرام می‌تپد، برای بی‌دسته)،
 * اسم طرف حساب، و زیرش دسته · ساعت · بانک؛ مبلغ در طرف دیگر.
 * رنگ مبلغ فقط برای پولی است که آمده (سبز)؛ خرید ناموفق خط‌خورده و کم‌رنگ، انتقال به خودم خاکستری‌آبی.
 * اگر اپ دسته‌ای حدس زده، دو دکمه زیرش می‌آید: «سوپرمارکته؟ آره» و «یه چیز دیگه».
 * @param tint ظاهر دسته‌ی اصلی تراکنش (اگر دسته دارد)
 * @param onAcceptSuggestion پذیرفتن دسته‌ی پیشنهادی با یک لمس؛ null یعنی پیشنهادی نیست
 * @param suggestionName اسم دسته‌ی پیشنهادی که نشان داده می‌شود (در عمق مجاز کاربر)؛ پیش‌فرض همان پیشنهاد ذخیره‌شده
 */
@Composable
internal fun TransactionRow(
    sms: Transaction,
    tint: CategoryTint?,
    onClick: () -> Unit,
    onAcceptSuggestion: (() -> Unit)? = null,
    /** متن جست‌وجو: هر جای عنوان و توضیح که پیدا شد، پررنگ می‌شود */
    highlight: String? = null,
    suggestionName: String? = sms.suggestedCategory,
) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val tx = sms.transaction
    val isDeposit = tx.type == FlowType.DEPOSIT
    val failed = sms.isFailedPurchase
    val selfTransfer = sms.isSelfTransfer && !failed
    val uncategorized = sms.categoryId == null && !failed && !selfTransfer
    val bank = if (sms.isManual) stringResource(R.string.tx_manual_source) else sms.bank?.name?.let(::shortBankName) ?: stringResource(R.string.bank_unknown)

    // یادداشت خود کاربر (مثلاً از «بنویس» نوتیفیکیشن) گویاتر از اسم طرف حساب است؛ طرف حساب می‌رود در خط دوم
    val title = when {
        sms.note != null -> sms.note
        sms.merchant != null -> sms.merchant
        failed -> stringResource(R.string.tx_failed_purchase)
        selfTransfer -> stringResource(R.string.tx_self_transfer)
        sms.isManual -> stringResource(if (isDeposit) R.string.tx_manual_income else R.string.tx_manual_expense)
        isDeposit -> stringResource(R.string.tx_deposit_to, bank)
        else -> stringResource(R.string.tx_withdrawal_from, bank)
    }
    val kind = when {
        failed -> stringResource(R.string.tx_meta_failed)
        selfTransfer -> stringResource(R.string.tx_meta_transfer)
        uncategorized -> stringResource(if (isDeposit) R.string.tx_meta_ask_income else R.string.tx_meta_ask)
        sms.categoryName != null -> if (sms.isAutoCategorized) stringResource(R.string.tx_auto_category, sms.categoryName) else sms.categoryName
        else -> null
    }
    val meta = listOfNotNull(
        kind,
        // خرج یک‌باره (خرید خانه…): از بودجه و میانگین جداست
        stringResource(R.string.tx_meta_one_off).takeIf { sms.isOneOff && !failed && !selfTransfer },
        sms.merchant?.takeIf { sms.note != null },
        Jalali.time(sms.dateMillis),
        bank,
        sms.feeRial?.let { stringResource(R.string.tx_fee, amount(Money.compact(it))) },
    ).joinToString(" · ")
    val amountColor = when {
        failed || selfTransfer -> t.muted
        isDeposit -> t.income
        else -> colors.onBackground
    }
    val sign = when {
        failed || selfTransfer -> ""
        isDeposit -> "+"
        else -> "−"
    }
    val strike = if (failed) TextDecoration.LineThrough else null
    val largeText = LocalDensity.current.fontScale >= LARGE_FONT_SCALE

    Column(Modifier.alpha(if (failed) 0.6f else 1f)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClickLabel = stringResource(R.string.cd_pick_category), onClick = onClick)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                // خرج یک‌باره‌ی بی‌دسته: ستاره به‌جای «؟» خالی (متن «مال چی بود؟» هنوز دسته می‌خواهد)
                uncategorized && sms.isOneOff -> CategoryIconTile(CategoryTint(t.sugBg, t.sugFg, DesignIcons.Star, null), size = 52.dp, radius = 18.dp, iconSize = 26.dp)
                uncategorized -> UncategorizedTile()
                failed -> CategoryIconTile(CategoryTint(t.failBg, t.failFg, DesignIcons.Failed, null), size = 52.dp, radius = 18.dp, iconSize = 26.dp)
                selfTransfer -> CategoryIconTile(CategoryTint(t.transferBg, t.transferFg, DesignIcons.Transfer, null), size = 52.dp, radius = 18.dp, iconSize = 26.dp)
                tint != null -> CategoryIconTile(tint, size = 52.dp, radius = 18.dp, iconSize = 26.dp)
                else -> UncategorizedTile()
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    highlighted(title, highlight, t.amberTint),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    textDecoration = strike,
                    maxLines = if (largeText) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    highlighted(meta, highlight, t.amberTint),
                    fontSize = 13.sp,
                    color = if (uncategorized) t.uncatFg else t.muted,
                    maxLines = if (largeText) 4 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (largeText) Amount(sign, tx.amountRial, amountColor, strike)
            }
            if (!largeText) {
                Spacer(Modifier.width(8.dp))
                Amount(sign, tx.amountRial, amountColor, strike)
            }
        }
        if (uncategorized && onAcceptSuggestion != null && suggestionName != null) {
            Row(Modifier.padding(start = 66.dp, bottom = 10.dp)) {
                Row(
                    Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(t.sugBg)
                        .clickable(onClick = onAcceptSuggestion)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(DesignIcons.Check, contentDescription = null, tint = t.sugFg, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(suggestQuestion(suggestionName), color = t.sugFg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.tx_suggest_other),
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(t.chip)
                        .clickable(onClick = onClick)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onBackground,
                    maxLines = 1,
                )
            }
        }
    }
}

/** جاهایی از [text] که [query] (بی‌توجه به ی/ک عربی و بزرگی حروف) در آن هست، با زمینه‌ی [bg] */
internal fun highlighted(text: String, query: String?, bg: Color): AnnotatedString {
    val q = query?.trim().orEmpty()
    if (q.isEmpty()) return AnnotatedString(text)
    fun norm(s: String) = s.replace('ي', 'ی').replace('ك', 'ک').lowercase()
    val hay = norm(text)
    val needle = norm(q)
    if (hay.length != text.length) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var from = 0
        while (true) {
            val i = hay.indexOf(needle, from)
            if (i < 0) break
            addStyle(SpanStyle(background = bg, fontWeight = FontWeight.Black), i, i + needle.length)
            from = i + needle.length
        }
    }
}

/** از این اندازه‌ی فونت گوشی به بالا، مبلغ زیر عنوان می‌آید */
private const val LARGE_FONT_SCALE = 1.5f

/** مبلغ: علامت و عدد دو تکه‌ی جدا، تا جهت‌نویسی راست‌به‌چپ جای علامت را جابه‌جا نکند */
@Composable
private fun Amount(sign: String, amountRial: Long, color: Color, strike: TextDecoration?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (sign.isNotEmpty()) {
            Text(sign, fontSize = 17.sp, fontWeight = FontWeight.Black, color = color)
            Spacer(Modifier.width(2.dp))
        }
        Text(
            amount(Money.tomanNumber(amountRial)),
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            color = color,
            textDecoration = strike,
            maxLines = 1,
        )
    }
}

/** «؟» در کاشی نقطه‌چین مرجانی که آرام می‌تپد: «اینو دسته‌بندی کن» */
@Composable
private fun UncategorizedTile() {
    val t = JibitoTheme.colors
    // تپش رو به داخل (۱ ← ۰٫۹۳): کاشی هیچ‌وقت از جای ۵۲dp خودش بیرون نمی‌زند،
    // وگرنه ردیفِ گردشده (clip) لبه‌ی نقطه‌چینش را می‌بُرد. بی‌انیمیشن هم اندازه‌ی کامل می‌ماند.
    val scale = loopingValue(1f, 0.93f, 800, label = "uncatPulse")
    Box(
        Modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(t.uncatBg)
            .drawBehind {
                val w = 2.dp.toPx()
                drawRoundRect(
                    color = t.uncatBorder,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(17.dp.toPx()),
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("؟", fontSize = 24.sp, fontWeight = FontWeight.Black, color = t.uncatFg)
    }
}
