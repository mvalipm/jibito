package ir.jibito.app.ui.welcome

import android.content.Context
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.ui.common.MascotFace
import ir.jibito.app.ui.common.PocketMascot
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.LocalJibitoColors
import ir.jibito.app.util.Jalali
import kotlin.math.roundToInt

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

/** زمینه‌ی تیره‌ی این لحظه، در هر پوسته (لحظه‌ی برند است) */
private val RevealBackground = Color(0xFF17141C)
private val ButtonPeach = Color(0xFFFF8A65)
private val ButtonInk = Color(0xFF2A0A00)

/** مدت شمارش (میلی‌ثانیه) */
private const val COUNT_MILLIS = 2600

/**
 * لحظه‌ی «آهان!»: بعد از دادن اجازه و اولین خواندن پیامک‌ها.
 * «جیبی» پیامک‌هایی را که به سمتش پرواز می‌کنند می‌بلعد و عدد تراکنش‌ها می‌شمارد؛
 * آخر کار جیبی خوشحال بالا می‌پرد و چند واقعیت (چند ماه، چند بانک) مثل حباب بیرون می‌زنند.
 * اگر «حذف انیمیشن‌ها»ی گوشی روشن باشد، همه‌چیز از اول در حالت نهایی است.
 * روی همه‌چیز می‌نشیند تا کاربر «بزن بریم» را بزند.
 */
@Composable
fun FirstRunReveal(stats: RevealStats, onDone: () -> Unit) {
    val extras = LocalJibitoColors.current
    BackHandler(onBack = onDone)
    val context = LocalContext.current
    val motionOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    val count = remember { Animatable(if (motionOff) stats.count.toFloat() else 0f) }
    var done by remember { mutableStateOf(motionOff) }
    // پرش خوشحالی (پیکسل به بالا)
    val hop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (motionOff) return@LaunchedEffect
        count.animateTo(stats.count.toFloat(), tween(COUNT_MILLIS, easing = FastOutSlowInEasing))
        done = true
        hop.animateTo(-1f, tween(180))
        hop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val buttonAlpha by animateFloatAsState(if (done) 1f else 0.35f, tween(400), label = "button")
    val density = LocalDensity.current
    // عدد درشت تزئینی است و با فونت گوشی بزرگ نمی‌شود (اندازه به dp)؛ صفحه‌خوان آن را می‌خواند
    val bigNumber = with(density) { 96.dp.toSp() }

    Box(
        Modifier
            .fillMaxSize()
            .background(RevealBackground)
            // لمس‌ها به صفحه‌ی زیرش نرسد
            .pointerInput(Unit) { detectTapGestures { } }
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(bottom = 72.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(if (done) R.string.reveal_done else R.string.reveal_reading),
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            // صحنه: هاله، پیامک‌های پرنده، جیبی
            Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier
                        .padding(bottom = 4.dp)
                        .size(220.dp)
                        .drawBehind {
                            drawCircle(
                                Brush.radialGradient(listOf(extras.heroStart.copy(alpha = 0.5f), Color.Transparent)),
                            )
                        }
                )
                if (!done) FlyingMessages(Modifier.fillMaxSize())
                Mascot(chomping = !done && !motionOff, hop = hop.value, face = if (done) MascotFace.HAPPY else MascotFace.CHOMP)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                Jalali.toPersianDigits(count.value.roundToInt().toString()),
                color = Color.White,
                fontSize = bigNumber,
                lineHeight = bigNumber,
                fontWeight = FontWeight.Black,
                // صفحه‌خوان فقط عدد نهایی را می‌خواند، نه هر قدم شمارش را
                modifier = Modifier.clearAndSetSemantics { contentDescription = Jalali.toPersianDigits(stats.count.toString()) },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.reveal_found),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Facts(stats, visible = done, animate = !motionOff)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.reveal_next),
                color = Color.White.copy(alpha = if (done) 0.8f else 0f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Button(
            onClick = onDone,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(58.dp)
                .alpha(buttonAlpha),
            shape = RoundedCornerShape(29.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ButtonPeach, contentColor = ButtonInk),
        ) {
            Text(stringResource(R.string.reveal_go), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
    }
}

/** جیبی؛ موقع شمارش آرام «می‌جود» (کش و فشرده می‌شود)، آخر کار بالا می‌پرد */
@Composable
private fun Mascot(chomping: Boolean, hop: Float, face: MascotFace) {
    val chew = if (chomping) {
        val t = rememberInfiniteTransition(label = "chew")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(290, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "chewValue")
        v
    } else 0f
    val hopPx = with(LocalDensity.current) { 26.dp.toPx() }
    PocketMascot(
        face = face,
        size = 150.dp,
        modifier = Modifier.graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 0.9f)
            scaleX = 1f + 0.07f * chew
            scaleY = 1f - 0.06f * chew
            translationY = hop * hopPx
        },
    )
}

/**
 * پیامک‌های کوچکی که از بالای صحنه به سمت دهان جیبی پرواز می‌کنند و کوچک و محو می‌شوند.
 * فقط مرحله‌ی کشیدن تکرار می‌شود (نه چیدمان).
 */
@Composable
private fun FlyingMessages(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "fly")
    val progress by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1150, easing = LinearEasing)), label = "flyProgress")
    val tints = listOf(Color(0xFF17BEBB), Color(0xFFFF9B7A), Color(0xFFF5B83D))
    Canvas(modifier) {
        val w = size.width
        val bubble = Size(56.dp.toPx(), 36.dp.toPx())
        val target = Offset(w / 2f, size.height - 40.dp.toPx())
        val starts = listOf(0.18f, 0.5f, 0.82f)
        for (i in 0 until BUBBLES) {
            val p = (progress + i.toFloat() / BUBBLES) % 1f
            // شتاب به سمت دهان
            val e = p * p
            val start = Offset(w * starts[i % 3], (if (i % 2 == 0) 0f else 16.dp.toPx()) + bubble.height / 2)
            val center = Offset(start.x + (target.x - start.x) * e, start.y + (target.y - start.y) * e)
            val s = 1f - 0.75f * e
            val alpha = when {
                p < 0.15f -> p / 0.15f
                p > 0.8f -> (1f - p) / 0.2f
                else -> 1f
            }
            val size = Size(bubble.width * s, bubble.height * s)
            val topLeft = Offset(center.x - size.width / 2, center.y - size.height / 2)
            drawRoundRect(
                Color.White.copy(alpha = 0.16f * alpha),
                topLeft = topLeft,
                size = size,
                cornerRadius = CornerRadius(10.dp.toPx() * s),
            )
            val lineH = 4.dp.toPx() * s
            val pad = 9.dp.toPx() * s
            drawRoundRect(
                Color.White.copy(alpha = 0.6f * alpha),
                topLeft = Offset(topLeft.x + pad, topLeft.y + pad),
                size = Size(size.width * 0.7f, lineH),
                cornerRadius = CornerRadius(lineH / 2),
            )
            drawRoundRect(
                tints[i % 3].copy(alpha = alpha),
                topLeft = Offset(topLeft.x + pad, topLeft.y + pad + lineH * 2),
                size = Size(size.width * 0.45f, lineH),
                cornerRadius = CornerRadius(lineH / 2),
            )
        }
    }
}

private const val BUBBLES = 9

/** «از ۶ ماه گذشته» · «توی ۳ بانک»: بعد از شمارش، یکی‌یکی با جهش بیرون می‌زنند */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(stats: RevealStats, visible: Boolean, animate: Boolean) {
    val facts = buildList {
        add(JibitoIcons.Calendar to Jalali.toPersianDigits(stringResource(R.string.reveal_fact_months, stats.months)))
        if (stats.banks > 0) add(JibitoIcons.Bank to Jalali.toPersianDigits(stringResource(R.string.reveal_fact_banks, stats.banks)))
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        facts.forEachIndexed { i, (icon, text) -> FactPill(icon, text, visible, if (animate) i * 130 else 0, animate) }
    }
}

@Composable
private fun FactPill(icon: ImageVector, text: String, visible: Boolean, delay: Int, animate: Boolean) {
    val spec: FiniteAnimationSpec<Float> = when {
        !animate -> snap()
        delay == 0 -> spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        else -> tween(450, delayMillis = delay, easing = FastOutSlowInEasing)
    }
    val p by animateFloatAsState(if (visible) 1f else 0f, spec, label = "fact")
    Row(
        Modifier
            .graphicsLayer {
                alpha = p.coerceIn(0f, 1f)
                scaleX = 0.7f + 0.3f * p
                scaleY = 0.7f + 0.3f * p
                translationY = (1f - p) * 16.dp.toPx()
            }
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
