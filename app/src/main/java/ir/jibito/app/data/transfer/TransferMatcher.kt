package ir.jibito.app.data.transfer

import ir.jibito.app.data.linking.PurchaseLinker

/** فقط چیزهایی از یک تراکنش که برای پیدا کردن جفت لازم است. */
data class TransferCandidate(
    val id: Long,
    val isWithdrawal: Boolean,
    val amountRial: Long,
    val dateMillis: Long,
)

/** یک پیشنهاد: «این برداشت و این واریز احتمالاً انتقال بین حساب‌های خودت است». */
data class TransferPair(val withdrawalId: Long, val depositId: Long)

/**
 * پیدا کردن جفت‌های «برداشت ← واریزِ هم‌مبلغ» که می‌توانند انتقال بین حساب‌های خود کاربر باشند.
 *
 * قانون‌ها:
 * - مبلغ دقیقاً برابر، یا برداشت = واریز + کارمزد انتقال (مثلاً ۱۱ هزار ریال).
 * - واریز بعد از برداشت (یا هم‌زمان)، حداکثر ۲۴ ساعت بعد (پایا و ساتنا هم دیر می‌رسند).
 * - هر تراکنش حداکثر در یک جفت.
 * - برداشت‌ها به ترتیب زمان؛ هر برداشت نزدیک‌ترین واریزِ آزاد بعد از خودش را می‌گیرد.
 *
 * فقط پیشنهاد است؛ تا کاربر تأیید نکند چیزی عوض نمی‌شود.
 */
object TransferMatcher {

    const val WINDOW_MILLIS = 24L * 60 * 60 * 1000

    /** ورودی‌ها باید از قبل فیلتر شده باشند (حذف‌شده، خرید ناموفق، انتقال‌های قبلی و ردشده‌ها کنار رفته‌اند). */
    fun findPairs(items: List<TransferCandidate>): List<TransferPair> {
        val order = compareBy<TransferCandidate>({ it.dateMillis }, { it.id })
        val withdrawals = items.filter { it.isWithdrawal }.sortedWith(order)
        val deposits = items.filter { !it.isWithdrawal }.sortedWith(order)
        val depositsByAmount = deposits.groupBy { it.amountRial }
        val datesByAmount = depositsByAmount.mapValues { (_, list) -> LongArray(list.size) { list[it].dateMillis } }
        val depositDates = LongArray(deposits.size) { deposits[it].dateMillis }
        val used = HashSet<Long>()
        val pairs = mutableListOf<TransferPair>()
        for (w in withdrawals) {
            fun ok(d: TransferCandidate) =
                d.id !in used && d.dateMillis >= w.dateMillis && d.dateMillis - w.dateMillis <= WINDOW_MILLIS
            // اول مبلغ دقیقاً برابر؛ وگرنه واریزی که به اندازه‌ی کارمزد انتقال کمتر است (برداشت = واریز + کارمزد).
            // فقط واریزهای همان ۲۴ ساعت بررسی می‌شوند (جستجوی دودویی روی زمان)، نه همه‌ی واریزها.
            val match = depositsByAmount[w.amountRial]?.let { sameAmount ->
                firstInWindow(sameAmount, datesByAmount.getValue(w.amountRial), w.dateMillis) { ok(it) }
            }
                ?: firstInWindow(deposits, depositDates, w.dateMillis) { ok(it) && PurchaseLinker.isTransferFee(w.amountRial, it.amountRial) }
                ?: continue
            used += match.id
            pairs += TransferPair(w.id, match.id)
        }
        return pairs
    }

    /** اولین واریز (به ترتیب زمان) از [from] تا ۲۴ ساعت بعدش که شرط را دارد */
    private inline fun firstInWindow(
        deposits: List<TransferCandidate>,
        dates: LongArray,
        from: Long,
        predicate: (TransferCandidate) -> Boolean,
    ): TransferCandidate? {
        var i = lowerBound(dates, from)
        while (i < deposits.size && dates[i] - from <= WINDOW_MILLIS) {
            if (predicate(deposits[i])) return deposits[i]
            i++
        }
        return null
    }

    /** اولین اندیسی که زمانش >= value است */
    private fun lowerBound(sorted: LongArray, value: Long): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < value) lo = mid + 1 else hi = mid
        }
        return lo
    }
}
