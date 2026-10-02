package ir.jibito.app.ui.main

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * آیکون‌های خطی نوار پایین (طراحی خود جیبیتو، بدون کتابخانه).
 * همه روی صفحه‌ی ۲۴×۲۴ با خط ۱٫۸ کشیده شده‌اند؛ رنگشان را Icon(tint) تعیین می‌کند.
 */
object NavIcons {

    private fun lineIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    /** دایره‌ی کامل با دو کمان */
    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }

    /** خلاصه: نمودار دایره‌ای */
    val Summary: ImageVector by lazy {
        lineIcon("summary") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 3.5f)
            lineTo(12f, 12f)
            lineTo(19.36f, 16.25f)
        }
    }

    /** تراکنش‌ها: رسید با لبه‌ی دندانه‌ای */
    val Transactions: ImageVector by lazy {
        lineIcon("transactions") {
            moveTo(6f, 3.5f)
            lineTo(18f, 3.5f)
            lineTo(18f, 20.5f)
            lineTo(16f, 19.2f)
            lineTo(14f, 20.5f)
            lineTo(12f, 19.2f)
            lineTo(10f, 20.5f)
            lineTo(8f, 19.2f)
            lineTo(6f, 20.5f)
            close()
            moveTo(9f, 8.5f)
            lineTo(15f, 8.5f)
            moveTo(9f, 12f)
            lineTo(15f, 12f)
            moveTo(9f, 15.5f)
            lineTo(12f, 15.5f)
        }
    }

    /** بررسی: حباب پیام */
    val Review: ImageVector by lazy {
        lineIcon("review") {
            moveTo(6f, 4f)
            lineTo(18f, 4f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = 2f)
            lineTo(20f, 15f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = 2f)
            lineTo(11f, 17f)
            lineTo(7f, 20.5f)
            lineTo(7f, 17f)
            lineTo(6f, 17f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = -2f)
            lineTo(4f, 6f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = -2f)
            close()
            moveTo(8f, 9f)
            lineTo(16f, 9f)
            moveTo(8f, 12.5f)
            lineTo(13f, 12.5f)
        }
    }

    /** تنظیمات: سه اسلایدر */
    val Settings: ImageVector by lazy {
        lineIcon("settings") {
            // ردیف بالا، دستگیره روی ۹
            moveTo(4f, 6f); lineTo(7f, 6f)
            circle(9f, 6f, 2f)
            moveTo(11f, 6f); lineTo(20f, 6f)
            // ردیف وسط، دستگیره روی ۱۵
            moveTo(4f, 12f); lineTo(13f, 12f)
            circle(15f, 12f, 2f)
            moveTo(17f, 12f); lineTo(20f, 12f)
            // ردیف پایین، دستگیره روی ۸
            moveTo(4f, 18f); lineTo(6f, 18f)
            circle(8f, 18f, 2f)
            moveTo(10f, 18f); lineTo(20f, 18f)
        }
    }
}
