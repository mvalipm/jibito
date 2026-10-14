package ir.jibito.app.ui.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import ir.jibito.app.ui.common.Mascot
import ir.jibito.app.ui.common.MascotFace
import ir.jibito.app.ui.common.loopingValue

// نقاشی‌ها و انیمیشن‌های صفحه‌ی «جیبت رو شناختم» (چیدمان در FirstRunReveal.kt)

/** ارتفاع جای جیبی و بالای خود جیبی در آن (dp) */
internal const val HERO_HEIGHT = 236
internal const val MASCOT_TOP = 84

/** مسیر حباب‌ها نسبت به طرح اول (که جیبی ۱۴۰dp پایین‌تر و بزرگ‌تر بود) */
internal const val BUBBLE_TRAVEL = 0.74f

/** چند جرقه‌ی رنگی دور جیبی، بعد از پرش */
@Composable
internal fun Sparks(modifier: Modifier) {
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
internal fun RevealMascot(done: Boolean, modifier: Modifier) {
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
        face = if (done) MascotFace.HAPPY else MascotFace.CHOMP,
        modifier = modifier.graphicsLayer {
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
internal fun Glow(modifier: Modifier) {
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
internal fun FlyingBubbles() {
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
