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
 * - فقط انتخاب‌های خود کاربر شمرده می‌شوند، نه دسته‌هایی که اپ خودکار گذاشته.
 */
object CategoryLearner {

    /** چند تأیید پشت سر هم لازم است تا دسته خودکار شود */
    const val AUTO_THRESHOLD = 3

    /**
     * @param userChoicesNewestFirst دسته‌هایی که کاربر خودش برای این طرف حساب انتخاب کرده، تازه‌ترین اول
     * @return null یعنی هنوز چیزی یاد گرفته نشده
     */
    fun decide(userChoicesNewestFirst: List<Long>, merchant: String): LearnedDecision? {
        val latest = userChoicesNewestFirst.firstOrNull() ?: return null
        val streak = userChoicesNewestFirst.takeWhile { it == latest }.size
        val auto = streak >= AUTO_THRESHOLD && !isPersonTransfer(merchant)
        return LearnedDecision(latest, auto)
    }

    /** طرف حساب فقط یک شماره کارت/حساب است (انتقال به یک شخص)، نه یک فروشگاه */
    fun isPersonTransfer(merchant: String): Boolean =
        merchant.startsWith("کارت") || merchant.startsWith("حساب")
}

/**
 * همان منطق، با خواندن از دیتابیس؛ نتیجه‌ها در طول یک همگام‌سازی کش می‌شوند.
 *
 * ترتیب: ۱) انتخاب‌های خود کاربر برای همین طرف حساب (CategoryLearner)، ۲) فقط اگر چیزی یاد نگرفته بود، حدس
 * (همیشه «پیشنهاد»، هیچ‌وقت خودکار): برای فروشگاه از کلمه‌های مشترک با فروشگاه‌های قبلی (MerchantWordModel)،
 * و برای کارت یک شخص از ماهانه بودن مبلغ‌های بزرگ (RentGuess).
 * حدس فروشگاه فقط وقتی است که فهرست کلمه‌های ثابت (CategorySuggester) هم جوابی ندارد؛ آن فهرست دقیق‌تر است.
 */
class CategoryLearning(private val db: AppDatabase) {

    private val cache = HashMap<Pair<String, Int>, LearnedDecision?>()
    private val wordModels = HashMap<Int, MerchantWordModel>()

    suspend fun decide(merchant: String?, flowType: Int): LearnedDecision? {
        if (merchant == null) return null
        return cache.getOrPut(merchant to flowType) {
            val choices = db.transactionFlowDao().userChoices(merchant, flowType, CategoryLearner.AUTO_THRESHOLD + 2)
            CategoryLearner.decide(choices, merchant) ?: guess(merchant, flowType)
        }
    }

    private suspend fun guess(merchant: String, flowType: Int): LearnedDecision? {
        val categoryId = if (CategoryLearner.isPersonTransfer(merchant)) {
            rentCategoryIfMonthly(merchant, flowType)
        } else if (CategorySuggester.suggest(merchant) == null) {
            wordModels.getOrPut(flowType) {
                MerchantWordModel(db.transactionFlowDao().merchantChoices(flowType, MAX_TRAINING_CHOICES).map { it.merchant to it.categoryId })
            }.predict(merchant)
        } else {
            null
        }
        return categoryId?.let { LearnedDecision(it, auto = false) }
    }

    /** برداشت ماهانه‌ی بزرگ به یک کارت ← «اجاره و مسکن»، اگر آن دسته هست و بایگانی نشده */
    private suspend fun rentCategoryIfMonthly(merchant: String, flowType: Int): Long? {
        if (flowType != EXPENSE) return null
        val now = System.currentTimeMillis()
        val payments = db.transactionFlowDao().paymentsTo(merchant, RentGuess.windowStart(now))
        if (!RentGuess.looksLikeRent(payments.map { it.amount to it.dateEpoch }, now)) return null
        return db.categoryDao().byCode(RENT_CODE)?.takeIf { !it.isArchived }?.id
    }

    private val names = HashMap<Long, String?>()

    /** اسم دسته (برای ستون suggestedCategory) */
    suspend fun nameOf(categoryId: Long): String? =
        names.getOrPut(categoryId) { db.categoryDao().byId(categoryId)?.name }

    private companion object {
        const val EXPENSE = 2
        const val RENT_CODE = "home.rent"
        const val MAX_TRAINING_CHOICES = 3000
    }
}
