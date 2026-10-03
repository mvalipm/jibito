package ir.jibito.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import ir.jibito.app.data.category.CategoryPalette

/**
 * ظاهر یک دسته در طرح «جیبی»: کاشی رنگی کم‌رنگ (bg) + آیکون خطی پررنگ (fg).
 * رنگ‌ها روشنایی و شدت هم‌اندازه دارند و فقط فامشان فرق می‌کند؛ هیچ‌کدام قرمز (هشدار) یا سبز (درآمد) نیست،
 * جز «حقوق» که خودش درآمد است.
 * @param icon آیکون خطی؛ null یعنی دسته‌ی شخصی با ایموجی‌ای که آیکون خطی ندارد ([glyph] نشان داده می‌شود)
 */
data class CategoryTint(val bg: Color, val fg: Color, val icon: ImageVector?, val glyph: String?)

object CategoryStyle {

    /** جفت رنگ‌های طرح: [روشن bg, روشن fg, تیره bg, تیره fg] */
    private val TINTS: Map<String, List<Long>> = mapOf(
        "grocery" to listOf(0xFFEEF0D2, 0xFF5F6A16, 0xFF2C2F17, 0xFFC5D16A),
        "food" to listOf(0xFFFBE3EF, 0xFFA0236B, 0xFF3A1C2D, 0xFFF08BC2),
        "cafe" to listOf(0xFFF3E6D8, 0xFF8B5A2B, 0xFF33261A, 0xFFD9A878),
        "taxi" to listOf(0xFFE1E9FA, 0xFF2F5DB8, 0xFF1C2840, 0xFF8FB0F2),
        "shop" to listOf(0xFFEDE5FB, 0xFF6D3FC0, 0xFF2A2142, 0xFFB79CF2),
        "home" to listOf(0xFFE3EAF0, 0xFF4B6478, 0xFF232C34, 0xFFA9BCCB),
        "health" to listOf(0xFFDDF0F8, 0xFF1F6F93, 0xFF17303A, 0xFF7CC4E4),
        "gold" to listOf(0xFFF6ECCC, 0xFF7D6608, 0xFF332B12, 0xFFDCC06A),
        "other" to listOf(0xFFEFEAE6, 0xFF6B625E, 0xFF2A262E, 0xFFB3A9A4),
        "salary" to listOf(0xFFD7F5E8, 0xFF047857, 0xFF15342A, 0xFF5FD3A0),
    )

    /** رنگ ذخیره‌شده‌ی هر دسته (پالت قدیمی نمودار) ← جفت رنگ طرح */
    private val BY_HEX: Map<String, String> = buildMap {
        val order = listOf("grocery", "food", "taxi", "home", "shop", "health", "cafe", "gold")
        CategoryPalette.LIGHT.forEachIndexed { i, hex -> put(hex.uppercase(), order[i]) }
        put(CategoryPalette.NEUTRAL.uppercase(), "other")
        // دسته‌های درآمد
        put("#1E9E6A", "salary")
        put("#2E86AB", "taxi")
        put("#8D6A9F", "shop")
        put("#17BEBB", "health")
        put("#F2A541", "gold")
        put("#C73E8B", "food")
    }

    /** ایموجی دسته (پیش‌فرض یا شخصی) ← آیکون خطی و رنگ پیش‌فرضش */
    private val BY_EMOJI: Map<String, Pair<String, String>> = mapOf(
        "🛒" to (DesignIcons.GROCERY to "grocery"),
        "🍽" to (DesignIcons.FOOD to "food"),
        "☕" to (DesignIcons.CAFE to "cafe"),
        "🚗" to (DesignIcons.TAXI to "taxi"),
        "🚕" to (DesignIcons.TAXI to "taxi"),
        "⛽" to (DesignIcons.TAXI to "taxi"),
        "👕" to (DesignIcons.SHOP to "shop"),
        "🛍" to (DesignIcons.SHOP to "shop"),
        "🏠" to (DesignIcons.HOME to "home"),
        "💊" to (DesignIcons.HEALTH to "health"),
        "📚" to (DesignIcons.BOOK to "grocery"),
        "🎬" to (DesignIcons.TICKET to "gold"),
        "💳" to (DesignIcons.CARD to "cafe"),
        "🧾" to (DesignIcons.CARD to "cafe"),
        "🐾" to (DesignIcons.PAW to "home"),
        "💄" to (DesignIcons.SPARKLE to "shop"),
        "🏋" to (DesignIcons.DUMBBELL to "taxi"),
        "📱" to (DesignIcons.PHONE to "cafe"),
        "💰" to (DesignIcons.COINS to "other"),
        "💼" to (DesignIcons.SALARY to "salary"),
        "🏷" to (DesignIcons.TAG to "taxi"),
        "🤝" to (DesignIcons.TRANSFER to "shop"),
        "↩" to (DesignIcons.FAILED to "health"),
        "🏦" to (DesignIcons.BANK to "gold"),
        "🎁" to (DesignIcons.GIFT to "food"),
        "•" to (DesignIcons.DOTS to "other"),
        "⭐" to (DesignIcons.STAR to "gold"),
        "🔧" to (DesignIcons.WRENCH to "home"),
        "🌱" to (DesignIcons.LEAF to "grocery"),
        "🎯" to (DesignIcons.TARGET to "food"),
        "🧒" to (DesignIcons.CHILD to "health"),
        "🚬" to (DesignIcons.CIGARETTE to "other"),
        "✂" to (DesignIcons.SCISSORS to "shop"),
        "🕌" to (DesignIcons.MOSQUE to "gold"),
    )

    private val iconCache = HashMap<String, ImageVector>()

    private fun icon(path: String): ImageVector = synchronized(iconCache) {
        iconCache.getOrPut(path) { DesignIcons.svg("cat", path, strokeWidth = if (path == DesignIcons.DOTS) 3f else 1.8f) }
    }

    private fun clean(emoji: String?): String? = emoji?.replace("️", "")?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * @param colorHex رنگ ذخیره‌شده‌ی دسته‌ی اصلی (null یعنی نامعلوم ← از روی ایموجی)
     * @param emoji ایموجی دسته‌ی اصلی
     */
    fun tint(colorHex: String?, emoji: String?, dark: Boolean): CategoryTint {
        val e = clean(emoji)
        val byEmoji = e?.let { BY_EMOJI[it] }
        val key = colorHex?.uppercase()?.let { BY_HEX[it] } ?: byEmoji?.second ?: "other"
        val c = TINTS.getValue(key)
        return CategoryTint(
            bg = Color(if (dark) c[2] else c[0]),
            fg = Color(if (dark) c[3] else c[1]),
            icon = byEmoji?.let { icon(it.first) } ?: if (e == null) icon(DesignIcons.DOTS) else null,
            glyph = e,
        )
    }

    /** فقط رنگ پررنگ دسته (برای نوار سهم‌ها و نمودارها) */
    fun color(colorHex: String?, dark: Boolean): Color = tint(colorHex, null, dark).fg

    /** «بقیه» و بی‌دسته */
    fun neutral(dark: Boolean): Color = Color(TINTS.getValue("other")[if (dark) 3 else 1])
}

/** ظاهر دسته با حالت روشن/تیره‌ی فعلی */
@Composable
@ReadOnlyComposable
fun categoryTint(colorHex: String?, emoji: String?): CategoryTint =
    CategoryStyle.tint(colorHex, emoji, JibitoTheme.colors.dark)
