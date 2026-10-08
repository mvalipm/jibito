package ir.jibito.app.ui.summary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.common.HIDDEN_AMOUNT
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoText
import ir.jibito.app.ui.theme.Vazirmatn
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
import kotlin.math.roundToInt

/* سرصفحه‌ی «خلاصه»: تکه‌ها (کپسول ماه، دکمه‌های گرد، عدد درشت، نوار بودجه)؛ چیدمانشان در SummaryHero.kt */

@Composable
internal fun MonthPill(month: JalaliMonth, background: Color, onPick: (JalaliMonth) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(background)
                .clickable { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(month.title, color = Color.White, style = JibitoText.lead, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Icon(DesignIcons.ChevronDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val current = JalaliMonth.current()
            (0 until 12).map { current.plus(-it) }.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.title, fontWeight = if (m == month) FontWeight.Black else FontWeight.Normal) },
                    onClick = {
                        open = false
                        onPick(m)
                    },
                )
            }
        }
    }
}

@Composable
internal fun HeroButton(icon: ImageVector, label: String, onClick: () -> Unit, background: Color) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

/** آخرین عددی که عدد درشت نشان داد (تا آخر عمر فرایند اپ)؛ شروع شمارش دفعه‌ی بعد */
private var lastBigNumberRial: Long? = null

/**
 * عدد درشت خرج ماه؛ وقتی عوض می‌شود (خرج تازه، ماه دیگر)، نرم از عدد قبلی به عدد تازه می‌رسد.
 * با برگشتن به تب از عدد قبلی شروع می‌کند، نه صفر؛ پس هر بار باز کردن صفحه دوباره از صفر نمی‌شمارد.
 * اگر عدد و واحدش در عرض جا نشوند (عدد بلند، صفحه‌ی باریک، فونت بزرگ گوشی)، هر دو با هم کوچک می‌شوند
 * تا هیچ رقمی از لبه بیرون نزند.
 */
@Composable
internal fun BigNumber(spentRial: Long, hidden: Boolean, onClick: () -> Unit) {
    val (_, unit) = Money.compactParts(spentRial)
    val anim = remember { Animatable((lastBigNumberRial ?: 0L).toFloat()) }
    LaunchedEffect(spentRial) {
        anim.animateTo(spentRial.toFloat(), tween(900, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)))
        lastBigNumberRial = spentRial
    }
    val v = anim.value.toLong()
    val number = when {
        spentRial / 10 >= 1_000_000_000L -> Money.inUnit(v, 1_000_000_000L)
        spentRial / 10 >= 1_000_000L -> Money.inUnit(v, 1_000_000L)
        spentRial / 10 >= 1_000L -> Jalali.toPersianDigits((v / 10 / 1_000).toString())
        else -> Jalali.toPersianDigits((v / 10).toString())
    }
    val shown = if (hidden) HIDDEN_AMOUNT else number
    val unitText = if (hidden || unit.isEmpty()) stringResource(R.string.unit_toman) else "$unit ${stringResource(R.string.unit_toman)}"
    // فونت خیلی بزرگ گوشی: عدد درشت کمی کوچک‌تر، تا از صفحه بیرون نزند
    val scale = LocalDensity.current.fontScale
    val base = if (scale > 1.15f) 84f * 1.15f / scale else 84f
    // عرض را با عدد نهایی می‌سنجیم (نه عدد در حال شمارش) تا اندازه وسط انیمیشن نپرد؛
    // اعشار هم حساب می‌شود چون عدد در حال شمارش (مثلاً «۶۹٫۹» پیش از «۷۰») اعشار دارد
    val final = when {
        hidden -> HIDDEN_AMOUNT
        spentRial / 10 >= 1_000_000L -> Money.compactParts(spentRial).first.substringBefore('٫') + "٫۸"
        else -> Money.compactParts(spentRial).first
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val fit = remember(final, unitText, base, maxWidth) {
            val numberW = measurer.measure(final, bigNumberStyle(base.sp), softWrap = false).size.width
            val unitW = measurer.measure(unitText, unitStyle(19.sp), softWrap = false).size.width
            val needed = numberW + unitW + with(density) { 10.dp.toPx() }
            val room = with(density) { maxWidth.toPx() }
            if (needed > room) (room / needed).coerceAtLeast(0.4f) else 1f
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClickLabel = stringResource(R.string.overall_dialog_title), onClick = onClick),
        ) {
            Text(
                shown,
                style = bigNumberStyle((base * fit).sp),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(10.dp * fit))
            Text(
                unitText,
                style = unitStyle((19f * fit).sp),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/**
 * ارقام همیشه چپ‌به‌راست؛ ارتفاع خط آن‌قدر هست که ممیز «٫» (که زیر خط پایین می‌آید) بریده نشود.
 */
private fun bigNumberStyle(size: TextUnit) = TextStyle(
    color = Color.White,
    fontFamily = Vazirmatn,
    fontSize = size,
    fontWeight = FontWeight.Black,
    lineHeight = size * 1.3f,
    textDirection = TextDirection.Ltr,
)

private fun unitStyle(size: TextUnit) = TextStyle(
    color = Color.White,
    fontFamily = Vazirmatn,
    fontSize = size,
    fontWeight = FontWeight.Bold,
)

/**
 * پس‌زمینه‌ی دکمه‌ها و کپسول‌های سرصفحه. در حالت روشن سیاه کم‌رنگ (سفید روی آن بالای ۶:۱)؛
 * در حالت تیره رنگ سرصفحه خودش تیره است و سفید کم‌رنگ هم کنتراست کافی دارد.
 */
internal fun heroControlColor(dark: Boolean) = if (dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.14f)

/**
 * نوار سفید مصرف بودجه با خط «امروز» (کجای ماه هستیم) و برچسبش. بخش خالی شیار تیره است، نه سفید کم‌رنگ،
 * تا مرز پر و خالی (و جای سقف بودجه) واضح باشد.
 */
@Composable
internal fun BudgetBar(spent: Float, today: Float?, marker: Color) {
    val fill by animateFloatAsState(spent, tween(900, easing = CubicBezierEasing(0.34f, 1.3f, 0.64f, 1f)), label = "budgetFill")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (today != null) TodayLabel(today.coerceIn(0f, 1f))
        BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.22f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fill.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                )
            }
            if (today != null) {
                // خط ۳ پیکسلی به رنگ حال جیب با حلقه‌ی سفید دورش
                val x: Dp = (maxWidth * today.coerceIn(0f, 1f) - 3.5.dp).coerceAtLeast(0.dp)
                Box(
                    Modifier
                        .offset(x = x)
                        .width(7.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.5.dp))
                        .background(Color.White)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(marker)
                )
            }
        }
    }
}

/**
 * «امروز» بالای نوار بودجه، وسطش روی خط امروز؛ نزدیک دو سر نوار داخل عرض می‌ماند.
 * ردیف خودش را دارد (نه بیرون از مرز نوار) تا همیشه کشیده شود؛ برای TalkBack پنهان است چون تنها معنایی ندارد.
 */
@Composable
private fun TodayLabel(today: Float) {
    Text(
        stringResource(R.string.hero_today),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {}
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minWidth = 0))
                val center = (constraints.maxWidth * today).roundToInt()
                val x = (center - p.width / 2).coerceIn(0, (constraints.maxWidth - p.width).coerceAtLeast(0))
                // placeRelative: در راست‌به‌چپ از راست حساب می‌شود، مثل خط امروز
                layout(constraints.maxWidth, p.height) { p.placeRelative(x, 0) }
            },
        color = Color.White,
        style = JibitoText.tiny,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * زیر نوار بودجه: «۶۱٪ از بودجه‌ی ۳۰ میلیونی ✎» (لمس ← تعیین بودجه‌ی کل) و «۲۲ روز مونده».
 * با فونت خیلی بزرگ گوشی زیر هم می‌آیند، تا جمله‌ی بودجه وسط کلمه نشکند.
 */
@Composable
internal fun BudgetLabels(
    s: MonthSummary,
    budget: Long,
    hidden: Boolean,
    isCurrent: Boolean,
    nowMillis: Long,
    onEditBudget: () -> Unit,
) {
    val ofBudget = Jalali.toPersianDigits(
        stringResource(
            R.string.hero_of_budget,
            "${(s.budgetSpentRial * 100 / budget).coerceAtMost(999)}٪",
            if (hidden) HIDDEN_AMOUNT else Money.compactAdjective(budget),
        )
    )
    val days = daysLeft(s, nowMillis)
    val left = when {
        !isCurrent -> stringResource(R.string.hero_month_done)
        days <= 1 -> stringResource(R.string.hero_last_day)
        else -> Jalali.toPersianDigits(stringResource(R.string.hero_days_left, days))
    }
    val edit: @Composable () -> Unit = {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = stringResource(R.string.overall_dialog_title), role = Role.Button, onClick = onEditBudget)
                .padding(vertical = 4.dp)
                .padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(ofBudget, color = Color.White, style = JibitoText.body, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Icon(JibitoIcons.Pencil, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
    val daysText: @Composable () -> Unit = {
        Text(left, color = Color.White, style = JibitoText.body, fontWeight = FontWeight.Medium)
    }
    // ناحیه‌ی لمس جمله‌ی بودجه را خود Compose تا ۴۸dp بزرگ می‌کند (minimumTouchTargetSize)، چون چیز قابل‌لمس دیگری کنارش نیست؛
    // پس ارتفاع دیدنی‌اش را بالا نمی‌بریم تا سرصفحه بلندتر نشود
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(Modifier.fillMaxWidth()) {
            edit()
            daysText()
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { edit() }
            daysText()
        }
    }
}
