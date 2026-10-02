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
        val withdrawals = items.filter { it.isWithdrawal }.sortedWith(compareBy({ it.dateMillis }, { it.id }))
        val deposits = items.filter { !it.isWithdrawal }.sortedWith(compareBy({ it.dateMillis }, { it.id }))
        val depositsByAmount = deposits.groupBy { it.amountRial }
        val used = HashSet<Long>()
        val pairs = mutableListOf<TransferPair>()
        for (w in withdrawals) {
            fun ok(d: TransferCandidate) =
                d.id !in used && d.dateMillis >= w.dateMillis && d.dateMillis - w.dateMillis <= WINDOW_MILLIS
            // اول مبلغ دقیقاً برابر؛ وگرنه واریزی که به اندازه‌ی کارمزد انتقال کمتر است (برداشت = واریز + کارمزد)
            val match = depositsByAmount[w.amountRial]?.firstOrNull(::ok)
                ?: deposits.filter { ok(it) && PurchaseLinker.isTransferFee(w.amountRial, it.amountRial) }
                    .minByOrNull { it.dateMillis }
                ?: continue
            used += match.id
            pairs += TransferPair(w.id, match.id)
        }
        return pairs
    }
}
