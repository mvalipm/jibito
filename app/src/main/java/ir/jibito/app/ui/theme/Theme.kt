package ir.jibito.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// رنگ‌های اصلی جیبیتو: مرجانیِ گرم (برند و نشان) + فیروزه‌ای برای «حال خوب»
val Coral = Color(0xFFE4572E)
val CoralDark = Color(0xFFFF8A65)
val Teal = Color(0xFF17BEBB)
val Cream = Color(0xFFFBF6F2)
val Ink = Color(0xFF1C1B22)
val InkSoft = Color(0xFF1E1B22)

/**
 * رنگ‌هایی که در MaterialTheme جایی ندارند (توکن‌های طرح «جیبی»):
 * قرمز فقط برای هشدار (alert)، سبز فقط برای «پول اومد» (income)، فیروزه‌ای برای حال خوب (teal).
 * رنگ دسته‌ها جدا (CategoryStyle) است: روشنایی و شدت هم‌اندازه، فقط فام فرق می‌کند.
 */
@Immutable
data class JibitoColors(
    val dark: Boolean = false,
    val heroStart: Color = Coral,
    val heroEnd: Color = Color(0xFFF08A4B),
    val income: Color = Color(0xFF047857),
    val transfer: Color = Color(0xFF5A6B8C),
    val warning: Color = Color(0xFFA85407),
    /** متن کم‌رنگ (توضیح‌ها) */
    val muted: Color = Color(0xFF6B625E),
    /** کم‌رنگ‌تر (برچسب‌های غیرفعال) */
    val faint: Color = Color(0xFF8A817C),
    /** زمینه‌ی دکمه‌های کپسولی و ریل نوارها */
    val chip: Color = Color(0xFFF1E8E2),
    val border: Color = Color(0xFFEADFD8),
    /** برگه‌ی پایین صفحه */
    val sheet: Color = Color(0xFFFFFCFA),
    val handle: Color = Color(0xFFE2D6CF),
    val scrim: Color = Color(0x731C1418),
    val navBg: Color = Color(0xDBFFFCFA),
    val navInd: Color = Color(0xFFFFE1D4),
    val navOn: Color = Color(0xFFC2410C),
    val navOff: Color = Color(0xFF6B625E),
    val badge: Color = Color(0xFFC2410C),
    val badgeFg: Color = Color.White,
    val uncatBorder: Color = Coral,
    val uncatBg: Color = Color(0xFFFFF1EB),
    val uncatFg: Color = Color(0xFFC2410C),
    val alert: Color = Color(0xFFB42318),
    val teal: Color = Color(0xFF0E7C7B),
    val coral: Color = Coral,
    val amber: Color = Color(0xFFA85407),
    val transferBg: Color = Color(0xFFE6EAF2),
    val transferFg: Color = Color(0xFF5A6B8C),
    val failBg: Color = Color(0xFFEFEAE6),
    val failFg: Color = Color(0xFF6B625E),
    /** پیشنهاد دسته («سوپرمارکته؟ آره») */
    val sugBg: Color = Color(0xFFDDF3F2),
    val sugFg: Color = Color(0xFF0B5E5D),
    val trendOff: Color = Color(0xFFE8DCD4),
    val seenRing: Color = Color(0xFFE2D6CF),
    val toastBg: Color = Ink,
    val toastFg: Color = Color.White,
    val btnBg: Color = Color(0xFFC2410C),
    val btnFg: Color = Color.White,
    val smsBg: Color = Color.White,
    /** دکمه‌ی انتخاب‌شده (مثلاً فیلتر «همه») */
    val onBg: Color = Ink,
    val onFg: Color = Color.White,
    val tealTint: Color = Color(0xFFD5F1F0),
    val tealTintFg: Color = Color(0xFF0B5E5D),
    val amberTint: Color = Color(0xFFFCEBCF),
    val amberTintFg: Color = Color(0xFF8A4A08),
    /** رنگ سرصفحه‌ی «خلاصه» بسته به حال جیب: آروم، یواش‌تر، بیرون زد */
    val moodCalm: Color = Color(0xFF0E7C7B),
    val moodWarn: Color = Color(0xFFA85407),
    val moodOver: Color = Color(0xFFB42318),
)

internal val LightTokens = JibitoColors()

internal val DarkTokens = JibitoColors(
    dark = true,
    heroStart = CoralDark,
    income = Color(0xFF5FD3A0),
    transfer = Color(0xFF9FB0CF),
    warning = Color(0xFFF2B45A),
    muted = Color(0xFFB3A9A4),
    faint = Color(0xFF8F8590),
    chip = Color(0xFF2A262E),
    border = Color(0xFF322D36),
    sheet = InkSoft,
    handle = Color(0xFF3F3944),
    scrim = Color(0x99000000),
    navBg = Color(0xDB1E1B22),
    navInd = Color(0xFF4A2418),
    navOn = Color(0xFFFF9B7A),
    navOff = Color(0xFFB3A9A4),
    badge = CoralDark,
    badgeFg = Color(0xFF2A0A00),
    uncatBorder = CoralDark,
    uncatBg = Color(0xFF3A2119),
    uncatFg = Color(0xFFFF9B7A),
    alert = Color(0xFFF47C6B),
    teal = Color(0xFF3FD0CC),
    coral = CoralDark,
    amber = Color(0xFFF2B45A),
    transferBg = Color(0xFF232A38),
    transferFg = Color(0xFF9FB0CF),
    failBg = Color(0xFF2A262E),
    failFg = Color(0xFFB3A9A4),
    sugBg = Color(0xFF143634),
    sugFg = Color(0xFF7FE0DC),
    trendOff = Color(0xFF3A343F),
    seenRing = Color(0xFF3A343F),
    toastBg = Color(0xFFF3ECE8),
    toastFg = Ink,
    btnBg = CoralDark,
    btnFg = Color(0xFF2A0A00),
    smsBg = InkSoft,
    onBg = Color(0xFFF3ECE8),
    onFg = Ink,
    tealTint = Color(0xFF143634),
    tealTintFg = Color(0xFF7FE0DC),
    amberTint = Color(0xFF35260F),
    amberTintFg = Color(0xFFF2B45A),
    // در حالت تیره سرصفحه‌ی «خلاصه» یک درجه تیره‌تر و کم‌اشباع‌تر است تا در شب مثل چراغ ندرخشد
    moodCalm = Color(0xFF0A4D4C),
    moodWarn = Color(0xFF65400F),
    moodOver = Color(0xFF741C14),
)

val LocalJibitoColors = staticCompositionLocalOf { LightTokens }

/** دسترسی کوتاه: JibitoTheme.colors.income */
object JibitoTheme {
    val colors: JibitoColors
        @Composable @ReadOnlyComposable get() = LocalJibitoColors.current
}

/** رنگ‌های نمونه‌ی هر پوسته (برای انتخابگر در تنظیمات) */
fun previewColors(style: AppThemeStyle): List<Color> = when (style) {
    AppThemeStyle.DEFAULT -> listOf(Coral, Color(0xFF0E7C7B), Color(0xFFFF9B7A), Cream)
    AppThemeStyle.WARM -> listOf(Color(0xFFC78997), Color(0xFFF5B297), Color(0xFFF5D6A2), Color(0xFF8A99B1))
    AppThemeStyle.COOL -> listOf(Color(0xFF182346), Color(0xFF3D5387), Color(0xFF7C83AD), Color(0xFFBFA9BA))
    AppThemeStyle.MATCHA -> listOf(Color(0xFF5B7A4C), Color(0xFF9DB58A), Color(0xFFC9B48A), Color(0xFFDDE8D0))
    AppThemeStyle.LAVENDER -> listOf(Color(0xFF6E4FC0), Color(0xFFA88BEB), Color(0xFFF2B5D4), Color(0xFFE9E1FA))
    AppThemeStyle.CHERRY -> listOf(Color(0xFFA3123A), Color(0xFFD9425F), Color(0xFF2E1A1E), Color(0xFFFBD9E0))
    AppThemeStyle.DUSK -> listOf(Color(0xFF3F3470), Color(0xFFF29E6D), Color(0xFF6F5DA8), Color(0xFFFBE3D3))
}

@Composable
fun JibitoTheme(
    style: AppThemeStyle = AppThemeStyle.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val (scheme, extras) = schemeFor(style, darkTheme)
    CompositionLocalProvider(LocalJibitoColors provides extras) {
        MaterialTheme(colorScheme = scheme, typography = JibitoTypography, content = content)
    }
}
