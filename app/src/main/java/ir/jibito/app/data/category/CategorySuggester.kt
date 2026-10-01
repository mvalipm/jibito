package ir.jibito.app.data.category

/**
 * از روی اسم مقصد خرید (که از پیامک رمز دوم آمده) یک دسته پیشنهاد می‌دهد.
 * فعلاً یک فهرست ساده‌ی کلمه‌ها؛ بعداً اپ از انتخاب‌های خود کاربر یاد می‌گیرد.
 */
object CategorySuggester {

    /** ترتیب مهم است: «اسنپ فود» و «اسنپ تریپ» باید قبل از «اسنپ» بررسی شوند. */
    private val rules: List<Pair<List<String>, String>> = listOf(
        listOf("اسنپ فود", "اسنپ‌فود", "snappfood", "snapp food", "ریحون", "چیلیوری", "فودی") to "غذا",
        listOf("اسنپ مارکت", "اسنپ‌مارکت", "snappmarket", "اسنپ اکسپرس", "هایپر", "افق کوروش", "رفاه", "شهروند", "جانبو") to "سوپرمارکت",
        listOf("علی بابا", "علی‌بابا", "alibaba", "اسنپ تریپ", "فلای تودی", "هتل") to "سفر",
        listOf("اسنپ", "snapp", "تپسی", "tapsi", "مترو", "بی آر تی", "تاکسی", "پارکینگ", "عوارض") to "رفت‌وآمد",
        listOf("بنزین", "جایگاه سوخت", "سوخت") to "سوخت",
        listOf("دیجی کالا", "دیجی‌کالا", "digikala", "باسلام", "ترب", "تکنولایف", "بامیلو") to "خرید",
        listOf("ایرانسل", "همراه اول", "رایتل", "شارژ", "بسته اینترنت", "قبض", "آبفا", "توانیر", "گاز") to "قبض و شارژ",
        listOf("فیلیمو", "filimo", "نماوا", "namava", "سینما", "تیوال", "فیدیبو", "طاقچه") to "سرگرمی",
        listOf("داروخانه", "بیمارستان", "کلینیک", "آزمایشگاه", "دکتر") to "درمان",
    )

    fun suggest(merchant: String?): String? {
        if (merchant.isNullOrBlank()) return null
        val m = merchant.lowercase()
        return rules.firstOrNull { (words, _) -> words.any { m.contains(it.lowercase()) } }?.second
    }
}
