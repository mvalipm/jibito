package ir.jibito.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// رنگ‌های اصلی جیبیتو: مرجانیِ گرم + فیروزه‌ای برای تأکید
val Coral = Color(0xFFE4572E)
val CoralDark = Color(0xFFFF8A65)
val Teal = Color(0xFF17BEBB)
val Cream = Color(0xFFFFF8F3)
val Ink = Color(0xFF1C1B22)
val InkSoft = Color(0xFF2A2830)

private val LightColors = lightColorScheme(
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

private val DarkColors = darkColorScheme(
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

@Composable
fun JibitoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
