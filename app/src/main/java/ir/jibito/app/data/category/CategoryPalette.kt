package ir.jibito.app.data.category

/**
 * رنگ دسته‌های اصلی، از پالت ۸ رنگیِ اعتبارسنجی‌شده برای نمودار
 * (قابل تشخیص برای کوررنگی و دید عادی، در حالت روشن و تیره؛ با validate_palette.js بررسی شده).
 * هر دسته‌ی اصلی یک رنگ ثابت دارد — رنگ دنبال «دسته» است، نه رتبه‌اش در نمودار.
 * نسخه‌ی تیره‌ی هر رنگ در لایه‌ی نمایش (ChartColors) انتخاب می‌شود.
 */
object CategoryPalette {

    val LIGHT = listOf("#2A78D6", "#EB6834", "#1BAF7A", "#EDA100", "#E87BA4", "#008300", "#4A3AA7", "#E34948")
    val DARK = listOf("#3987E5", "#D95926", "#199E70", "#C98500", "#D55181", "#008300", "#9085E9", "#E66767")

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
