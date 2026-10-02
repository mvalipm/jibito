package ir.jibito.app.data.category

/**
 * رنگ دسته‌های اصلی. LIGHT کلیدهایی است که در دیتابیس ذخیره می‌شوند (رنگ دنبال «دسته» است، نه رتبه‌اش در نمودار)؛
 * رنگی که واقعاً دیده می‌شود از DISPLAY_LIGHT / DISPLAY_DARK با همان اندیس می‌آید (ChartColors).
 * پالت نمایش هماهنگ است: روشنایی و شدت نزدیک، فقط فام فرق می‌کند؛ قرمز (هشدار) و سبز (پول آمده) در آن نیست.
 * با validate_palette.js بررسی شده: نوار روشنایی، کف شدت، جدایی برای کوررنگی (ΔE ≥ ۱۰) و دید عادی (≥ ۱۵) بین همسایه‌ها.
 */
object CategoryPalette {

    val LIGHT = listOf("#2A78D6", "#EB6834", "#1BAF7A", "#EDA100", "#E87BA4", "#008300", "#4A3AA7", "#E34948")

    /** رنگ نمایش هر اندیس LIGHT در حالت روشن (روی زمینه‌ی کرم، کنتراست ≥ ۳) */
    val DISPLAY_LIGHT = listOf("#2F5DB8", "#B06A00", "#A0236B", "#0090B8", "#6D3FC0", "#7A7F0E", "#D0608C", "#8E4F14")

    /** همان رنگ‌ها برای حالت تیره (پله‌ی خودش، نه وارونه‌ی خودکار) */
    val DISPLAY_DARK = listOf("#5485F2", "#C48030", "#AB3578", "#3296B5", "#855BCF", "#7D8916", "#DC548F", "#A45C01")

    /** «بیرون از خرج» و «بقیه» خاکستری‌اند */
    const val NEUTRAL = "#8C8C8C"

    /** رنگ هر دسته‌ی اصلی پیش‌فرض (اندیس در پالت) */
    val BY_CODE: Map<String, String> = mapOf(
        "food" to LIGHT[0],
        "dining" to LIGHT[1],
        "transport" to LIGHT[2],
        "home" to LIGHT[3],
        "clothing" to LIGHT[4],
        "health" to LIGHT[5],
        "finance" to LIGHT[6],
        "fun" to LIGHT[7],
        "education" to LIGHT[0],
        "pets" to LIGHT[3],
        "beauty" to LIGHT[4],
        "sport" to LIGHT[2],
        "electronics" to LIGHT[6],
        "savings" to NEUTRAL,
    )

    /** رنگ nامین دسته‌ی اصلی شخصی (به نوبت، از رنگ دوم) */
    fun forCustom(index: Int): String = LIGHT[(index + 1) % LIGHT.size]
}
