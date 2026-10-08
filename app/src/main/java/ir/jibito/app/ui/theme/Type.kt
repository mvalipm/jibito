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

/**
 * اندازه‌های متن صفحه‌ها، با اسم به‌جای عدد (فعلاً صفحه‌ی «خلاصه»؛ بقیه‌ی صفحه‌ها کم‌کم).
 * فقط اندازه، ارتفاع خط و فونت را می‌گویند؛ وزن و رنگ را هر متن خودش می‌دهد.
 * ارتفاع خط حدود ۱٫۵ برابر اندازه است؛ بدون آن، متن ارتفاع خط bodyLarge (۲۸sp) را می‌گرفت و متن ریز دورش جای خالی داشت.
 */
object JibitoText {
    private fun style(size: Int, line: Int) = TextStyle(fontFamily = Vazirmatn, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.em)

    /** عنوان صفحه (۲۲) */
    val screenTitle = style(22, 32)

    /** عدد برجسته کنار متن، مثل سهم دسته (۲۴) */
    val figure = style(24, 32)

    /** عنوان بخش (۱۸) */
    val sectionTitle = style(18, 28)

    /** متن اصلی و جمله‌ها (۱۵) */
    val lead = style(15, 24)

    /** دکمه‌ی متنی و پیوند (۱۴) */
    val action = style(14, 22)

    /** متن بدنه‌ی کوچک (۱۳) */
    val body = style(13, 20)

    /** توضیح زیر عدد (۱۲) */
    val small = style(12, 18)

    /** برچسب ریز (۱۱) */
    val tiny = style(11, 16)

    /** ریزترین برچسب، مثل «تا امروز» زیر ستون (۱۰) */
    val micro = style(10, 14)
}
