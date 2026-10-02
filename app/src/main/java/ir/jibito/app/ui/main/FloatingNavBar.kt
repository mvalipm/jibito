package ir.jibito.app.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import ir.jibito.app.ui.theme.Vazirmatn
import ir.jibito.app.util.Jalali

/** یک دکمه‌ی نوار پایین */
data class NavItem(
    val icon: ImageVector,
    val label: String,
    /** عدد کوچک روی آیکون (مثلاً پیامک‌های منتظر بررسی)؛ ۰ یعنی نشان نده */
    val badge: Int = 0,
)

/** ارتفاع خود نوار (بدون فاصله از لبه‌ها) */
val FloatingNavBarHeight = 68.dp
/** فاصله‌ی نوار از پایین صفحه (بالای نوار سیستم) */
val FloatingNavBarBottomMargin = 12.dp

/**
 * نوار پایینِ شناور و کپسولی:
 * - از لبه‌ها فاصله دارد و دو سرش کاملاً گرد است.
 * - پس‌زمینه‌اش شیشه‌ای مات است: محتوای صفحه زیرش تار دیده می‌شود (اندروید ۱۲+؛ در قدیمی‌ترها نیمه‌شفاف).
 * - دکمه‌ی فعال یک کپسول کم‌رنگ به رنگ اصلی اپ پشتش دارد.
 */
@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val glass = colors.surface
    val edge = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f)

    Row(
        modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = FloatingNavBarBottomMargin)
            .fillMaxWidth()
            .height(FloatingNavBarHeight)
            .shadow(
                elevation = 18.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.20f),
                spotColor = Color.Black.copy(alpha = 0.20f),
            )
            .clip(CircleShape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = glass,
                    tints = listOf(HazeTint(glass.copy(alpha = if (dark) 0.70f else 0.62f))),
                    blurRadius = 24.dp,
                    noiseFactor = 0f,
                    // اندروید قدیمی (بدون تار شدن): سطح تقریباً مات
                    fallbackTint = HazeTint(glass.copy(alpha = 0.94f)),
                ),
            )
            .border(1.dp, edge, CircleShape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            NavButton(
                item = item,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NavButton(item: NavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val pill by animateColorAsState(
        if (selected) colors.primary.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = tween(220),
        label = "pill",
    )
    val content by animateColorAsState(
        if (selected) colors.primary else colors.onSurface.copy(alpha = 0.72f),
        animationSpec = tween(220),
        label = "content",
    )
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(percent = 50))
            .background(pill)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(item.icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
            if (item.badge > 0) {
                // عدد کوچک روی گوشه‌ی آیکون؛ متن بدون فاصله‌ی اضافه‌ی فونت، تا دقیقاً وسط دایره بنشیند
                val label = Jalali.toPersianDigits(if (item.badge > 9) "9+" else item.badge.toString())
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-3).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                        .border(1.5.dp, colors.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = colors.onPrimary,
                        fontWeight = FontWeight.Black,
                        style = TextStyle(
                            fontFamily = Vazirmatn,
                            fontSize = 9.sp,
                            lineHeight = 9.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            item.label,
            color = content,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
