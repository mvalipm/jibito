package ir.jibito.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * آیکون‌های خطی داخل صفحه‌ها، هم‌خانواده‌ی آیکون‌های نوار پایین (NavIcons):
 * صفحه‌ی ۲۴×۲۴، خط ۱٫۸، سر و گوشه‌ی گرد. جای ایموجی و نویسه‌هایی مثل 🔍 ✕ ↑ که روی هر گوشی شکل دیگری داشتند.
 * رنگ را Icon(tint) تعیین می‌کند. آیکون‌های جهت‌دار (برگشت، ادامه) در راست‌به‌چپ خودشان قرینه می‌شوند.
 */
object JibitoIcons {

    private fun lineIcon(name: String, autoMirror: Boolean = false, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = autoMirror).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }

    /** نقطه (با سر گرد خط) */
    private fun PathBuilder.dot(x: Float, y: Float) {
        moveTo(x, y - 0.05f)
        lineTo(x, y + 0.05f)
    }

    val Search: ImageVector by lazy {
        lineIcon("search") {
            circle(10.5f, 10.5f, 6.5f)
            moveTo(15.4f, 15.4f); lineTo(20f, 20f)
        }
    }

    val Close: ImageVector by lazy {
        lineIcon("close") {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    val Plus: ImageVector by lazy {
        lineIcon("plus") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    /** برگشت: در راست‌به‌چپ به سمت راست */
    val Back: ImageVector by lazy {
        lineIcon("back", autoMirror = true) {
            moveTo(19f, 12f); lineTo(5f, 12f)
            moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
        }
    }

    /** ادامه / باز کردن: در راست‌به‌چپ به سمت چپ */
    val ChevronForward: ImageVector by lazy {
        lineIcon("chevron_forward", autoMirror = true) {
            moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f)
        }
    }

    /** پایین (فلش «پیامک ← تراکنش» در خوش‌آمد) */
    val ChevronDown: ImageVector by lazy {
        lineIcon("chevron_down") {
            moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f)
        }
    }

    val Check: ImageVector by lazy {
        lineIcon("check") {
            moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f)
        }
    }

    val Warning: ImageVector by lazy {
        lineIcon("warning") {
            moveTo(12f, 4f); lineTo(21f, 19.5f); lineTo(3f, 19.5f); close()
            moveTo(12f, 10f); lineTo(12f, 14f)
            dot(12f, 16.8f)
        }
    }

    /** برچسب (دسته) */
    val Tag: ImageVector by lazy {
        lineIcon("tag") {
            moveTo(4f, 4f); lineTo(11f, 4f); lineTo(20f, 13f); lineTo(13f, 20f); lineTo(4f, 11f); close()
            circle(8.5f, 8.5f, 1.4f)
        }
    }

    /** انتقال بین حساب‌های خودم */
    val Transfer: ImageVector by lazy {
        lineIcon("transfer") {
            moveTo(4f, 8f); lineTo(19f, 8f)
            moveTo(15.5f, 4.5f); lineTo(19f, 8f); lineTo(15.5f, 11.5f)
            moveTo(20f, 16f); lineTo(5f, 16f)
            moveTo(8.5f, 12.5f); lineTo(5f, 16f); lineTo(8.5f, 19.5f)
        }
    }

    /** پیامک */
    val Message: ImageVector by lazy {
        lineIcon("message") {
            moveTo(5.5f, 6f)
            lineTo(18.5f, 6f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = 2f)
            lineTo(20.5f, 16f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = 2f)
            lineTo(5.5f, 18f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = -2f)
            lineTo(3.5f, 8f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = -2f)
            close()
            moveTo(4.5f, 7.5f); lineTo(12f, 13f); lineTo(19.5f, 7.5f)
        }
    }

    val Bell: ImageVector by lazy {
        lineIcon("bell") {
            moveTo(6f, 16.5f)
            lineTo(6f, 11f)
            arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 18f, y1 = 11f)
            lineTo(18f, 16.5f)
            moveTo(4.5f, 16.5f); lineTo(19.5f, 16.5f)
            moveTo(10f, 19.8f); lineTo(14f, 19.8f)
        }
    }

    /** برداشت / خرج */
    val ArrowUp: ImageVector by lazy {
        lineIcon("arrow_up") {
            moveTo(12f, 19f); lineTo(12f, 5f)
            moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f)
        }
    }

    /** واریز / درآمد */
    val ArrowDown: ImageVector by lazy {
        lineIcon("arrow_down") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(6f, 13f); lineTo(12f, 19f); lineTo(18f, 13f)
        }
    }

    /** پول برگشته (خرید ناموفق) */
    val Refund: ImageVector by lazy {
        lineIcon("refund") {
            moveTo(5f, 12f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 19f)
            moveTo(2f, 9f); lineTo(5f, 12f); lineTo(8f, 9f)
        }
    }

    /** پرداخت تکراری (ماهانه) */
    val Repeat: ImageVector by lazy {
        lineIcon("repeat") {
            moveTo(5f, 12f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 19f, y1 = 12f)
            moveTo(16.8f, 9.8f); lineTo(19f, 12f); lineTo(21.2f, 9.8f)
            moveTo(19f, 12f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 5f, y1 = 12f)
            moveTo(2.8f, 14.2f); lineTo(5f, 12f); lineTo(7.2f, 14.2f)
        }
    }

    /** بودجه نزدیک به تمام شدن: نمودار دایره‌ای با برش بزرگ */
    val Gauge: ImageVector by lazy {
        lineIcon("gauge") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 3.5f); lineTo(12f, 12f); lineTo(3.5f, 12f)
        }
    }

    /** تقویم (چند ماه) */
    val Calendar: ImageVector by lazy {
        lineIcon("calendar") {
            moveTo(5f, 6f); lineTo(19f, 6f); lineTo(19f, 20f); lineTo(5f, 20f); close()
            moveTo(5f, 10f); lineTo(19f, 10f)
            moveTo(9f, 3.5f); lineTo(9f, 7.5f)
            moveTo(15f, 3.5f); lineTo(15f, 7.5f)
        }
    }

    /** بانک (ستون‌دار) */
    val Bank: ImageVector by lazy {
        lineIcon("bank") {
            moveTo(3.5f, 9.5f); lineTo(12f, 4f); lineTo(20.5f, 9.5f)
            moveTo(6f, 10.5f); lineTo(6f, 17f)
            moveTo(10f, 10.5f); lineTo(10f, 17f)
            moveTo(14f, 10.5f); lineTo(14f, 17f)
            moveTo(18f, 10.5f); lineTo(18f, 17f)
            moveTo(3.5f, 20f); lineTo(20.5f, 20f)
        }
    }

    /** پوسته / رنگ‌ها: پالت نقاشی */
    val Palette: ImageVector by lazy {
        lineIcon("palette") {
            moveTo(12f, 3.5f)
            arcTo(8.5f, 8.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 12.5f, y1 = 20.5f)
            curveTo(14f, 20.5f, 14.5f, 19.5f, 13.8f, 18.4f)
            curveTo(13f, 17.2f, 13.8f, 15.5f, 15.5f, 15.5f)
            lineTo(17.5f, 15.5f)
            curveTo(19.2f, 15.5f, 20.5f, 14.2f, 20.5f, 12f)
            curveTo(20.5f, 7.3f, 16.7f, 3.5f, 12f, 3.5f)
            close()
            dot(8f, 10f)
            dot(12f, 7.5f)
            dot(16f, 10f)
        }
    }

    /** لایه‌ها (نمایش دسته‌ها) */
    val Layers: ImageVector by lazy {
        lineIcon("layers") {
            moveTo(12f, 4f); lineTo(20.5f, 8.5f); lineTo(12f, 13f); lineTo(3.5f, 8.5f); close()
            moveTo(3.5f, 12.5f); lineTo(12f, 17f); lineTo(20.5f, 12.5f)
            moveTo(3.5f, 16.5f); lineTo(12f, 21f); lineTo(20.5f, 16.5f)
        }
    }

    /** قفل (قفل اپ و پشتیبان) */
    val Lock: ImageVector by lazy {
        lineIcon("lock") {
            moveTo(5.5f, 11f); lineTo(18.5f, 11f); lineTo(18.5f, 20f); lineTo(5.5f, 20f); close()
            moveTo(8f, 11f); lineTo(8f, 8f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 16f, y1 = 8f)
            lineTo(16f, 11f)
            moveTo(12f, 14.5f); lineTo(12f, 16.5f)
        }
    }

    /** خروجی گرفتن (فایل) */
    val Export: ImageVector by lazy {
        lineIcon("export") {
            moveTo(12f, 3.5f); lineTo(12f, 14.5f)
            moveTo(7.5f, 8f); lineTo(12f, 3.5f); lineTo(16.5f, 8f)
            moveTo(4.5f, 13f); lineTo(4.5f, 19.5f); lineTo(19.5f, 19.5f); lineTo(19.5f, 13f)
        }
    }
}
