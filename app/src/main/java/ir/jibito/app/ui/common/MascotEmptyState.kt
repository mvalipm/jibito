package ir.jibito.app.ui.common

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.jibito.app.ui.theme.Coral
import ir.jibito.app.ui.theme.Teal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** «حذف انیمیشن‌ها»ی گوشی روشن است؟ (انیمیشن‌های بی‌پایان و جشن‌ها خاموش می‌شوند) */
@Composable
fun rememberMotionOff(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * حالت خالی صفحه‌ها با «جیبی»: جیبی (آرام بالا و پایین می‌رود)، یک عنوان و یک جمله.
 * @param celebrate یک بار کاغذرنگی از پشت جیبی بیرون می‌زند (مثلاً وقتی کاربر خودش صندوق را خالی کرد)
 * @param action دکمه‌ی زیر متن (اختیاری)
 */
@Composable
fun MascotEmptyState(
    face: MascotFace,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    mascotSize: Dp = 132.dp,
    celebrate: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    val motionOff = rememberMotionOff()
    val bob = if (motionOff) 0f else {
        val t = rememberInfiniteTransition(label = "bob")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "bobValue")
        v
    }
    val bobPx = with(LocalDensity.current) { 6.dp.toPx() }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (celebrate && !motionOff) ConfettiBurst(Modifier.height(mascotSize * 2).fillMaxWidth())
            PocketMascot(face, size = mascotSize, modifier = Modifier.graphicsLayer { translationY = -bob * bobPx })
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** رنگ‌های کاغذرنگی: رنگ‌های برند */
private val ConfettiColors = listOf(Coral, Teal, Color(0xFFF5B83D), Color(0xFF6D3FC0), Color(0xFFFF9B7A))

/**
 * یک بار کاغذرنگی: تکه‌ها از وسط به بیرون پرتاب می‌شوند، می‌چرخند، با جاذبه پایین می‌آیند و محو می‌شوند.
 * فقط مرحله‌ی کشیدن به‌روز می‌شود (نه چیدمان).
 */
@Composable
private fun ConfettiBurst(modifier: Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1700, easing = LinearEasing)) }
    // جهت و سرعت هر تکه ثابت است (نه تصادفی در هر بار کشیدن)
    val pieces = remember {
        List(CONFETTI_PIECES) { i ->
            val angle = (i.toFloat() / CONFETTI_PIECES) * 2f * PI.toFloat() + (i % 3) * 0.21f
            Piece(angle, speed = 0.55f + (i * 37 % 10) / 22f, size = 6f + (i % 3) * 3f, color = ConfettiColors[i % ConfettiColors.size], round = i % 2 == 0)
        }
    }
    Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val origin = Offset(size.width / 2f, size.height / 2f)
        val reach = size.minDimension * 0.55f
        val alpha = if (t < 0.7f) 1f else (1f - t) / 0.3f
        for (p in pieces) {
            val d = reach * p.speed * (1f - (1f - t) * (1f - t))
            val fall = reach * 0.6f * t * t
            val center = Offset(origin.x + cos(p.angle) * d, origin.y + sin(p.angle) * d - reach * 0.25f * t + fall)
            val s = p.size.dp.toPx()
            rotate(degrees = 540f * t * (if (p.round) 1f else -1f), pivot = center) {
                drawRoundRect(
                    p.color.copy(alpha = alpha),
                    topLeft = Offset(center.x - s / 2, center.y - s / 2),
                    size = Size(s, if (p.round) s else s * 0.5f),
                    cornerRadius = CornerRadius(if (p.round) s / 2 else 1.dp.toPx()),
                )
            }
        }
    }
}

private data class Piece(val angle: Float, val speed: Float, val size: Float, val color: Color, val round: Boolean)

private const val CONFETTI_PIECES = 26
