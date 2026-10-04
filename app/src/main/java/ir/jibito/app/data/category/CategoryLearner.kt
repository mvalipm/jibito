package ir.jibito.app.data.category

import ir.jibito.app.data.local.AppDatabase

/**
 * نتیجه‌ی یادگیری برای یک طرف حساب:
 * - auto = true ← اپ مطمئن است و دسته را خودکار می‌گذارد (کاربر همیشه می‌تواند عوضش کند).
 * - auto = false ← فقط «پیشنهاد» است؛ کاربر با یک لمس تأیید می‌کند.
 */
data class LearnedDecision(val categoryId: Long, val auto: Boolean)

/**
 * منطق یادگیری دسته (سند MVP، گزینه‌ی D):
 * - «الگو» = طرف حساب (فروشگاه/مقصد) + نوع تراکنش (خرج یا درآمد). مبلغ مهم نیست.
 * - اپ اول پیشنهاد می‌دهد. وقتی کاربر **۳ بار پشت سر هم** همان دسته را برای همان طرف حساب انتخاب کرد،
 *   از آن به بعد خودکار می‌گذارد.
 * - هر انتخاب متفاوت، شمارش را از صفر شروع می‌کند (آخرین انتخاب کاربر پیشنهاد بعدی می‌شود).
 * - انتقال کارت‌به‌کارت به یک شخص هیچ‌وقت خودکار نمی‌شود (یک کارت هم اجاره است هم قرض)؛ فقط پیشنهاد.
 * - «طرف حساب» عمومی هم هیچ‌وقت خودکار نمی‌شود: بعضی پیامک‌ها به‌جای اسم فروشگاه، اسم شرکت پرداخت
 *   یا درگاه یا پایانه را دارند («به پرداخت ملت»، «شاپرک»، «پایانه»)؛ پشت این اسم‌ها صدها فروشگاه مختلف است.
 * - اگر کاربر برای همین طرف حساب قبلاً دسته‌ی دیگری هم زده (در [LOOKBACK] انتخاب آخر)، یعنی این اسم
 *   قطعاً یک فروشگاه نیست؛ آن‌وقت [AMBIGUOUS_THRESHOLD] تأیید پشت سر هم لازم است، نه ۳ تا.
 * - فقط انتخاب‌های خود کاربر شمرده می‌شوند، نه دسته‌هایی که اپ خودکار گذاشته.
 */
object CategoryLearner {

    /** چند تأیید پشت سر هم لازم است تا دسته خودکار شود */
    const val AUTO_THRESHOLD = 3

    /** وقتی همین طرف حساب قبلاً دسته‌ی دیگری هم گرفته، چند تأیید پشت سر هم لازم است */
    const val AMBIGUOUS_THRESHOLD = 5

    /** چند انتخاب آخر کاربر برای تصمیم خوانده می‌شود */
    const val LOOKBACK = 10

    /**
     * @param userChoicesNewestFirst دسته‌هایی که کاربر خودش برای این طرف حساب انتخاب کرده، تازه‌ترین اول
     *   (حداکثر [LOOKBACK] تا لازم است)
     * @return null یعنی هنوز چیزی یاد گرفته نشده
     */
    fun decide(userChoicesNewestFirst: List<Long>, merchant: String): LearnedDecision? {
        val recent = userChoicesNewestFirst.take(LOOKBACK)
        val latest = recent.firstOrNull() ?: return null
        val streak = recent.takeWhile { it == latest }.size
        // در همین چند انتخاب آخر، دسته‌ی دیگری هم بوده؟ ← اسمِ مشترکِ چند فروشگاه است، شاهد بیشتری لازم است
        val needed = if (recent.any { it != latest }) AMBIGUOUS_THRESHOLD else AUTO_THRESHOLD
        val auto = streak >= needed && canAutoLearn(merchant)
        return LearnedDecision(latest, auto)
    }

    /** دسته‌ی این طرف حساب اصلاً می‌تواند خودکار شود؟ (نه شخص، نه اسم عمومی شرکت پرداخت/درگاه) */
    fun canAutoLearn(merchant: String): Boolean = !isPersonTransfer(merchant) && !isGenericMerchant(merchant)

    /** طرف حساب فقط یک شماره کارت/حساب است (انتقال به یک شخص)، نه یک فروشگاه */
    fun isPersonTransfer(merchant: String): Boolean =
        merchant.startsWith("کارت") || merchant.startsWith("حساب")

    /**
     * اسمی که مال یک فروشگاه نیست، بلکه مال واسطه‌ی پرداخت است: شرکت‌های پرداخت (PSP)، شاپرک، درگاه‌های
     * اینترنتی، پایانه/کارتخوان، یا کلمه‌های کلی مثل «خرید اینترنتی». (اسمِ تمیزشده‌ی MerchantExtractor)
     *
     * این اسم‌ها نه دسته‌ی خودکار می‌گیرند، نه «کارت خودم» یاد گرفته می‌شوند (وگرنه همه‌ی خریدهایی که از
     * همان درگاه رد می‌شوند یک‌جا «انتقال به خودم» می‌شدند).
     */
    fun isGenericMerchant(merchant: String): Boolean {
        val key = genericKey(merchant)
        if (key.isEmpty()) return true
        return key in GENERIC_EXACT || GENERIC_PARTS.any { key.contains(it) }
    }

    /** یکدست برای مقایسه: حروف کوچک، بدون فاصله و نیم‌فاصله و خط‌تیره و نقطه */
    private fun genericKey(s: String): String =
        s.lowercase().filterNot { it.isWhitespace() || it == '\u200c' || it == '-' || it == '.' || it == '_' }

    /** اگر جایی از اسم بیاید، اسم عمومی است (همه یکدست‌شده با genericKey) */
    private val GENERIC_PARTS = listOf(
        // شبکه و شرکت‌های پرداخت (PSP)
        "شاپرک", "shaparak", "پرداختالکترونیک", "بهپرداخت", "behpardakht", "آسانپرداخت", "asanpardakht",
        "سامانکیش", "samankish", "ایرانکیش", "irankish", "پرداختنوین", "تجارتالکترونیک", "فنآوا", "فناوا",
        "سایانکارت", "سپهرپرداخت", "کیشپرداخت", "سداد", "sadad", "پرداختیار",
        // درگاه‌ها و واسطه‌های پرداخت اینترنتی
        "زرینپال", "zarinpal", "پیپینگ", "payping", "آیدیپی", "idpay", "نکستپی", "nextpay", "وندار", "vandar",
        "جیبیت", "jibit", "پیاستار", "paystar", "زیبال", "zibal", "درگاه", "ipg",
        // پایانه و کلمه‌های کلی
        "پایانه", "ترمینال", "terminal", "کارتخوان", "پرداختاینترنتی", "خریداینترنتی", "پرداختاینترنت", "خریداینترنت",
    )

    /** فقط اگر کل اسم همین باشد، اسم عمومی است (کوتاه‌اند و ممکن است جزو اسم یک فروشگاه واقعی باشند) */
    private val GENERIC_EXACT = setOf(
        "اینترنتی", "خرید", "پرداخت", "فروشگاه", "فروشگاهی", "پذیرنده", "خریدکالا", "پوز", "pos", "سپ", "آپ", "تاپ",
    )
}

/** همان منطق، با خواندن از دیتابیس؛ نتیجه‌ها در طول یک همگام‌سازی کش می‌شوند. */
class CategoryLearning(private val db: AppDatabase) {

    private val cache = HashMap<Pair<String, Int>, LearnedDecision?>()

    suspend fun decide(merchant: String?, flowType: Int): LearnedDecision? {
        if (merchant == null) return null
        return cache.getOrPut(merchant to flowType) {
            val choices = db.transactionFlowDao().userChoices(merchant, flowType, CategoryLearner.LOOKBACK)
            CategoryLearner.decide(choices, merchant)
        }
    }

    private val names = HashMap<Long, String?>()

    /** اسم دسته (برای ستون suggestedCategory) */
    suspend fun nameOf(categoryId: Long): String? =
        names.getOrPut(categoryId) { db.categoryDao().byId(categoryId)?.name }
}
