package ir.jibito.app.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.Vazirmatn
import ir.jibito.app.util.Jalali

/** یک دکمه‌ی نوار پایین */
data class NavItem(
    val icon: NavIcons.NavIcon,
    val label: String,
    /** عدد کوچک روی آیکون (مثلاً پیامک‌های منتظر بررسی)؛ ۰ یعنی نشان نده */
    val badge: Int = 0,
)

/** ارتفاع خود نوار (بدون فاصله از لبه‌ها) */
val FloatingNavBarHeight = 70.dp
/** فاصله‌ی نوار از پایین صفحه (بالای نوار سیستم) */
val FloatingNavBarBottomMargin = 16.dp

/**
 * نوار پایینِ شناور و کپسولی (طرح «جیبی»):
 * - از لبه‌ها ۱۶ فاصله دارد و دو سرش کاملاً گرد است؛ پس‌زمینه شیشه‌ای مات (اندروید ۱۲+ تار، قدیمی‌ترها نیمه‌شفاف).
 * - پشت تب فعال یک کپسول هلویی است که با فنری نرم بین تب‌ها سُر می‌خورد.
 */
@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val t = JibitoTheme.colors
    val glass = t.navBg
    val shape = RoundedCornerShape(35.dp)

    BoxWithConstraints(
        modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = FloatingNavBarBottomMargin)
            .fillMaxWidth()
            .height(FloatingNavBarHeight)
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = if (t.dark) Color.Black else Color(0xFF461E14),
                spotColor = if (t.dark) Color.Black else Color(0xFF461E14),
            )
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = glass.copy(alpha = 1f),
                    tints = listOf(HazeTint(glass)),
                    blurRadius = 18.dp,
                    noiseFactor = 0f,
                    // اندروید قدیمی (بدون تار شدن): سطح تقریباً مات
                    fallbackTint = HazeTint(glass.copy(alpha = 0.96f)),
                ),
            )
            .border(1.dp, if (t.dark) Color.White.copy(alpha = 0.07f) else Color(0x0F1C1B22), shape)
            .padding(6.dp),
    ) {
        val itemWidth = maxWidth / items.size.coerceAtLeast(1)
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
            label = "navIndicator",
        )
        // کپسول تب فعال (در راست‌به‌چپ offset خودش از راست حساب می‌شود)
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(itemWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(29.dp))
                .background(t.navInd)
        )
        Row(Modifier.fillMaxSize()) {
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
}

@Composable
private fun NavButton(item: NavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val t = JibitoTheme.colors
    val content by animateColorAsState(if (selected) t.navOn else t.navOff, animationSpec = tween(300), label = "navContent")
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(29.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(
                if (selected) item.icon.selected else item.icon.normal,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(24.dp),
            )
            if (item.badge > 0) {
                // عدد کوچک روی گوشه‌ی آیکون؛ متن بدون فاصله‌ی اضافه‌ی فونت، تا دقیقاً وسط بنشیند
                val label = Jalali.toPersianDigits(if (item.badge > 9) "9+" else item.badge.toString())
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-10).dp, y = (-5).dp)
                        .height(18.dp)
                        .defaultMinSize(minWidth = 18.dp)
                        .clip(CircleShape)
                        .background(t.badge, CircleShape)
                        .border(2.dp, t.sheet, CircleShape)
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = t.badgeFg,
                        fontWeight = FontWeight.Black,
                        style = TextStyle(
                            fontFamily = Vazirmatn,
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            item.label,
            color = content,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
