package ir.jibito.app.ui.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.util.Jalali
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.Layout
import ir.jibito.app.ui.common.rememberFitScale

private val RevealBg = Color(0xFF17141C)
internal val TintTeal = Color(0xFF17BEBB)
internal val TintPeach = Color(0xFFFF9B7A)
internal val TintGold = Color(0xFFF5B83D)
private val TealText = Color(0xFF5FE0DC)
private val TintCta = Color(0xFFFF8A65)
private val OnCta = Color(0xFF2A0A00)

/** دکمه‌ی «بزن بریم»: فلشی رو به جلو (در راست‌به‌چپ، چپ) */
private val GoIcon by lazy { DesignIcons.svg("go", "M19 12H5M11 6l-6 6 6 6", strokeWidth = 2.6f) }
private val SparkleIcon by lazy { DesignIcons.svg("sparkle", DesignIcons.SPARKLE) }

/** جیبی و حباب‌ها با نسبت ۰٫۵ تا ۱٫۲ از طرح اصلی ([HERO_HEIGHT]) جا را پر می‌کنند */
private const val HERO_MIN_SCALE = 0.5f
private const val HERO_MAX_SCALE = 1.2f

/** یک دور شمارش (میلی‌ثانیه) */
private const val COUNT_MILLIS = 2800

/**
 * لحظه‌ی «آهان!» (طرح «جیبی»): بعد از دادن اجازه و اولین خواندن پیامک‌ها.
 * پیامک‌ها به شکل حباب‌های کوچک توی دهان جیبی می‌ریزند و عدد بالا می‌رود؛ آخرش جیبی می‌پرد و
 * چند واقعیت کوتاه (چند ماه، چند بانک) یکی‌یکی می‌آیند. روی همه‌چیز می‌نشیند تا «بزن بریم».
 */
@Composable
fun FirstRunReveal(
    stats: RevealStats,
    onDone: () -> Unit,
    /** چند پیامک که مطمئن نبودم تراکنش‌اند؛ این‌جا فقط گفته می‌شود و بعداً از «کارها» بررسی می‌شوند */
    unsure: Int = 0,
) {
    BackHandler(onBack = onDone)
    // هر بار «دوباره ببین»، یک دور تازه
    var round by remember { mutableIntStateOf(0) }
    val count = remember(round) { Animatable(0f) }
    var done by remember(round) { mutableStateOf(false) }
    LaunchedEffect(round) {
        count.animateTo(stats.count.toFloat(), tween(COUNT_MILLIS, easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)))
        done = true
    }
    val buttonAlpha by animateFloatAsState(if (done) 1f else 0.35f, tween(400), label = "button")

    Box(
        Modifier
            .fillMaxSize()
            .background(RevealBg)
            // لمس‌ها به صفحه‌ی زیرش نرسد
            .pointerInput(Unit) { detectTapGestures { } }
            .safeDrawingPadding()
    ) {
        Column(Modifier.fillMaxSize().padding(bottom = 16.dp)) {
            // جیبی هر جای خالی را می‌گیرد و جمع می‌شود تا همه‌چیز بی‌اسکرول جا شود؛ فقط با فونت خیلی درشت
            // اسکرول می‌خورد. دکمه همیشه پایین صفحه می‌ماند
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // عدد بزرگ هم با ارتفاع صفحه کم و زیاد می‌شود
                val countMaxSp = (maxHeight.value * COUNT_HEIGHT_RATIO).coerceIn(COUNT_MIN_SP, COUNT_MAX_SP)
                HeroFill(
                    viewport = maxHeight,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    top = { Caption(done, Modifier.padding(top = 12.dp, start = 20.dp, end = 20.dp)) },
                    hero = { RevealHero(done) },
                    bottom = {
                        CountLine(count.value.toInt(), stats.count, countMaxSp, Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp))
                        // «تراکنش» اول همین جمله، نه کنار عدد، تا عدد همه‌ی عرض را داشته باشد
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = TintPeach, fontWeight = FontWeight.Black)) {
                                    append(stringResource(R.string.reveal_unit_transactions))
                                }
                                append(' ')
                                append(stringResource(R.string.reveal_found_sub))
                            },
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )

                        // آمار کوتاه و قدم بعدی، بعد از تمام شدن شمارش
                        val cells = buildList {
                            if (stats.months > 0) add(StatCell(Jalali.toPersianDigits(stats.months.toString()), stringResource(R.string.reveal_unit_months), stringResource(R.string.reveal_label_months), DesignIcons.Calendar, TintTeal))
                            if (stats.banks > 0) add(StatCell(Jalali.toPersianDigits(stats.banks.toString()), stringResource(R.string.reveal_unit_banks), stringResource(R.string.reveal_label_banks), DesignIcons.Bank, TintGold))
                        }
                        if (cells.isNotEmpty()) PopIn(done, delayMillis = 0) { StatsCard(cells, Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) }
                        PopIn(done, delayMillis = 160) { NextHint(unsure, Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 8.dp)) }
                    },
                )
            }

            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .height(58.dp)
                    .alpha(buttonAlpha)
                    .clip(RoundedCornerShape(29.dp))
                    .background(TintCta)
                    .clickable(onClick = onDone),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.reveal_go), color = OnCta, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(10.dp))
                Icon(GoIcon, contentDescription = null, tint = OnCta, modifier = Modifier.size(20.dp))
            }
            // «دوباره ببین» حذف شد: این لحظه فقط یک دکمه می‌خواهد
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** بالای صفحه: موقع خواندن یک جمله‌ی کم‌رنگ؛ آخرش کپسول فیروزه‌ای با تیک */
@Composable
private fun Caption(done: Boolean, modifier: Modifier) {
    // کمینه، نه ارتفاع ثابت: با فونت درشت گوشی کپسول بلندتر می‌شود و متن بریده نمی‌شود
    Box(modifier.heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
        if (done) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(TintTeal.copy(alpha = 0.12f))
                    .padding(start = 10.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(20.dp).background(TintTeal, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(DesignIcons.CheckBold, contentDescription = null, tint = Color(0xFF062A29), modifier = Modifier.size(12.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reveal_caption_done), color = TealText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                stringResource(R.string.reveal_caption_reading),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * عدد بزرگ، تنها در یک خط و وسط صفحه. از ۸۸ شروع می‌شود و آن‌قدر کوچک می‌شود که عدد نهایی ([target])
 * در عرض واقعی جا شود؛ اندازه با عدد نهایی سنجیده می‌شود تا وسط شمارش نپرد.
 */
@Composable
private fun CountLine(value: Int, target: Int, maxSp: Float, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val scale = rememberFitScale(maxWidth, groupedCount(target) to countStyle(maxSp.sp))
        // پهنای متن همه‌ی عرض است نه اندازه‌ی دقیق عدد: با پهنای تنگ، اندروید گاهی آخرین رقم را به خط
        // دوم می‌برد و maxLines = 1 آن را دور می‌اندازد (۱٬۴۰۸ روی گوشی «۱٬۴۰» دیده می‌شد)
        Text(
            groupedCount(value),
            modifier = Modifier.fillMaxWidth().testTag(COUNT_TAG),
            style = countStyle((maxSp * scale).sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private const val COUNT_MAX_SP = 88f
private const val COUNT_MIN_SP = 56f

/** اندازه‌ی عدد نسبت به ارتفاع جای محتوا، میان [COUNT_MIN_SP] و [COUNT_MAX_SP] */
private const val COUNT_HEIGHT_RATIO = 0.115f

/** برای تست روی امولاتور */
const val COUNT_TAG = "reveal_count"

private fun groupedCount(value: Int) = Jalali.toPersianDigits(String.format(Locale.US, "%,d", value).replace(',', '٬'))

/** ارقام چپ‌به‌راست؛ ارتفاع خط کمی بیشتر از اندازه تا لبه‌ی ارقام بریده نشود. فاصله‌ی منفی بین ارقام نه: پهنای سنجیده را از پهنای واقعی کمتر می‌کرد */
@Composable
private fun countStyle(size: TextUnit) = LocalTextStyle.current.merge(
    TextStyle(
        color = Color.White,
        fontSize = size,
        lineHeight = size * 1.2f,
        fontWeight = FontWeight.Black,
        textDirection = TextDirection.Ltr,
    )
)

private data class StatCell(val value: String, val unit: String?, val label: String, val icon: ImageVector, val tint: Color)

/** کارت آمار: هر ستون آیکون رنگی، عدد درشت و برچسب کوچک */
@Composable
private fun StatsCard(cells: List<StatCell>, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .height(IntrinsicSize.Min)
            .padding(vertical = 14.dp, horizontal = 4.dp),
    ) {
        cells.forEachIndexed { i, c ->
            if (i > 0) Box(Modifier.padding(vertical = 8.dp).width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.1f)))
            Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(c.icon, contentDescription = null, tint = c.tint, modifier = Modifier.size(20.dp)) }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        c.value,
                        modifier = Modifier.alignByBaseline(),
                        color = Color.White,
                        fontSize = if (c.unit == null) 17.sp else 20.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    c.unit?.let {
                        Spacer(Modifier.width(3.dp))
                        Text(it, modifier = Modifier.alignByBaseline(), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    c.label,
                    modifier = Modifier.padding(top = 4.dp),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** قدم بعد: از این به بعد چه می‌شود؛ و اگر پیامکی مبهم ماند، نوار کوچکی که بعداً از «کارها» */
@Composable
private fun NextHint(unsure: Int, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.padding(top = 2.dp).size(28.dp).clip(RoundedCornerShape(9.dp)).background(TintGold.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(SparkleIcon, contentDescription = null, tint = TintGold, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.reveal_next),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 13.5.sp,
                lineHeight = 22.sp,
            )
        }
        if (unsure > 0) {
            val shape = RoundedCornerShape(14.dp)
            Text(
                Jalali.toPersianDigits(stringResource(R.string.reveal_unsure, unsure)),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(TintGold.copy(alpha = 0.10f))
                    .border(1.dp, TintGold.copy(alpha = 0.22f), shape)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

/**
 * سه تکه زیر هم: بالا، جای جیبی، پایین. بالا و پایین به اندازه‌ی خودشان‌اند و جیبی بقیه‌ی [viewport] را
 * می‌گیرد (میان کمینه و بیشینه‌ی خودش). اگر حتی با کمینه جا نشد، کل این ستون در اسکرول بیرونی می‌رود.
 */
@Composable
private fun HeroFill(
    viewport: Dp,
    modifier: Modifier,
    top: @Composable () -> Unit,
    hero: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    Layout(
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { top() }
            Box { hero() }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { bottom() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val column = Constraints(minWidth = w, maxWidth = w)
        val topPlaced = measurables[0].measure(column)
        val bottomPlaced = measurables[2].measure(column)
        val heroH = (viewport.roundToPx() - topPlaced.height - bottomPlaced.height)
            .coerceIn((HERO_HEIGHT * HERO_MIN_SCALE).dp.roundToPx(), (HERO_HEIGHT * HERO_MAX_SCALE).dp.roundToPx())
        val heroPlaced = measurables[1].measure(Constraints.fixed(w, heroH))
        layout(w, topPlaced.height + heroH + bottomPlaced.height) {
            topPlaced.placeRelative(0, 0)
            heroPlaced.placeRelative(0, topPlaced.height)
            bottomPlaced.placeRelative(0, topPlaced.height + heroH)
        }
    }
}

/** حباب‌های پیامک + هاله + جیبی، روی بوم ثابت [HERO_HEIGHT] که با جای موجود بزرگ و کوچک می‌شود */
@Composable
private fun RevealHero(done: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val scale = (maxHeight.value / HERO_HEIGHT).coerceIn(HERO_MIN_SCALE, HERO_MAX_SCALE)
        Box(
            Modifier.fillMaxWidth().requiredHeight(HERO_HEIGHT.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!done) FlyingBubbles()
            Glow(Modifier.offset(y = (MASCOT_TOP - 30).dp))
            if (done) Sparks(Modifier.offset(y = (MASCOT_TOP - 40).dp))
            RevealMascot(done, Modifier.offset(y = MASCOT_TOP.dp))
        }
    }
}

/** بعد از تمام شدن شمارش با فنر کوچکی بالا می‌آید؛ جایش از اول نگه داشته می‌شود تا صفحه نپرد */
@Composable
private fun PopIn(visible: Boolean, delayMillis: Int, content: @Composable () -> Unit) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            // تأخیر داخل خود انیمیشن (نه delay جدا)، تا با ساعت انیمیشن‌ها جلو برود
            pop.animateTo(1f, tween(550, delayMillis = delayMillis, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)))
        } else {
            pop.snapTo(0f)
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = pop.value.coerceIn(0f, 1f)
            scaleX = 0.92f + 0.08f * pop.value
            scaleY = 0.92f + 0.08f * pop.value
            translationY = (1f - pop.value) * 16f * density
        }
    ) { content() }
}

