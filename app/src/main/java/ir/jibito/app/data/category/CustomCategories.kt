package ir.jibito.app.data.category

/** نتیجه‌ی ساختن دسته‌ی شخصی */
sealed interface CreateCategoryResult {
    data class Created(val id: Long) : CreateCategoryResult
    data class Invalid(val reason: Reason) : CreateCategoryResult

    enum class Reason { EMPTY, TOO_LONG, DUPLICATE }
}

/**
 * قانون‌های دسته‌ی شخصی (سند MVP: «کاربر بتواند در هر سطح دسته‌ی شخصی اضافه کند»):
 * - اسم خالی نباشد و حداکثر ۳۰ حرف.
 * - اسم تکراری نباشد (در همان نوع خرج/درآمد)، چون پیشنهاد دسته با اسم پیدا می‌شود.
 *   مقایسه بدون توجه به ی/ک عربی، نیم‌فاصله و فاصله‌ها.
 */
object CustomCategories {

    const val MAX_NAME_LENGTH = 30

    /** آیکون‌هایی که برای دسته‌ی اصلی شخصی می‌شود انتخاب کرد */
    val ICONS = listOf(
        "⭐", "🎯", "🛍", "🧾", "🎁", "🧒", "🚬", "💼", "🔧", "🌱", "✂", "🕌",
        "🌐", "🎮", "✈", "🎵", "💡", "📷", "🎓", "🚌", "☂", "⚡", "💧", "🔥",
        "☕", "🍽", "🛒", "🚗", "🏠", "💊", "📚", "🎬", "🐾", "🏋", "💰", "📱",
    )


    fun clean(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    fun validate(name: String, existingNames: Collection<String>): CreateCategoryResult.Reason? {
        val n = clean(name)
        return when {
            n.isEmpty() -> CreateCategoryResult.Reason.EMPTY
            n.length > MAX_NAME_LENGTH -> CreateCategoryResult.Reason.TOO_LONG
            existingNames.any { key(it) == key(n) } -> CreateCategoryResult.Reason.DUPLICATE
            else -> null
        }
    }

    private fun key(s: String): String =
        s.replace('ي', 'ی').replace('ك', 'ک').replace("‌", "").replace(" ", "").lowercase()
}
