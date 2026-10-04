package ir.jibito.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * آیکون‌های خطی طرح «جیبی» (همان مسیرهای SVG ماکاپ): صفحه‌ی ۲۴×۲۴، خط ۱٫۸، سر و گوشه‌ی گرد.
 * رنگ را Icon(tint) تعیین می‌کند؛ ضخامت خط را هم می‌شود با [strokeWidth] عوض کرد (مثلاً تب فعال: ۲٫۲).
 */
object DesignIcons {

    fun svg(name: String, d: String, strokeWidth: Float = 1.8f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    // ── نوار پایین ──
    const val NAV_SUMMARY = "M4 20V11M10 20V5M16 20v-6M3 20h18"
    const val NAV_TRANSACTIONS = "M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01"
    const val NAV_TODO = "M4 8a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4v8a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4zM8.5 12l2.5 2.5 4.5-5"
    const val NAV_SETTINGS = "M4 7h9M17 7h3M4 17h3M11 17h9M13 7a2 2 0 1 0 4 0a2 2 0 1 0-4 0M7 17a2 2 0 1 0 4 0a2 2 0 1 0-4 0"

    // ── دسته‌ها ──
    const val CAFE = "M4 9h12v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5V9zM16 10h1.5a2.5 2.5 0 0 1 0 5H16M8 3v3M12 3v3"
    const val TAXI = "M4 17v-4l2-6h12l2 6v4H4zM6 17v2.5M18 17v2.5M4 13h16M7.5 15h1M15.5 15h1"
    const val GROCERY = "M3 4h2l2.4 10.2a1 1 0 0 0 1 .8h8.8a1 1 0 0 0 1-.8L20 7H6.2M7.5 19a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0M15.5 19a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0"
    const val FOOD = "M7 3v7a2 2 0 0 0 2 2v9M5 3v5M9 3v5M5 8h4M17 21V3c-2 1.5-3 4-3 8h3"
    const val SHOP = "M5 8h14l-1 12H6zM9 8V6a3 3 0 0 1 6 0v2"
    const val HOME = "M4 11l8-7 8 7v9H4zM10 20v-5h4v5"
    const val HEALTH = "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"
    const val TRANSFER = "M4 8h15l-3-3M20 16H5l3 3"
    const val FAILED = "M9 14L4 9l5-5M4 9h10a6 6 0 0 1 0 12h-3"
    const val SALARY = "M4 8h16v11H4zM9 8V6h6v2M4 13h16"
    const val GIFT = "M4 11h16v9H4zM3 8h18v3H3zM12 8v12M12 8c-2-4-6-3-4 0M12 8c2-4 6-3 4 0"
    const val BOOK = "M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3zM5 17a3 3 0 0 1 3-3h11M9 8h6"
    const val TICKET = "M4 7h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4zM10 7v10"
    const val CARD = "M3 6h18v12H3zM3 10h18M7 15h4"
    const val PAW = "M7 16.5c0-2.5 2.2-4.5 5-4.5s5 2 5 4.5c0 1.9-1.6 3-5 3s-5-1.1-5-3zM3.9 11a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0M7.4 6.5a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0M13.4 6.5a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0M16.9 11a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0"
    const val SPARKLE = "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM18 16l.8 2.2L21 19l-2.2.8L18 22l-.8-2.2L15 19l2.2-.8z"
    const val DUMBBELL = "M6 8v8M3.5 10v4M18 8v8M20.5 10v4M6 12h12"
    const val PHONE = "M7 3h10v18H7zM11 18h2"
    const val COINS = "M4 8a8 3 0 1 0 16 0a8 3 0 1 0-16 0M4 8v4c0 1.7 3.6 3 8 3s8-1.3 8-3V8M4 12v4c0 1.7 3.6 3 8 3s8-1.3 8-3v-4"
    const val TAG = "M4 4h7l9 9-7 7-9-9zM8.5 8.5h.01"
    const val BANK = "M3 10l9-6 9 6M5 10v8M10 10v8M14 10v8M19 10v8M3 20h18"
    const val DOTS = "M5 12h.01M12 12h.01M19 12h.01"
    const val STAR = "M12 4l2.4 5 5.6.6-4.2 3.8 1.2 5.6-5-2.9-5 2.9 1.2-5.6L4 9.6 9.6 9z"
    const val WRENCH = "M14.5 4a4.5 4.5 0 0 0-4.2 6.1L4 16.4V20h3.6l6.3-6.3A4.5 4.5 0 0 0 20 9.5l-3 1-2.5-2.5 1-3z"
    const val LEAF = "M5 19c0-8 5-13 14-14 0 9-5 14-13 14zM5 19l7-7"
    const val TARGET = "M4 12a8 8 0 1 0 16 0a8 8 0 1 0-16 0M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0M12 12h.01"
    const val CHILD = "M9.5 5.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0M6 12l3-2.5h6l3 2.5M9 9.5V15l-1 5M15 9.5V15l1 5M9 15h6"
    const val CIGARETTE = "M3 14h18v3H3zM16 14v3M18 11c0-2-2-2-2-4M21 11c0-2-2-2-2-4"
    const val SCISSORS = "M4 7a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0M4 17a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0M8.6 8.4L20 18M8.6 15.6L20 6"
    const val MOSQUE = "M5 20v-7a7 5 0 0 1 14 0v7M12 4v4M3 20h18M10 20v-3a2 2 0 0 1 4 0v3"

    // ── حال جیب و چیزهای دیگر ──
    const val BELL = "M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 20a2 2 0 0 0 4 0"
    const val CALM = "M4 12a8 8 0 1 0 16 0a8 8 0 1 0-16 0M9 14q3 3 6 0M9 10h.01M15 10h.01"
    const val WARN = "M12 4l9 16H3zM12 10v4M12 17h.01"
    const val OVER = "M4 12a8 8 0 1 0 16 0a8 8 0 1 0-16 0M12 8v5M12 16h.01"
    const val CHEVRON_DOWN = "M6 9l6 6 6-6"
    const val MOON = "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"
    const val SUN = "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4"
    const val EYE = "M3 12s4-6 9-6 9 6 9 6-4 6-9 6-9-6-9-6zM9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0"
    const val EYE_OFF = "M3 3l18 18M10.6 6.1A9 9 0 0 1 12 6c5 0 9 6 9 6a15 15 0 0 1-2.4 3M6.4 7.6C4.3 9.2 3 12 3 12s4 6 9 6a8 8 0 0 0 4-1"
    const val SEARCH = "M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0M20 20l-4-4"
    const val CHECK = "M5 12l5 5 9-10"
    const val CALENDAR = "M4 6h16v14H4zM4 10h16M8 3v4M16 3v4"
    const val REPEAT = "M20 11a8 8 0 0 0-14-5L4 8M4 4v4h4M4 13a8 8 0 0 0 14 5l2-2M20 20v-4h-4"
    const val FINGERPRINT = "M12 11v4a6 6 0 0 1-1 3.5M8 8.5A5 5 0 0 1 17 11v2a10 10 0 0 1-.6 3.5M5 15a12 12 0 0 0 .5-3.5 6.5 6.5 0 0 1 11-4.8M8.5 18.5A9 9 0 0 0 9 15v-4a3 3 0 0 1 6 0"
    const val SHIELD = "M12 21s7-3.5 7-9V6l-7-3-7 3v6c0 5.5 7 9 7 9zM9 12l2 2 4-4"
    const val MESSAGE = "M4 6h16v11H9l-5 3zM8 10h8M8 13h5"

    val Bell by lazy { svg("bell", BELL) }
    val Calm by lazy { svg("calm", CALM) }
    val Warn by lazy { svg("warn", WARN) }
    val Over by lazy { svg("over", OVER) }
    val ChevronDown by lazy { svg("chevron_down", CHEVRON_DOWN) }
    val Moon by lazy { svg("moon", MOON) }
    val Sun by lazy { svg("sun", SUN) }
    val Eye by lazy { svg("eye", EYE) }
    val EyeOff by lazy { svg("eye_off", EYE_OFF) }
    val Search by lazy { svg("search", SEARCH) }
    val Check by lazy { svg("check", CHECK) }
    val CheckBold by lazy { svg("check_bold", CHECK, strokeWidth = 3f) }
    val Calendar by lazy { svg("calendar", CALENDAR) }
    val Bank by lazy { svg("bank", BANK) }
    val Repeat by lazy { svg("repeat", REPEAT) }
    val Transfer by lazy { svg("transfer", TRANSFER) }
    val Failed by lazy { svg("failed", FAILED) }
    val Message by lazy { svg("message", MESSAGE) }
    val Fingerprint by lazy { svg("fingerprint", FINGERPRINT) }
    val Shield by lazy { svg("shield", SHIELD) }
    val Grocery by lazy { svg("grocery", GROCERY) }
    val Dots by lazy { svg("dots", DOTS, strokeWidth = 3f) }
}
