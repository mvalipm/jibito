package ir.jibito.app.data.recurring

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import java.util.Calendar
import kotlin.math.abs

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
 * پرداخت‌های تکراری را از روی تراکنش‌ها پیدا می‌کند. یک طرف حساب وقتی «ماهانه» حساب می‌شود که:
 * - در دست‌کم ۳ ماه شمسیِ مختلف از ۶ ماه اخیر، پرداختی با مبلغ نزدیک (±۱۵٪ میانه) داشته باشد،
 * - در هر ماه تقریباً یک بار (نه خریدهای پشت سر هم از یک فروشگاه)،
 * - حدوداً در یک روز ماه (پراکندگی روز حداکثر ۴ روز)،
 * - و هنوز فعال باشد (این ماه یا ماه قبل هم پرداخت شده).
 */
object RecurringDetector {

    const val MIN_MONTHS = 3
    private const val LOOKBACK_MONTHS = 6
    private const val AMOUNT_TOLERANCE = 0.15
    private const val MAX_DAY_SPREAD = 4
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
            val median = median(payments.map { it.transaction.amountRial })
            val similar = payments.filter { abs(it.transaction.amountRial - median) <= median * AMOUNT_TOLERANCE }
            val byMonth = similar.groupBy { JalaliMonth.of(it.dateMillis).key }
            if (byMonth.size < MIN_MONTHS) return@mapNotNull null
            // تقریباً یک بار در ماه
            if (similar.size > byMonth.size * 4 / 3) return@mapNotNull null
            // هنوز فعال
            if (current.key !in byMonth && current.plus(-1).key !in byMonth) return@mapNotNull null
            val days = byMonth.values.map { jalaliDay(it.first().dateMillis) }
            val typicalDay = median(days.map { it.toLong() }).toInt()
            if (days.any { abs(it - typicalDay) > MAX_DAY_SPREAD }) return@mapNotNull null
            RecurringSuggestion(
                key = key,
                title = payments.first().merchant!!.trim(),
                amountRial = median(similar.map { it.transaction.amountRial }),
                dayOfMonth = typicalDay.coerceIn(1, 31),
                months = byMonth.size,
            )
        }
            .sortedByDescending { it.amountRial }
            .take(MAX_SUGGESTIONS)
    }

    fun normalize(text: String): String =
        text.replace('ي', 'ی').replace('ك', 'ک').replace("‌", "").replace(" ", "").lowercase().trim()

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun jalaliDay(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return Jalali.fromGregorian(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)).third
    }
}
