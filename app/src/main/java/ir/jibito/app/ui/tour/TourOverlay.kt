package ir.jibito.app.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.util.Jalali

/** پشت صفحه‌ی راهنما: تیره، ولی نه آن‌قدر که صفحه‌ی زیرش گم شود */
private val Scrim = Color(0xFF0E0C12).copy(alpha = 0.88f)

/** فاصله‌ی متن راهنما از جای روشن */
private val TipGap = 18.dp

/** ثبت جای این بخش برای راهنما. [part]: اگر یک مرحله از چند تکه ساخته می‌شود، شماره‌ی این تکه. بدون [step] کاری نمی‌کند. */
fun Modifier.tourAnchor(step: TourStep?, part: Int = 0): Modifier =
    if (step == null) this else composed {
        val anchors = LocalTourAnchors.current
        DisposableEffect(anchors, step, part) { onDispose { anchors?.remove(step, part) } }
        if (anchors == null) Modifier else Modifier.onGloballyPositioned { anchors.set(step, part, it.boundsInRoot()) }
    }

/**
 * راهنمای کاربر تازه: کل صفحه تیره می‌شود جز همان جایی که توضیح داده می‌شود، و دو سه جمله‌اش در بخش تیره نوشته می‌شود.
 * [enabled] وقتی درست است که صفحه برای راهنما آماده باشد (راهنما فعال است، «خلاصه» بالاست، و صفحه‌ی دیگری روی آن نیست).
 * تا «خلاصه» نیامده (جای دکمه‌ی تنظیمات ثبت نشده)، صبر می‌کند. مرحله‌هایی که جایشان روی صفحه نیست رد می‌شوند.
 * [startAt]: از کدام مرحله شروع شود (اسکرین‌شات‌ها).
 */
@Composable
fun ProductTourOverlay(anchors: TourAnchors, enabled: Boolean, onFinish: () -> Unit, startAt: TourStep = TourStep.entries.first()) {
    if (enabled && TourStep.SETTINGS in anchors.steps) TourContent(anchors, startAt, onFinish)
}

@Composable
private fun TourContent(anchors: TourAnchors, startAt: TourStep, onFinish: () -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    // شماره‌ی آخرین مرحله‌ی دیده‌شده؛ شماره (نه جای فهرست) تا اگر مرحله‌ای از صفحه رفت، جای کاربر نپرد
    var ordinal by rememberSaveable { mutableIntStateOf(startAt.ordinal) }
    val density = LocalDensity.current
    BackHandler(onBack = onFinish)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            // لمس‌ها به صفحه‌ی زیر نرسند
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val onScreen = anchors.steps.filter { step ->
            anchors[step]?.translate(-origin)?.let { it.left >= -1f && it.top >= -1f && it.right <= width + 1f && it.bottom <= height + 1f } == true
        }.toSet()
        val steps = tourSteps(onScreen)
        val step = steps.current(ordinal) ?: return@BoxWithConstraints
        val position = steps.indexOf(step)
        val last = position == steps.lastIndex

        val pad = with(density) { step.padDp.dp.toPx() }
        val target = (anchors[step] ?: return@BoxWithConstraints).translate(-origin).inflate(pad)
        val spot by animateRectAsState(target, tween(300), label = "tourSpot")
        val corner = with(density) { step.cornerDp.dp.toPx() }

        Canvas(
            Modifier
                .fillMaxSize()
                // «سوراخ» کردن پرده با BlendMode.Clear فقط روی لایه‌ی جدا کار می‌کند
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val radius = CornerRadius(minOf(corner, spot.width / 2, spot.height / 2))
            drawRect(Scrim)
            drawRoundRect(Color.Black, spot.topLeft, spot.size, radius, blendMode = BlendMode.Clear)
            drawRoundRect(Color.White.copy(alpha = 0.9f), spot.topLeft, spot.size, radius, style = Stroke(2.dp.toPx()))
        }

        // متن در هر کدام از دو بخش تیره که جای بیشتری دارد
        val below = spot.center.y < height / 2
        val topPad = if (below) with(density) { spot.bottom.toDp() } + TipGap else 0.dp
        val bottomPad = if (below) 0.dp else with(density) { (height - spot.top).toDp() } + TipGap
        Column(
            Modifier
                .align(if (below) Alignment.TopCenter else Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = topPad, bottom = bottomPad)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(step.title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(stringResource(step.text), color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, lineHeight = 26.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Dots(count = steps.size, selected = position)
                Spacer(Modifier.weight(1f))
                val ghost = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.85f))
                if (!last) TextButton(onClick = onFinish, colors = ghost) { Text(stringResource(R.string.tour_skip)) }
                if (position > 0) TextButton(onClick = { ordinal = steps[position - 1].ordinal }, colors = ghost) { Text(stringResource(R.string.tour_prev)) }
                Button(
                    onClick = { if (last) onFinish() else ordinal = steps[position + 1].ordinal },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) { Text(stringResource(if (last) R.string.tour_done else R.string.tour_next), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** نقطه‌های پیشرفت؛ مرحله‌ی جاری بلندتر */
@Composable
private fun Dots(count: Int, selected: Int) {
    val a11y = Jalali.toPersianDigits(stringResource(R.string.tour_step_a11y, selected + 1, count))
    Row(
        Modifier.clearAndSetSemantics { contentDescription = a11y },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Spacer(
                Modifier
                    .height(6.dp)
                    .width(if (i == selected) 18.dp else 6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == selected) 1f else 0.35f)),
            )
        }
    }
}
