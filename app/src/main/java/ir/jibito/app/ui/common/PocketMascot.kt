package ir.jibito.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.jibito.app.ui.theme.Coral
import ir.jibito.app.ui.theme.Ink
import ir.jibito.app.ui.theme.Teal

/** حالت چهره‌ی «جیبی» */
enum class MascotFace {
    /** چشم باز، دهان باز: دارد پیامک‌ها را می‌خورد */
    CHOMP,
    /** چشم‌های خندان و لبخند */
    HAPPY,
    /** چشم باز رو به بالا، دهان کوچک: «این چیه؟» (منتظر بررسی، فهرست خالی) */
    CURIOUS,
}

/**
 * «جیبی»: همان جیب و سکه‌ی لوگو، با چشم و دهان. فقط تزئینی است (صفحه‌خوان نمی‌خواند).
 * روی هر زمینه‌ای دیده می‌شود: بدنه مرجانی برند، سکه فیروزه‌ای.
 * طراحی در یک کادر ۱۲۰×۱۳۰ انجام شده و به اندازه‌ی [size] (عرض) کشیده می‌شود.
 */
@Composable
fun PocketMascot(face: MascotFace, size: Dp = 140.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = size, height = size * (130f / 120f))) {
        scale(this.size.width / 120f, pivot = Offset.Zero) {
            // سکه
            drawCircle(Teal, radius = 13f, center = Offset(80f, 22f))
            drawCircle(Color.White, radius = 6f, center = Offset(80f, 22f), style = Stroke(width = 3f))
            // بدنه‌ی جیب
            val body = Path().apply {
                moveTo(14f, 34f)
                lineTo(106f, 34f)
                lineTo(106f, 68f)
                cubicTo(106f, 96f, 80f, 116f, 60f, 126f)
                cubicTo(40f, 116f, 14f, 96f, 14f, 68f)
                close()
            }
            drawPath(body, Coral)
            // لبه‌ی بالای جیب
            drawRoundRect(BandColor, topLeft = Offset(14f, 34f), size = Size(92f, 16f), cornerRadius = CornerRadius(5f))
            when (face) {
                MascotFace.CHOMP -> {
                    drawOval(Color.White, topLeft = Offset(38f, 71f), size = Size(16f, 18f))
                    drawOval(Color.White, topLeft = Offset(66f, 71f), size = Size(16f, 18f))
                    drawCircle(Ink, radius = 4f, center = Offset(47f, 76f))
                    drawCircle(Ink, radius = 4f, center = Offset(75f, 76f))
                    drawOval(Ink, topLeft = Offset(53f, 95f), size = Size(14f, 12f))
                }
                MascotFace.CURIOUS -> {
                    drawOval(Color.White, topLeft = Offset(38f, 71f), size = Size(16f, 18f))
                    drawOval(Color.White, topLeft = Offset(66f, 71f), size = Size(16f, 18f))
                    drawCircle(Ink, radius = 4f, center = Offset(48f, 75f))
                    drawCircle(Ink, radius = 4f, center = Offset(76f, 75f))
                    drawLine(Color.White, Offset(54f, 101f), Offset(66f, 101f), strokeWidth = 4f, cap = StrokeCap.Round)
                }
                MascotFace.HAPPY -> {
                    val smile = Path().apply {
                        moveTo(40f, 82f); quadraticTo(46f, 74f, 52f, 82f)
                        moveTo(68f, 82f); quadraticTo(74f, 74f, 80f, 82f)
                        moveTo(46f, 96f); quadraticTo(60f, 110f, 74f, 96f)
                    }
                    drawPath(smile, Color.White, style = Stroke(width = 4f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

private val BandColor = Color(0xFFFF9B7A)
