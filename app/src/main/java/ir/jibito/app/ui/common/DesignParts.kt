package ir.jibito.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.ui.theme.CategoryTint
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme

// ── «جیبی»، شخصیت اپ: یک جیب مرجانی با سکه‌ی فیروزه‌ای ──

private val MascotCoin = Color(0xFF17BEBB)
private val MascotBody = Color(0xFFE4572E)
private val MascotFlap = Color(0xFFFF9B7A)
private val MascotInk = Color(0xFF1C1B22)

private val pocketPath by lazy { PathParser().parsePathString("M14 34h92v34c0 28-26 48-46 58C40 116 14 96 14 68z").toPath() }
private val smilePath by lazy { PathParser().parsePathString("M40 82q6-8 12 0M68 82q6-8 12 0M46 96q14 14 28 0").toPath() }

/** حالت صورت جیبی */
enum class MascotFace {
    /** چشم‌های خندان و لبخند */
    HAPPY,
    /** چشم‌های باز و دهان صاف: «مطمئن نیستم» */
    UNSURE,
    /** چشم‌های باز و دهان باز: در حال خواندن پیامک‌ها */
    CHOMP,
}

/**
 * جیبی (صفحه‌ی ۱۲۰×۱۳۰ طرح). تزئینی است؛ صفحه‌خوان آن را نمی‌خواند.
 * @param width ارتفاع خودش از نسبت ۱۲۰:۱۳۰ می‌آید
 * @param smileStroke ضخامت خط لبخند (در اندازه‌های خیلی کوچک پررنگ‌تر)
 */
@Composable
fun Mascot(width: Dp, face: MascotFace = MascotFace.HAPPY, modifier: Modifier = Modifier, smileStroke: Float = 4f) {
    Canvas(modifier.size(width, width * 130f / 120f)) {
        scale(size.width / 120f, size.height / 130f, pivot = Offset.Zero) {
            drawCircle(MascotCoin, radius = 13f, center = Offset(80f, 22f))
            drawCircle(Color.White, radius = 6f, center = Offset(80f, 22f), style = Stroke(width = 3f))
            drawPath(pocketPath, MascotBody)
            drawRoundRect(MascotFlap, topLeft = Offset(14f, 34f), size = Size(92f, 16f), cornerRadius = CornerRadius(5f, 5f))
            when (face) {
                MascotFace.HAPPY -> drawPath(smilePath, Color.White, style = Stroke(width = smileStroke, cap = StrokeCap.Round))
                MascotFace.UNSURE, MascotFace.CHOMP -> {
                    drawOval(Color.White, topLeft = Offset(38f, 71f), size = Size(16f, 18f))
                    drawOval(Color.White, topLeft = Offset(66f, 71f), size = Size(16f, 18f))
                    if (face == MascotFace.UNSURE) {
                        drawCircle(MascotInk, radius = 4f, center = Offset(48f, 76f))
                        drawCircle(MascotInk, radius = 4f, center = Offset(76f, 76f))
                        drawLine(Color.White, Offset(54f, 100f), Offset(66f, 100f), strokeWidth = 4f, cap = StrokeCap.Round)
                    } else {
                        drawCircle(MascotInk, radius = 4f, center = Offset(47f, 75f))
                        drawCircle(MascotInk, radius = 4f, center = Offset(75f, 75f))
                        drawOval(MascotInk, topLeft = Offset(53f, 95f), size = Size(14f, 12f))
                    }
                }
            }
        }
    }
}

/** جیبی که آرام بالا و پایین می‌رود (حالت‌های خالی صفحه‌ها) */
@Composable
fun BobbingMascot(width: Dp, face: MascotFace = MascotFace.HAPPY, modifier: Modifier = Modifier) {
    val y = loopingValue(0f, -6f, 1200, label = "bob")
    Mascot(width, face, modifier.graphicsLayer { translationY = y * density })
}

// ── کاشی دسته ──

/**
 * کاشی گرد‌گوشه‌ی یک دسته: زمینه‌ی کم‌رنگ + آیکون خطی (یا ایموجی دسته‌ی شخصی).
 * @param size ۵۲ در فهرست تراکنش‌ها، ۵۶ در «کجا رفت؟»، ۶۰ در برگه‌ی انتخاب دسته
 */
@Composable
fun CategoryIconTile(tint: CategoryTint, size: Dp, radius: Dp, iconSize: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(tint.bg),
        contentAlignment = Alignment.Center,
    ) {
        val icon = tint.icon
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint.fg, modifier = Modifier.size(iconSize))
        } else {
            Text(tint.glyph.orEmpty(), fontSize = (iconSize.value * 0.82f).sp)
        }
    }
}

// ── پیام کوتاه بالای صفحه («رفت تو کافه») ──

/** یک پیام کوتاه؛ اگر [actionLabel] باشد، دکمه‌ای مثل «برگردون» کنارش می‌آید */
data class ToastMessage(val text: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null, val id: Long = System.nanoTime())

/** کپسول تیره‌ی بالای صفحه با تیک فیروزه‌ای؛ با فنر کوچکی پایین می‌آید */
@Composable
fun JibiToast(message: ToastMessage?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val t = JibitoTheme.colors
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.55f, stiffness = 500f)) { -it / 2 } +
            scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f), initialScale = 0.9f) + fadeIn(tween(150)),
        exit = slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(200)),
    ) {
        // آخرین پیام، حتی وقتی در حال محو شدن است
        val m = message ?: return@AnimatedVisibility
        Row(
            Modifier
                .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(24.dp))
                .background(t.toastBg)
                .height(48.dp)
                .padding(start = 20.dp, end = if (m.actionLabel != null) 6.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MascotCoin),
                contentAlignment = Alignment.Center,
            ) { Icon(DesignIcons.CheckBold, contentDescription = null, tint = Color(0xFF0B302F), modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(
                m.text,
                color = t.toastFg,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (m.actionLabel != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    m.actionLabel,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            m.onAction?.invoke()
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (t.dark) Color(0xFFB42318) else Color(0xFFFF9B7A),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
