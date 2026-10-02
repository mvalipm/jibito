package ir.jibito.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ir.jibito.app.R

/**
 * وزیرمتن (نسخه‌ی UI، مجوز OFL — licenses/Vazirmatn-OFL.txt): فارسی روی همه‌ی گوشی‌ها یک‌شکل دیده شود.
 * فقط چهار وزن داخل اپ است؛ وزن‌های دیگر (مثلاً SemiBold) به نزدیک‌ترین وزن موجود می‌رسند.
 */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_black, FontWeight.Black),
)

/**
 * همان مقیاس اندازه‌های Material 3، با وزیرمتن.
 * فاصله‌ی حروف صفر است (حروف فارسی به هم چسبیده‌اند و فاصله‌ی اضافه شکلشان را خراب می‌کند)
 * و ارتفاع خط متن‌های بدنه کمی بیشتر است تا نقطه‌ها و دندانه‌های فارسی نفس بکشند.
 */
val JibitoTypography: Typography = Typography().run {
    fun TextStyle.persian(extraLineHeight: Float = 0f) = copy(
        fontFamily = Vazirmatn,
        letterSpacing = 0.em,
        lineHeight = if (lineHeight.isSp) (lineHeight.value + extraLineHeight).sp else lineHeight,
    )
    Typography(
        displayLarge = displayLarge.persian(),
        displayMedium = displayMedium.persian(),
        displaySmall = displaySmall.persian(),
        headlineLarge = headlineLarge.persian(),
        headlineMedium = headlineMedium.persian(),
        headlineSmall = headlineSmall.persian(),
        titleLarge = titleLarge.persian(),
        titleMedium = titleMedium.persian(),
        titleSmall = titleSmall.persian(),
        bodyLarge = bodyLarge.persian(extraLineHeight = 4f),
        bodyMedium = bodyMedium.persian(extraLineHeight = 4f),
        bodySmall = bodySmall.persian(extraLineHeight = 2f),
        labelLarge = labelLarge.persian(),
        labelMedium = labelMedium.persian(),
        labelSmall = labelSmall.persian(),
    )
}
