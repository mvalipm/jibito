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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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

/** یک دور شمارش (میلی‌ثانیه) */
private const val COUNT_MILLIS = 2800

/**
 * لحظه‌ی «آهان!» (طرح «جیبی»): بعد از دادن اجازه و اولین خواندن پیامک‌ها.
 * پیامک‌ها به شکل حباب‌های کوچک توی دهان جیبی می‌ریزند و عدد بالا می‌رود؛ آخرش جیبی می‌پرد و
 * چند واقعیت کوتاه (چند ماه، چند بانک، بیشترین خرج) یکی‌یکی می‌آیند. روی همه‌چیز می‌نشیند تا «بزن بریم».
 */
@OptIn(ExperimentalLayoutApi::class)
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(if (done) R.string.reveal_caption_done else R.string.reveal_caption_reading),
                modifier = Modifier.padding(top = 24.dp).height(28.dp),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )

            // حباب‌های پیامک + هاله + جیبی
            Box(Modifier.fillMaxWidth().height(300.dp).padding(top = 4.dp), contentAlignment = Alignment.TopCenter) {
                if (!done) FlyingBubbles()
                Glow(Modifier.offset(y = 120.dp))
                RevealMascot(done, Modifier.offset(y = 140.dp))
            }

            Text(
                Jalali.toPersianDigits(count.value.toInt().toString()),
                modifier = Modifier.padding(top = 18.dp),
                color = Color.White,
                fontSize = 104.sp,
                lineHeight = 104.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp,
            )
            Text(
                stringResource(R.string.reveal_found),
                modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            // واقعیت‌های کوتاه، بعد از تمام شدن شمارش
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .padding(start = 24.dp, end = 24.dp, top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (done) {
                    val facts = buildList {
                        if (stats.months > 0) add(Fact(Jalali.toPersianDigits(stringResource(R.string.reveal_fact_months, stats.months)), DesignIcons.Calendar, Color.White.copy(alpha = 0.1f), Color.White))
                        if (stats.banks > 0) add(Fact(Jalali.toPersianDigits(stringResource(R.string.reveal_fact_banks, stats.banks)), DesignIcons.Bank, Color.White.copy(alpha = 0.1f), Color.White))
                        stats.topCategory?.let { add(Fact(stringResource(R.string.reveal_fact_top, it), DesignIcons.Grocery, TintTeal, Color(0xFF062A29))) }
                    }
                    facts.forEachIndexed { i, f -> FactChip(f, delayMillis = i * 120) }
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.reveal_go),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .alpha(buttonAlpha)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xFFFF8A65))
                    .clickable(onClick = onDone)
                    .padding(top = 14.dp),
                color = Color(0xFF2A0A00),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.reveal_replay),
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { round++ }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
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
        160.dp,
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
    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.TopCenter) {
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
                        translationY = ((6f + (i % 2) * 18f) + (if (dx == 0f) 230f else 250f) * e) * density
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

private data class Fact(val text: String, val icon: ImageVector, val bg: Color, val fg: Color)

/** کپسول یک واقعیت، که با فنر کوچکی بالا می‌پرد */
@Composable
private fun FactChip(f: Fact, delayMillis: Int) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // تأخیر داخل خود انیمیشن (نه delay جدا)، تا با ساعت انیمیشن‌ها جلو برود
        pop.animateTo(1f, tween(550, delayMillis = delayMillis, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)))
    }
    Row(
        Modifier
            .graphicsLayer {
                alpha = pop.value.coerceIn(0f, 1f)
                scaleX = 0.7f + 0.3f * pop.value
                scaleY = 0.7f + 0.3f * pop.value
                translationY = (1f - pop.value) * 16f * density
            }
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(f.bg)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(f.icon, contentDescription = null, tint = f.fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(f.text, color = f.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
