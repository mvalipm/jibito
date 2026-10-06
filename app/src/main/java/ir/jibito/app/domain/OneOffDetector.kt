package ir.jibito.app.domain

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.util.JalaliMonth

/**
 * پیشنهاد «خرج یک‌باره بود؟» برای خریدهای خیلی بزرگ‌تر از معمول (خانه، ماشین…).
 * فقط می‌پرسد؛ علامت را همیشه خود کاربر می‌زند. «نه» یعنی دیگر درباره‌ی همان تراکنش پرسیده نمی‌شود.
 */
object OneOffDetector {

    /** خریدهای یک سال اخیر (بلندترین بازه‌ی گزارش‌ها)؛ قدیمی‌تر روی هیچ گزارشی اثر ندارد */
    const val LOOKBACK_DAYS = 365

    /** کمتر از ۵۰ میلیون تومان «خرید بزرگ» نیست، هرچقدر هم خرج ماهانه کم باشد */
    const val MIN_RIAL = 500_000_000L

    /** دست‌کم نصف خرج یک ماه معمولی (میانگین ۳ ماه قبل از آن خرید، بدون خرج‌های یک‌باره) */
    const val SHARE_OF_MONTH = 0.5

    private const val BASELINE_MONTHS = 3
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /**
     * @param all همه‌ی تراکنش‌ها
     * @param nonSpendCategoryIds دسته‌هایی که خرج حساب نمی‌شوند (پس‌انداز، قرض دادن)
     * @return پیشنهادها، بزرگ‌ترین اول
     */
    fun find(all: List<Transaction>, nonSpendCategoryIds: Set<Long>, now: Long): List<Transaction> {
        val spends = all.filter { it.countsAsSpend(nonSpendCategoryIds) }
        // خرج عادی هر ماه (کلید ماه شمسی)؛ پایه‌ی «یک ماه معمولی»
        val routineByMonth = HashMap<Int, Long>()
        for (t in spends) {
            if (t.isOneOff) continue
            val key = JalaliMonth.of(t.dateMillis).key
            routineByMonth[key] = (routineByMonth[key] ?: 0L) + t.transaction.amountRial
        }
        val from = now - LOOKBACK_DAYS * DAY_MILLIS
        return spends
            .filter { !it.isOneOff && !it.isOneOffRejected && it.dateMillis in from..now }
            .filter { t ->
                val amount = t.transaction.amountRial
                if (amount < MIN_RIAL) return@filter false
                val month = JalaliMonth.of(t.dateMillis)
                val previous = (1..BASELINE_MONTHS).mapNotNull { routineByMonth[month.plus(-it).key] }.filter { it > 0 }
                // بدون سابقه: فقط قانون مبلغ ثابت
                previous.isEmpty() || amount >= previous.average() * SHARE_OF_MONTH
            }
            .sortedByDescending { it.transaction.amountRial }
    }

    private fun Transaction.countsAsSpend(nonSpend: Set<Long>): Boolean =
        transaction.type == FlowType.WITHDRAWAL && !isFailedPurchase && !isSelfTransfer &&
            (categoryId == null || categoryId !in nonSpend)
}
