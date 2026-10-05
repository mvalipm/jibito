package ir.jibito.app.ui.reports

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.jibito.app.ui.common.rememberHaptics
import kotlin.math.roundToInt

/** داده‌ی نمودار خطی «گزارش‌ها»؛ مبلغ‌ها ریال */
@Immutable
data class LineChartData(
    /** چند نقطه روی محور افقی (مثلاً ۳۰ روز ماه یا ۶ ماه) */
    val slots: Int,
    /** مقدارهای واقعی، از نقطه‌ی ۰ */
    val values: List<Long>,
    /** خط کم‌رنگِ پشت (ماه قبل) روی همان محور؛ اضافه‌اش بریده می‌شود */
    val ghost: List<Long> = emptyList(),
    /** پیش‌بینی، از آخرین نقطه‌ی واقعی (اولین عضوش همان نقطه است) */
    val projection: List<Long> = emptyList(),
    /** خط افقی مرجع (بودجه یا میانگین) */
    val reference: Long? = null,
    /** محور عمودی از صفر (خرج تجمعی) یا از نزدیکِ کمترین مقدار (خرج ماه‌ها) */
    val fromZero: Boolean = true,
) {
    /** آخرین نقطه‌ای که می‌شود رویش انگشت گذاشت (با پیش‌بینی) */
    val lastIndex: Int get() = values.lastIndex + (projection.size - 1).coerceAtLeast(0)

    fun valueAt(i: Int): Long? = values.getOrNull(i) ?: projection.getOrNull(i - values.lastIndex)

    internal val min: Double
    internal val max: Double

    init {
        val all = values + ghost.take(slots) + projection + listOfNotNull(reference)
        val hi = (all.maxOrNull() ?: 0L).toDouble()
        val lo = if (fromZero) 0.0 else (all.minOrNull() ?: 0L) * 0.8
        min = lo
        max = maxOf(hi * 1.08, lo + 1.0)
    }
}

/** جای حباب بالای نمودار */
private val TipSpace = 52.dp
private val ChartHeight = 168.dp
private val PadTop = 10.dp
private val PadBottom = 8.dp

/**
 * نمودار خطی خرج: خط اصلی با سایه‌ی محو زیرش، خط‌چین کم‌رنگ ماه قبل، نقطه‌چین پیش‌بینی و خط‌چین بودجه/میانگین.
 * با لمس و کشیدن انگشت، نقطه‌ی زیر انگشت انتخاب می‌شود (با لرزش کوتاه) و [tooltip] بالایش می‌آید؛
 * با برداشتن انگشت دوباره به حالت عادی برمی‌گردد. محور زمان حتی در راست‌به‌چپ از چپ به راست است.
 *
 * @param selected نقطه‌ی انتخاب‌شده؛ null یعنی انگشتی روی نمودار نیست (نقطه روی آخرین مقدار واقعی است)
 */
@Composable
fun SpendLineChart(
    data: LineChartData,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    lineColor: Color,
    ghostColor: Color,
    referenceColor: Color,
    description: String,
    modifier: Modifier = Modifier,
    referenceLabel: (@Composable () -> Unit)? = null,
    tooltip: @Composable (Int) -> Unit = {},
) {
    val haptics = rememberHaptics()
    val select by rememberUpdatedState(onSelect)
    var lastPick by remember { mutableIntStateOf(-1) }
    // خط از چپ به راست کشیده می‌شود
    val reveal = remember(data) { Animatable(0f) }
    LaunchedEffect(data) { reveal.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val density = LocalDensity.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier
                .fillMaxWidth()
                .height(TipSpace + ChartHeight)
                .clearAndSetSemantics { contentDescription = description }
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val chartPx = with(density) { ChartHeight.toPx() }
            fun xOf(i: Int): Float = if (data.slots <= 1) widthPx / 2 else i * widthPx / (data.slots - 1)
            fun yOf(v: Long): Float {
                val top = with(density) { PadTop.toPx() }
                val inner = chartPx - top - with(density) { PadBottom.toPx() }
                return top + (1f - ((v - data.min) / (data.max - data.min)).toFloat()) * inner
            }
            fun pick(x: Float) {
                if (data.values.isEmpty() || widthPx <= 0f) return
                val i = (x / widthPx * (data.slots - 1)).roundToInt().coerceIn(0, data.lastIndex)
                if (i != lastPick) {
                    lastPick = i
                    haptics.tick()
                    select(i)
                }
            }
            fun release() {
                lastPick = -1
                select(null)
            }

            Canvas(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(ChartHeight)
                    .pointerInput(data, widthPx) {
                        detectTapGestures(onPress = { pos ->
                            pick(pos.x)
                            tryAwaitRelease()
                            release()
                        })
                    }
                    .pointerInput(data, widthPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { pick(it.x) },
                            onDragEnd = { release() },
                            onDragCancel = { release() },
                        ) { change, _ ->
                            change.consume()
                            pick(change.position.x)
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                fun path(points: List<Long>, from: Int): Path = Path().apply {
                    points.forEachIndexed { j, v ->
                        val x = xOf(from + j)
                        val y = yOf(v)
                        if (j == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                val dash = 5.dp.toPx()

                data.reference?.let { ref ->
                    val y = yOf(ref)
                    drawLine(
                        referenceColor, Offset(0f, y), Offset(w, y),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                    )
                }
                val ghost = data.ghost.take(data.slots)
                if (ghost.size > 1) {
                    drawPath(
                        path(ghost, 0), ghostColor,
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))),
                    )
                }
                if (data.values.isEmpty()) return@Canvas

                clipRect(right = w * reveal.value) {
                    val line = path(data.values, 0)
                    if (data.values.size > 1) {
                        val area = Path().apply {
                            addPath(line)
                            lineTo(xOf(data.values.lastIndex), h)
                            lineTo(xOf(0), h)
                            close()
                        }
                        drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.24f), lineColor.copy(alpha = 0f)), startY = 0f, endY = h))
                    }
                    if (data.projection.size > 1) {
                        drawPath(
                            path(data.projection, data.values.lastIndex), lineColor.copy(alpha = 0.75f),
                            style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 5.dp.toPx()))),
                        )
                    }
                    drawPath(line, lineColor, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                val active = selected ?: data.values.lastIndex
                val value = data.valueAt(active) ?: return@Canvas
                val x = xOf(active)
                val y = yOf(value)
                if (selected != null) {
                    drawLine(
                        ghostColor, Offset(x, 0f), Offset(x, h),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                    )
                }
                if (reveal.value >= 0.99f || selected != null) {
                    drawCircle(lineColor.copy(alpha = 0.16f), radius = 9.dp.toPx(), center = Offset(x, y))
                    drawCircle(Color.White, radius = 4.5.dp.toPx(), center = Offset(x, y))
                    drawCircle(lineColor, radius = 4.5.dp.toPx(), center = Offset(x, y), style = Stroke(2.4.dp.toPx()))
                }
            }

            // برچسب خط مرجع: راستِ نمودار، درست بالای خط
            if (referenceLabel != null && data.reference != null) {
                val y = with(density) { TipSpace.toPx() } + yOf(data.reference)
                Box(
                    Modifier.layout { measurable, c ->
                        val p = measurable.measure(c.copy(minWidth = 0, minHeight = 0))
                        layout(c.maxWidth, c.maxHeight) {
                            p.place(c.maxWidth - p.width, (y - p.height - 2.dp.roundToPx()).roundToInt().coerceAtLeast(0))
                        }
                    }
                ) { referenceLabel() }
            }

            // حباب نقطه‌ی انتخاب‌شده: بالای نمودار، هم‌راستای انگشت ولی داخل کادر
            if (selected != null) {
                val x = xOf(selected)
                Box(
                    Modifier.layout { measurable, c ->
                        val p = measurable.measure(c.copy(minWidth = 0, minHeight = 0))
                        layout(c.maxWidth, p.height) {
                            p.place((x - p.width / 2f).roundToInt().coerceIn(0, (c.maxWidth - p.width).coerceAtLeast(0)), 0)
                        }
                    }
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { tooltip(selected) }
                }
            }
        }
    }
}

