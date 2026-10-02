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

// رنگ‌های اصلی جیبیتو: مرجانیِ گرم + فیروزه‌ای برای تأکید
val Coral = Color(0xFFE4572E)
val CoralDark = Color(0xFFFF8A65)
val Teal = Color(0xFF17BEBB)
val Cream = Color(0xFFFFF8F3)
val Ink = Color(0xFF1C1B22)
val InkSoft = Color(0xFF2A2830)

/**
 * رنگ‌هایی که در MaterialTheme جایی ندارند:
 * - گرادیان کارت بالای «خلاصه» (متن رویش سفید است)
 * - رنگ‌های معنایی: پول آمده (income)، انتقال به خودم (transfer)، هشدار نزدیک بودجه (warning).
 *   در هر سه پوسته یکی‌اند و فقط برای حالت تیره روشن‌تر می‌شوند تا روی زمینه‌ی تیره خوانا بمانند.
 */
@Immutable
data class JibitoColors(
    val heroStart: Color,
    val heroEnd: Color,
    val income: Color = IncomeLight,
    val transfer: Color = TransferLight,
    val warning: Color = WarningLight,
)

private val IncomeLight = Color(0xFF1E9E6A)
private val IncomeDark = Color(0xFF4CC38A)
private val TransferLight = Color(0xFF3A6FD8)
private val TransferDark = Color(0xFF7FA4F0)
private val WarningLight = Color(0xFFE0951F)
private val WarningDark = Color(0xFFF2B45A)

val LocalJibitoColors = staticCompositionLocalOf { JibitoColors(Coral, Color(0xFFF08A4B)) }

/** دسترسی کوتاه: JibitoTheme.colors.income */
object JibitoTheme {
    val colors: JibitoColors
        @Composable @ReadOnlyComposable get() = LocalJibitoColors.current
}

// ── مرجانی (پیش‌فرض) ──

private val DefaultLight = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF5F4),
    onSecondaryContainer = Color(0xFF00403F),
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF5EDE8),
    onSurfaceVariant = Color(0xFF5B5450),
)

private val DefaultDark = darkColorScheme(
    primary = CoralDark,
    onPrimary = Color(0xFF3A0B00),
    primaryContainer = Color(0xFF6B2410),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Teal,
    onSecondary = Color(0xFF00302F),
    secondaryContainer = Color(0xFF005653),
    onSecondaryContainer = Color(0xFFCFF5F4),
    background = Ink,
    onBackground = Color(0xFFEDE6E2),
    surface = InkSoft,
    onSurface = Color(0xFFEDE6E2),
    surfaceVariant = Color(0xFF38343C),
    onSurfaceVariant = Color(0xFFCFC6C1),
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

/** رنگ‌های هر پوسته؛ کنتراست متن‌ها بررسی شده (متن اصلی ≥ ۱۲، دکمه‌ها ≥ ۴٫۵، گرادیان ≥ ۴) */
private fun schemeFor(style: AppThemeStyle, dark: Boolean): Pair<ColorScheme, JibitoColors> = when (style) {
    AppThemeStyle.DEFAULT ->
        if (dark) DefaultDark to JibitoColors(CoralDark, Color(0xFFF08A4B))
        else DefaultLight to JibitoColors(Coral, Color(0xFFF08A4B))
    AppThemeStyle.WARM ->
        if (dark) WarmDark to JibitoColors(Color(0xFF6E3A47), Color(0xFFA85F70))
        else WarmLight to JibitoColors(Color(0xFFA85F70), Color(0xFFB06A7A))
    AppThemeStyle.COOL ->
        if (dark) CoolDark to JibitoColors(Color(0xFF24305A), Color(0xFF3D5387))
        else CoolLight to JibitoColors(Color(0xFF182346), Color(0xFF3D5387))
}

/** رنگ‌های نمونه‌ی هر پوسته (برای انتخابگر در تنظیمات) */
fun previewColors(style: AppThemeStyle): List<Color> = when (style) {
    AppThemeStyle.DEFAULT -> listOf(Coral, Color(0xFFF08A4B), Teal, Cream)
    AppThemeStyle.WARM -> listOf(Color(0xFFC78997), Color(0xFFF5B297), Color(0xFFF5D6A2), Color(0xFF8A99B1))
    AppThemeStyle.COOL -> listOf(Color(0xFF182346), Color(0xFF3D5387), Color(0xFF7C83AD), Color(0xFFBFA9BA))
}

@Composable
fun JibitoTheme(
    style: AppThemeStyle = AppThemeStyle.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val (scheme, hero) = schemeFor(style, darkTheme)
    val extras = if (darkTheme) hero.copy(income = IncomeDark, transfer = TransferDark, warning = WarningDark) else hero
    CompositionLocalProvider(LocalJibitoColors provides extras) {
        MaterialTheme(colorScheme = scheme, typography = JibitoTypography, content = content)
    }
}
