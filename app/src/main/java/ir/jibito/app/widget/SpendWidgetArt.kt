package ir.jibito.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import ir.jibito.app.R

/**
 * تصویرهای ویجت (RemoteViews ابزار رسم ندارد): نمودار هفت روز اخیر و نوار ماه با نشانگر زمان.
 * اندازه‌ها به dp است و تصویر با تراکم صفحه‌ی گوشی کشیده می‌شود؛ ImageView با fitXY در جای واقعی‌اش می‌نشاند.
 * محور زمان راست‌به‌چپ است: قدیمی‌ترین روز سمت راست، امروز سمت چپ.
 */
internal object SpendWidgetArt {

    /** شفافیت ستون روزهای عادی و روزهایی که از سهم روزانه رد شده‌اند */
    private const val ALPHA_DAY = 105
    private const val ALPHA_HIGH = 242

    /**
     * نوار بودجه‌ی ماه: ریل کم‌رنگ، پر شدن از راست به اندازه‌ی [spent]، و خط عمودی در [pace] (چقدر از ماه گذشته).
     * دور خط عمودی کمی خالی می‌ماند تا روی بخش پرشده هم دیده شود.
     */
    fun monthBar(context: Context, widthDp: Float, spent: Float, pace: Float, color: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (widthDp * d).toInt().coerceAtLeast(1)
        val h = (12 * d).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val barH = 8 * d
        val top = (h - barH) / 2
        val r = barH / 2
        paint.alpha = 62
        canvas.drawRoundRect(RectF(0f, top, w.toFloat(), top + barH), r, r, paint)
        val fill = spent.coerceIn(0f, 1f) * w
        if (fill > 0) {
            paint.alpha = 255
            canvas.drawRoundRect(RectF(w - fill, top, w.toFloat(), top + barH), r, r, paint)
        }
        val x = w - pace.coerceIn(0f, 1f) * w
        val tick = 2 * d
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        canvas.drawRect(x - tick / 2 - 1.5f * d, 0f, x + tick / 2 + 1.5f * d, h.toFloat(), clear)
        paint.alpha = 255
        canvas.drawRoundRect(RectF(x - tick / 2, 0f, x + tick / 2, h.toFloat()), tick / 2, tick / 2, paint)
        return bitmap
    }

    /**
     * نمودار هفت روز اخیر: هر ستون خرج یک روز ([week]، قدیمی به جدید)، و خط‌چین سهم روزانه ([share]) اگر بودجه هست.
     * روزهایی که از سهم رد شده‌اند ([allHigh]: همه، وقتی بودجه‌ی ماه رد شده) پررنگ‌ترند؛ امروزِ بدون خرج یک قاب خط‌چین است.
     */
    fun weekChart(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        week: List<Long>,
        labels: List<String>,
        share: Long?,
        allHigh: Boolean,
        color: Int,
        muted: Int,
    ): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (widthDp * d).toInt().coerceAtLeast(1)
        val h = (heightDp * d).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val n = week.size.coerceAtLeast(1)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, context.resources.displayMetrics)
            textAlign = Paint.Align.CENTER
            typeface = font(context, R.font.vazirmatn_medium)
        }
        val labelH = 16 * d
        val plotTop = 3 * d
        val plotBottom = h - labelH
        val plotH = (plotBottom - plotTop).coerceAtLeast(1f)
        val gap = 8 * d
        val col = (w - gap * (n - 1)) / n
        val barW = minOf(col, 26 * d)
        val top = maxOf(week.maxOrNull() ?: 0L, share ?: 0L).coerceAtLeast(1L) * 1.12f

        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * d
            pathEffect = DashPathEffect(floatArrayOf(3 * d, 2.5f * d), 0f)
        }
        val radius = 5 * d
        week.forEachIndexed { i, value ->
            val today = i == week.lastIndex
            val right = w - i * (col + gap)
            val cx = right - col / 2
            val left = cx - barW / 2
            if (today && value <= 0) {
                val hh = 12 * d
                canvas.drawRoundRect(RectF(left, plotBottom - hh, left + barW, plotBottom), radius, radius, outline)
            } else {
                val bh = (value / top * plotH).coerceAtLeast(3 * d)
                val high = allHigh || (share != null && value > share) || (share == null && today)
                bar.alpha = if (high) ALPHA_HIGH else ALPHA_DAY
                canvas.drawRoundRect(RectF(left, plotBottom - bh, left + barW, plotBottom), radius, radius, bar)
                // پایه‌ی ستون صاف باشد (فقط سرش گرد)
                canvas.drawRect(left, plotBottom - minOf(bh, radius), left + barW, plotBottom, bar)
            }
            text.color = if (today) color else muted
            text.typeface = font(context, if (today) R.font.vazirmatn_black else R.font.vazirmatn_medium)
            canvas.drawText(labels.getOrElse(i) { "" }, cx, h - 3 * d, text)
        }

        if (share != null && share > 0) {
            val y = plotBottom - share / top * plotH
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                alpha = 190
                strokeWidth = 1.5f * d
                pathEffect = DashPathEffect(floatArrayOf(5 * d, 4 * d), 0f)
            }
            canvas.drawLine(0f, y, w.toFloat(), y, line)
        }
        return bitmap
    }

    private fun font(context: Context, id: Int): Typeface =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: Typeface.DEFAULT
}
