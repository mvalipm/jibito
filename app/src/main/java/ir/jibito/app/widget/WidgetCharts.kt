package ir.jibito.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import ir.jibito.app.R

/**
 * نمودارهای کوچک ویجت به شکل تصویر (RemoteViews نمی‌تواند خودش بکشد).
 * تصویر با عوض شدن حالت روشن/تیره‌ی گوشی دوباره کشیده نمی‌شود، پس رنگ‌ها روی هر دو زمینه خوانا انتخاب شده‌اند:
 * خاکستری گرم نیمه‌شفاف برای روزهای دیگر و مرجانیِ جیبی برای امروز.
 */
internal object WidgetCharts {

    private const val NEUTRAL = 0xFF8A817C.toInt()
    private const val PAST_BAR = 0x528A817C
    private const val AVERAGE_LINE = 0xCC8A817C.toInt()
    private const val TODAY = 0xFFE4572E.toInt()

    /** بیشتر از این تراکم، تصویر فقط حافظه می‌خورد (محدودیت حجم RemoteViews) */
    private const val MAX_DENSITY = 3f

    private fun density(context: Context) = context.resources.displayMetrics.density.coerceAtMost(MAX_DENSITY)

    private fun font(context: Context, bold: Boolean): Typeface =
        runCatching { ResourcesCompat.getFont(context, if (bold) R.font.vazirmatn_bold else R.font.vazirmatn_regular) }
            .getOrNull() ?: Typeface.DEFAULT

    /**
     * ستون‌های خرج هر روز ([values]، قدیمی‌ترین اول؛ امروز سمت چپ، چون روزها از راست به چپ جلو می‌روند)
     * با حرف اول روز زیر هر ستون و خط‌چینِ «یک روز معمولی».
     */
    fun week(context: Context, values: List<Long>, letters: List<String>, typical: Long?, widthDp: Int, heightDp: Int): Bitmap {
        val d = density(context)
        val w = (widthDp * d).toInt()
        val h = (heightDp * d).toInt()
        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        val labelArea = 16 * d
        val chartH = h - labelArea
        val gap = 5 * d
        val n = values.size.coerceAtLeast(1)
        val barW = (w - gap * (n - 1)) / n
        val max = maxOf(values.maxOrNull() ?: 0L, typical ?: 0L, 1L).toFloat()
        val bar = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = 4 * d
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10 * d
            textAlign = Paint.Align.CENTER
        }
        val regular = font(context, bold = false)
        val bold = font(context, bold = true)
        values.forEachIndexed { i, v ->
            val today = i == values.lastIndex
            val right = w - i * (barW + gap)
            val top = chartH - (v / max * chartH).coerceAtLeast(3 * d)
            bar.color = if (today) TODAY else PAST_BAR
            canvas.drawRoundRect(RectF(right - barW, top, right, chartH), radius, radius, bar)
            letters.getOrNull(i)?.let {
                label.color = if (today) TODAY else NEUTRAL
                label.typeface = if (today) bold else regular
                canvas.drawText(it, right - barW / 2, h - 3 * d, label)
            }
        }
        if (typical != null && typical > 0) {
            val y = chartH - typical / max * chartH
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AVERAGE_LINE
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * d
                pathEffect = DashPathEffect(floatArrayOf(3 * d, 3 * d), 0f)
            }
            canvas.drawPath(Path().apply { moveTo(0f, y); lineTo(w.toFloat(), y) }, line)
        }
        return bitmap
    }

    /** روند مانده در چند روز (قدیمی‌ترین اول، از راست به چپ) با نقطه‌ای روی امروز */
    fun spark(context: Context, values: List<Long>, widthDp: Int, heightDp: Int): Bitmap {
        val d = density(context)
        val w = (widthDp * d).toInt()
        val h = (heightDp * d).toInt()
        val bitmap = createBitmap(w, h)
        if (values.isEmpty()) return bitmap
        val canvas = Canvas(bitmap)
        val pad = 3 * d
        val max = values.max()
        val min = values.min()
        val range = (max - min).toFloat()
        val step = if (values.size > 1) (w - 2 * pad) / (values.size - 1) else 0f
        fun x(i: Int) = w - pad - i * step
        fun y(v: Long) = if (range == 0f) h / 2f else h - pad - (v - min) / range * (h - 2 * pad)
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AVERAGE_LINE
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * d
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(path, stroke)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NEUTRAL }
        canvas.drawCircle(x(values.lastIndex), y(values.last()), 2.4f * d, dot)
        return bitmap
    }
}
