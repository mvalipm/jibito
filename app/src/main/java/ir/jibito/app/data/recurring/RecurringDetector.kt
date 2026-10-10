package ir.jibito.app.data.recurring

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.JalaliMonth

/** «به نظر پرداخت ماهانه است»: اجاره، قسط، اشتراک… که کاربر هنوز یادآوری‌اش را نساخته */
data class RecurringSuggestion(
    /** کلید ثابت برای «نه، ممنون» (همان طرف حساب) */
    val key: String,
    val title: String,
    val amountRial: Long,
    /** روز معمول پرداخت در ماه شمسی */
    val dayOfMonth: Int,
    /** چند ماه دیده شده */
    val months: Int,
)

/**
 * پرداخت‌های تکراری را از روی تراکنش‌ها پیدا می‌کند. یک طرف حساب وقتی «ماهانه» حساب می‌شود که
 * پرداخت‌های ۶ ماه اخیرش قاعده‌ی MonthlyPatterns را داشته باشند.
 */
object RecurringDetector {

    const val MIN_MONTHS = MonthlyPatterns.MIN_MONTHS
    private const val LOOKBACK_MONTHS = 6
    private const val MAX_SUGGESTIONS = 3

    fun detect(
        transactions: List<Transaction>,
        existingTitles: Collection<String>,
        dismissedKeys: Set<String>,
        now: Long = System.currentTimeMillis(),
    ): List<RecurringSuggestion> {
        val current = JalaliMonth.of(now)
        val oldest = current.plus(-(LOOKBACK_MONTHS - 1))
        val existing = existingTitles.map { normalize(it) }.filter { it.isNotEmpty() }

        val candidates = transactions.asSequence()
            .filter {
                it.transaction.type == FlowType.WITHDRAWAL && !it.isFailedPurchase && !it.isSelfTransfer &&
                    !it.merchant.isNullOrBlank() && it.dateMillis <= now && JalaliMonth.of(it.dateMillis).key >= oldest.key
            }
            .groupBy { normalize(it.merchant!!) }

        return candidates.mapNotNull { (key, payments) ->
            if (key in dismissedKeys) return@mapNotNull null
            if (existing.any { it.contains(key) || key.contains(it) }) return@mapNotNull null
            val pattern = MonthlyPatterns.detect(payments.map { it.transaction.amountRial to it.dateMillis }, now)
                ?: return@mapNotNull null
            RecurringSuggestion(
                key = key,
                title = payments.first().merchant!!.trim(),
                amountRial = pattern.amountRial,
                dayOfMonth = pattern.dayOfMonth,
                months = pattern.months,
            )
        }
            .sortedByDescending { it.amountRial }
            .take(MAX_SUGGESTIONS)
    }

    fun normalize(text: String): String =
        text.replace('ي', 'ی').replace('ك', 'ک').replace("‌", "").replace(" ", "").lowercase().trim()
}
