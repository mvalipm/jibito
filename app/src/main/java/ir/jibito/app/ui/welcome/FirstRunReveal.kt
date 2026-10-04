package ir.jibito.app.ui.welcome

import ir.jibito.app.ui.common.loopingValue
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.common.Mascot
import ir.jibito.app.ui.common.MascotFace
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.util.Jalali
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale

/** فقط یک بار، بعد از اولین خواندن پیامک‌ها روی این گوشی */
class FirstRunFlag(context: Context) {
    private val prefs = context.getSharedPreferences("first_run", Context.MODE_PRIVATE)

    val done: Boolean get() = prefs.getBoolean(KEY_DONE, false)

    fun markDone() {
        prefs.edit().putBoolean(KEY_DONE, true).apply()
    }

    private companion object {
        const val KEY_DONE = "reveal_done"
    }
}

private val RevealBg = Color(0xFF17141C)
private val TintTeal = Color(0xFF17BEBB)
private val TintPeach = Color(0xFFFF9B7A)
private val TintGold = Color(0xFFF5B83D)
private val TealText = Color(0xFF5FE0DC)
private val TintCta = Color(0xFFFF8A65)
private val OnCta = Color(0xFF2A0A00)

/** دکمه‌ی «بزن بریم»: فلشی رو به جلو (در راست‌به‌چپ، چپ) */
private val GoIcon by lazy { DesignIcons.svg("go", "M19 12H5M11 6l-6 6 6 6", strokeWidth = 2.6f) }
private val ShopIcon by lazy { DesignIcons.svg("shop", DesignIcons.SHOP) }
private val SparkleIcon by lazy { DesignIcons.svg("sparkle", DesignIcons.SPARKLE) }

/** ارتفاع جای جیبی و بالای خود جیبی در آن (dp) */
private const val HERO_HEIGHT = 236
private const val MASCOT_TOP = 84

/** مسیر حباب‌ها نسبت به طرح اول (که جیبی ۱۴۰dp پایین‌تر و بزرگ‌تر بود) */
private const val BUBBLE_TRAVEL = 0.74f

/** یک دور شمارش (میلی‌ثانیه) */
private const val COUNT_MILLIS = 2800

/**
 * لحظه‌ی «آهان!» (طرح «جیبی»): بعد از دادن اجازه و اولین خواندن پیامک‌ها.
 * پیامک‌ها به شکل حباب‌های کوچک توی دهان جیبی می‌ریزند و عدد بالا می‌رود؛ آخرش جیبی می‌پرد و
 * چند واقعیت کوتاه (چند ماه، چند بانک، بیشترین خرج) یکی‌یکی می‌آیند. روی همه‌چیز می‌نشیند تا «بزن بریم».
 */
@Composable
fun FirstRunReveal(stats: RevealStats, onDone: () -> Unit) {
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
            // محتوا اگر جا نشد اسکرول می‌خورد؛ دکمه‌ها همیشه پایین صفحه می‌مانند
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Caption(done, Modifier.padding(top = 20.dp))

                // حباب‌های پیامک + هاله + جیبی
                Box(Modifier.fillMaxWidth().height(HERO_HEIGHT.dp), contentAlignment = Alignment.TopCenter) {
                    if (!done) FlyingBubbles()
                    Glow(Modifier.offset(y = (MASCOT_TOP - 30).dp))
                    if (done) Sparks(Modifier.offset(y = (MASCOT_TOP - 40).dp))
                    RevealMascot(done, Modifier.offset(y = MASCOT_TOP.dp))
                }

                CountLine(count.value.toInt(), Modifier.padding(top = 18.dp, start = 16.dp, end = 16.dp))
                Text(
                    stringResource(R.string.reveal_found_sub),
                    modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )

                // آمار کوتاه و قدم بعدی، بعد از تمام شدن شمارش
                val cells = buildList {
                    if (stats.months > 0) add(StatCell(Jalali.toPersianDigits(stats.months.toString()), stringResource(R.string.reveal_unit_months), stringResource(R.string.reveal_label_months), DesignIcons.Calendar, TintTeal))
                    if (stats.banks > 0) add(StatCell(Jalali.toPersianDigits(stats.banks.toString()), stringResource(R.string.reveal_unit_banks), stringResource(R.string.reveal_label_banks), DesignIcons.Bank, TintGold))
                    stats.topCategory?.let {
                        // «۲۸٪ خرج‌ها» به‌جای «بیشترین خرج» تا این آمار هم مثل دوتای دیگر عدد داشته باشد
                        val label = stats.topSharePercent
                            ?.let { p -> Jalali.toPersianDigits(stringResource(R.string.reveal_label_top_share, p)) }
                            ?: stringResource(R.string.reveal_label_top)
                        add(StatCell(it, null, label, ShopIcon, TintPeach))
                    }
                }
                if (cells.isNotEmpty()) PopIn(done, delayMillis = 0) { StatsCard(cells, Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) }
                PopIn(done, delayMillis = 160) { NextHint(Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 12.dp)) }
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
    Box(modifier.height(36.dp), contentAlignment = Alignment.Center) {
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

/** عدد بزرگ با جداکننده‌ی هزارگان و واحد «تراکنش»؛ اندازه با تعداد رقم کوچک می‌شود تا همیشه یک خط بماند */
@Composable
private fun CountLine(value: Int, modifier: Modifier) {
    val text = Jalali.toPersianDigits(String.format(Locale.US, "%,d", value).replace(',', '٬'))
    val size = when {
        text.length <= 3 -> 88.sp
        text.length <= 5 -> 76.sp
        text.length <= 7 -> 60.sp
        else -> 48.sp
    }
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text,
            modifier = Modifier.alignByBaseline(),
            style = LocalTextStyle.current.copy(brush = Brush.verticalGradient(listOf(Color.White, Color.White, Color(0xFFFFC9B5)))),
            fontSize = size,
            lineHeight = size,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.reveal_unit_transactions),
            modifier = Modifier.alignByBaseline(),
            color = TintPeach,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

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
            .padding(vertical = 16.dp, horizontal = 4.dp),
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

/** قدم بعد: از این به بعد چه می‌شود */
@Composable
private fun NextHint(modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 2.dp).size(28.dp).clip(RoundedCornerShape(9.dp)).background(TintGold.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(SparkleIcon, contentDescription = null, tint = TintGold, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.reveal_next),
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 13.5.sp,
            lineHeight = 23.sp,
        )
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

/** چند جرقه‌ی رنگی دور جیبی، بعد از پرش */
@Composable
private fun Sparks(modifier: Modifier) {
    val p = loopingValue(0f, 1f, 1400, label = "sparks")
    Box(modifier.size(width = 240.dp, height = 140.dp)) {
        listOf(
            Triple(Offset(178f, 20f), 8f, TintGold),
            Triple(Offset(44f, 58f), 6f, TintTeal),
            Triple(Offset(84f, 6f), 5f, TintPeach),
            Triple(Offset(196f, 92f), 6f, Color.White.copy(alpha = 0.5f)),
        ).forEachIndexed { i, (at, d, c) ->
            Box(
                Modifier
                    .offset(x = at.x.dp, y = at.y.dp)
                    .graphicsLayer { alpha = if (i % 2 == 0) 0.5f + 0.5f * p else 1f - 0.5f * p }
                    .size(d.dp)
                    .background(c, CircleShape)
            )
        }
    }
}

/** جیبی: موقع خواندن دهانش می‌جنبد؛ آخرش یک پرش شاد */
@Composable
private fun RevealMascot(done: Boolean, modifier: Modifier) {
    val squash = if (done) 0f else loopingValue(0f, 1f, 580, label = "chomp", reverse = false, easing = LinearEasing)
    val jump = remember { Animatable(0f) }
    LaunchedEffect(done) {
        if (done) {
            jump.snapTo(0f)
            jump.animateTo(1f, tween(900, easing = LinearEasing))
        }
    }
    // پرش: بالا ۲۲، فرود، یک پرش کوچک ۶ و آرام گرفتن
    val y = jumpOffset(jump.value)
    // «جویدن»: کمی پهن و کوتاه، بعد کمی باریک و بلند
    val wave = kotlin.math.sin(squash * 2 * Math.PI).toFloat()
    val sx = if (done) 1f else 1f + 0.05f * wave
    val sy = if (done) 1f else 1f - 0.05f * wave
    Mascot(
        132.dp,
        if (done) MascotFace.HAPPY else MascotFace.CHOMP,
        modifier.graphicsLayer {
            scaleX = sx
            scaleY = sy
            translationY = if (done) y * density else 0f
            transformOrigin = TransformOrigin(0.5f, 0.9f)
        },
    )
}

/** ارتفاع پرش جیبی (dp، منفی = بالا) در لحظه‌ی [p] از ۰ تا ۱ */
private fun jumpOffset(p: Float): Float {
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    return when {
        p < 0.3f -> lerp(0f, -22f, p / 0.3f)
        p < 0.55f -> lerp(-22f, 0f, (p - 0.3f) / 0.25f)
        p < 0.75f -> lerp(0f, -6f, (p - 0.55f) / 0.2f)
        else -> lerp(-6f, 0f, (p - 0.75f) / 0.25f)
    }
}

/** هاله‌ی گرم پشت جیبی که آرام نفس می‌کشد */
@Composable
private fun Glow(modifier: Modifier) {
    val p = loopingValue(0f, 1f, 1200, label = "glow")
    Box(
        modifier
            .size(200.dp)
            .graphicsLayer {
                alpha = 0.35f + 0.25f * p
                scaleX = 1f + 0.12f * p
                scaleY = 1f + 0.12f * p
            }
            .background(Brush.radialGradient(listOf(Color(0x8CE4572E), Color(0x00E4572E))), CircleShape)
    )
}

/** ۹ حباب پیامک که از بالا، در سه ستون، به سمت دهان جیبی پرواز می‌کنند و کوچک و محو می‌شوند */
@Composable
private fun FlyingBubbles() {
    val phase = loopingValue(0f, 1f, 1150, label = "fly", reverse = false, easing = LinearEasing)
    val easing = CubicBezierEasing(0.5f, 0f, 0.8f, 0.6f)
    val tints = listOf(TintTeal, TintPeach, TintGold)
    // ستون‌ها نسبت به وسط صفحه: راست، وسط، چپ (همان جای طرح ۳۹۰ پیکسلی)
    val columns = listOf(-104f to (110f to -8f), 0f to (0f to 0f), 102f to (-110f to 8f))
    Box(Modifier.fillMaxWidth().height(HERO_HEIGHT.dp), contentAlignment = Alignment.TopCenter) {
        for (i in 0 until 9) {
            val (x0, path) = columns[i % 3]
            val (dx, rot) = path
            val local = ((phase - i * 128f / 1150f) % 1f + 1f) % 1f
            val e = easing.transform(local)
            val alpha = if (local < 0.15f) local / 0.15f else 1f - (local - 0.15f) / 0.85f
            Column(
                Modifier
                    .graphicsLayer {
                        translationX = (x0 + dx * e) * density
                        translationY = ((6f + (i % 2) * 18f) + (if (dx == 0f) 230f else 250f) * BUBBLE_TRAVEL * e) * density
                        rotationZ = rot + (if (rot == 0f) 0f else -rot * 1.75f) * e
                        scaleX = 1f - 0.75f * e
                        scaleY = 1f - 0.75f * e
                        this.alpha = alpha
                    }
                    .size(width = 62.dp, height = 40.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 4.dp, bottomStart = 12.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.fillMaxWidth(0.8f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.55f)))
                Box(Modifier.fillMaxWidth(0.55f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(tints[i % 3]))
            }
        }
    }
}
