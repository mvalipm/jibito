package ir.jibito.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

private val LightTokens = JibitoColors()

private val DarkTokens = JibitoColors(
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
    moodCalm = Color(0xFF0B5F5E),
    moodWarn = Color(0xFF7E4306),
    moodOver = Color(0xFF8F1F14),
)

val LocalJibitoColors = staticCompositionLocalOf { LightTokens }

/** دسترسی کوتاه: JibitoTheme.colors.income */
object JibitoTheme {
    val colors: JibitoColors
        @Composable @ReadOnlyComposable get() = LocalJibitoColors.current
}

// ── مرجانی (پیش‌فرض)، همان رنگ‌های طرح ──

private val DefaultLight = lightColorScheme(
    primary = Color(0xFFC2410C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1D4),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF0E7C7B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F1F0),
    onSecondaryContainer = Color(0xFF0B5E5D),
    background = Cream,
    onBackground = Ink,
    surface = Color(0xFFFFFCFA),
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1E8E2),
    onSurfaceVariant = Color(0xFF6B625E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFCFA),
    surfaceContainer = Color(0xFFFFFCFA),
    surfaceContainerHigh = Color(0xFFFFFCFA),
    surfaceContainerHighest = Color(0xFFF1E8E2),
    outline = Color(0xFF8A817C),
    outlineVariant = Color(0xFFEADFD8),
    error = Color(0xFFB42318),
    onError = Color.White,
)

private val DefaultDark = darkColorScheme(
    primary = CoralDark,
    onPrimary = Color(0xFF2A0A00),
    primaryContainer = Color(0xFF4A2418),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFF3FD0CC),
    onSecondary = Color(0xFF0B302F),
    secondaryContainer = Color(0xFF143634),
    onSecondaryContainer = Color(0xFF7FE0DC),
    background = Color(0xFF141217),
    onBackground = Color(0xFFF3ECE8),
    surface = InkSoft,
    onSurface = Color(0xFFF3ECE8),
    surfaceVariant = Color(0xFF2A262E),
    onSurfaceVariant = Color(0xFFB3A9A4),
    surfaceContainerLowest = Color(0xFF141217),
    surfaceContainerLow = InkSoft,
    surfaceContainer = InkSoft,
    surfaceContainerHigh = InkSoft,
    surfaceContainerHighest = Color(0xFF2A262E),
    outline = Color(0xFF8F8590),
    outlineVariant = Color(0xFF322D36),
    error = Color(0xFFF47C6B),
    onError = Color(0xFF2A0A00),
)

// ── گرم: #C78997 #F5B297 #F5D6A2 #F5E4C4 #8A99B1 ──
// صورتی‌گلی با متن سفید خوانا نیست (۲٫۸)؛ برای دکمه‌ها یک درجه تیره‌تر: #A85F70 (۴٫۶)

private val WarmLight = lightColorScheme(
    primary = Color(0xFFA85F70),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3D9DE),
    onPrimaryContainer = Color(0xFF3E1720),
    secondary = Color(0xFF8A99B1),
    onSecondary = Color(0xFF0E0D15),
    secondaryContainer = Color(0xFFF5D6A2),
    onSecondaryContainer = Color(0xFF3B2A0C),
    tertiary = Color(0xFFF5B297),
    background = Color(0xFFFAF1E2),
    onBackground = Color(0xFF0E0D15),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF0E0D15),
    surfaceVariant = Color(0xFFF5E4C4),
    onSurfaceVariant = Color(0xFF5A4E4A),
)

private val WarmDark = darkColorScheme(
    primary = Color(0xFFE8A9B5),
    onPrimary = Color(0xFF3E1720),
    primaryContainer = Color(0xFF6E3A47),
    onPrimaryContainer = Color(0xFFF3D9DE),
    secondary = Color(0xFF8A99B1),
    onSecondary = Color(0xFF0E0D15),
    secondaryContainer = Color(0xFF4A3B24),
    onSecondaryContainer = Color(0xFFF5D6A2),
    tertiary = Color(0xFFF5B297),
    background = Color(0xFF17131A),
    onBackground = Color(0xFFF3E6E0),
    surface = Color(0xFF241D24),
    onSurface = Color(0xFFF3E6E0),
    surfaceVariant = Color(0xFF33292F),
    onSurfaceVariant = Color(0xFFD2C2BE),
)

// ── سرد: #0E0D15 #182346 #3D5387 #7C83AD #BFA9BA ──

private val CoolLight = lightColorScheme(
    primary = Color(0xFF3D5387),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE2F2),
    onPrimaryContainer = Color(0xFF182346),
    secondary = Color(0xFFBFA9BA),
    onSecondary = Color(0xFF0E0D15),
    secondaryContainer = Color(0xFFEFE4EC),
    onSecondaryContainer = Color(0xFF3A2A36),
    tertiary = Color(0xFF7C83AD),
    background = Color(0xFFF7F1E6),
    onBackground = Color(0xFF0E0D15),
    surface = Color.White,
    onSurface = Color(0xFF0E0D15),
    surfaceVariant = Color(0xFFEFE9F0),
    onSurfaceVariant = Color(0xFF4E5068),
)

private val CoolDark = darkColorScheme(
    primary = Color(0xFF9FAEE0),
    onPrimary = Color(0xFF0E0D15),
    primaryContainer = Color(0xFF3D5387),
    onPrimaryContainer = Color(0xFFE3E8F8),
    secondary = Color(0xFFBFA9BA),
    onSecondary = Color(0xFF0E0D15),
    secondaryContainer = Color(0xFF4A3B4A),
    onSecondaryContainer = Color(0xFFEFE4EC),
    tertiary = Color(0xFF7C83AD),
    background = Color(0xFF0E0D15),
    onBackground = Color(0xFFE9E6F2),
    surface = Color(0xFF182346),
    onSurface = Color(0xFFE9E6F2),
    surfaceVariant = Color(0xFF24305A),
    onSurfaceVariant = Color(0xFFC3C4DA),
)

/**
 * رنگ‌های هر پوسته؛ کنتراست متن‌ها بررسی شده (متن اصلی ≥ ۱۲، دکمه‌ها ≥ ۴٫۵).
 * پوسته‌های گرم و سرد همان توکن‌های طرح را دارند، فقط رنگ برند (دکمه، نوار پایین، سطح‌ها) از خودشان است.
 */
private fun schemeFor(style: AppThemeStyle, dark: Boolean): Pair<ColorScheme, JibitoColors> {
    val base = if (dark) DarkTokens else LightTokens
    return when (style) {
        AppThemeStyle.DEFAULT -> (if (dark) DefaultDark else DefaultLight) to base
        AppThemeStyle.WARM -> {
            val s = if (dark) WarmDark else WarmLight
            s to base.branded(s, if (dark) Color(0xFF6E3A47) else Color(0xFFA85F70), if (dark) Color(0xFFA85F70) else Color(0xFFB06A7A))
        }
        AppThemeStyle.COOL -> {
            val s = if (dark) CoolDark else CoolLight
            s to base.branded(s, if (dark) Color(0xFF24305A) else Color(0xFF182346), Color(0xFF3D5387))
        }
    }
}

/** توکن‌های طرح، با رنگ برند و سطح‌های یک پوسته‌ی دیگر */
private fun JibitoColors.branded(s: ColorScheme, heroStart: Color, heroEnd: Color) = copy(
    heroStart = heroStart,
    heroEnd = heroEnd,
    muted = s.onSurfaceVariant,
    chip = s.surfaceVariant,
    border = s.surfaceVariant,
    sheet = s.surface,
    handle = s.outline.copy(alpha = 0.4f),
    navBg = s.surface.copy(alpha = 0.86f),
    navInd = s.primaryContainer,
    navOn = s.primary,
    navOff = s.onSurfaceVariant,
    badge = s.primary,
    badgeFg = s.onPrimary,
    uncatBorder = s.primary,
    uncatBg = s.primaryContainer,
    uncatFg = if (dark) s.onPrimaryContainer else s.primary,
    coral = s.primary,
    trendOff = s.surfaceVariant,
    seenRing = s.surfaceVariant,
    btnBg = s.primary,
    btnFg = s.onPrimary,
    smsBg = s.surface,
)

/** رنگ‌های نمونه‌ی هر پوسته (برای انتخابگر در تنظیمات) */
fun previewColors(style: AppThemeStyle): List<Color> = when (style) {
    AppThemeStyle.DEFAULT -> listOf(Coral, Color(0xFF0E7C7B), Color(0xFFFF9B7A), Cream)
    AppThemeStyle.WARM -> listOf(Color(0xFFC78997), Color(0xFFF5B297), Color(0xFFF5D6A2), Color(0xFF8A99B1))
    AppThemeStyle.COOL -> listOf(Color(0xFF182346), Color(0xFF3D5387), Color(0xFF7C83AD), Color(0xFFBFA9BA))
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
