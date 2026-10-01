package ir.jibito.app.data.linking

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.data.parser.PurchaseOtp

/** یک پیامک تراکنش (واریز/برداشت). */
data class TxRecord(
    val id: Long,
    val timeMillis: Long,
    val bankId: Int,
    val tx: ParsedTransaction,
)

/** یک پیامک رمز دوم خرید. */
data class OtpRecord(
    val id: Long,
    val timeMillis: Long,
    val bankId: Int,
    val otp: PurchaseOtp,
)

/**
 * نتیجه‌ی نهایی برای نمایش.
 * @param merchant مقصد خرید، اگر برداشت به یک رمز دوم وصل شده باشد.
 * @param refund اگر پر باشد یعنی خرید قبول نشده و همین واریز، پولِ برگشتی است.
 */
data class LinkedTransaction(
    val record: TxRecord,
    val merchant: String? = null,
    val refund: TxRecord? = null,
) {
    val isFailedPurchase: Boolean get() = refund != null
}

/**
 * منطق خرید اینترنتی (طبق توضیح کارفرما):
 *
 * ۱. رمز دوم ← اگر تا ۳ دقیقه بعدش برداشتی از همان بانک نیامد، فقط یک رمز منقضی است؛ هیچ چیزی ثبت نمی‌شود.
 * ۲. رمز دوم + برداشت تا ۳ دقیقه بعد ← خرید تأییدشده؛ مقصد خرید از پیامک رمز به برداشت اضافه می‌شود.
 * ۳. بعد از آن برداشت، اگر تا ۳ دقیقه همان مبلغ به همان بانک واریز شد ← خرید ناموفق، پول برگشته.
 *    (این دو پیامک یک «خرید ناموفق» می‌شوند، نه یک خرج و یک درآمد.)
 */
object PurchaseLinker {

    /** مهلت رمز دوم و مهلت برگشت پول. */
    const val WINDOW_MILLIS: Long = 3 * 60 * 1000L

    fun link(transactions: List<TxRecord>, otps: List<OtpRecord>): List<LinkedTransaction> {
        val txs = transactions.sortedBy { it.timeMillis }
        val usedOtps = HashSet<Long>()
        val usedRefunds = HashSet<Long>()
        val merchantOf = HashMap<Long, String?>()
        val linkedToOtp = HashSet<Long>()

        // مرحله‌ی ۱: هر برداشت ← آخرین رمز دوم مناسبِ قبل از آن
        for (w in txs) {
            if (w.tx.type != FlowType.WITHDRAWAL) continue
            val otp = otps
                .filter { o ->
                    o.id !in usedOtps &&
                        o.bankId == w.bankId &&
                        o.timeMillis <= w.timeMillis &&
                        w.timeMillis - o.timeMillis <= WINDOW_MILLIS &&
                        (o.otp.amountRial == null || o.otp.amountRial == w.tx.amountRial)
                }
                .maxByOrNull { it.timeMillis }
                ?: continue
            usedOtps += otp.id
            linkedToOtp += w.id
            merchantOf[w.id] = otp.otp.merchant
        }

        // مرحله‌ی ۲: خریدِ تأییدشده ← واریز همان مبلغ به همان بانک تا ۳ دقیقه بعد = برگشت پول
        val refundOf = HashMap<Long, TxRecord>()
        for (w in txs) {
            if (w.id !in linkedToOtp) continue
            val refund = txs.firstOrNull { d ->
                d.id !in usedRefunds &&
                    d.tx.type == FlowType.DEPOSIT &&
                    d.bankId == w.bankId &&
                    d.tx.amountRial == w.tx.amountRial &&
                    d.timeMillis >= w.timeMillis &&
                    d.timeMillis - w.timeMillis <= WINDOW_MILLIS
            } ?: continue
            usedRefunds += refund.id
            refundOf[w.id] = refund
        }

        return txs
            .filter { it.id !in usedRefunds }
            .map { LinkedTransaction(it, merchantOf[it.id], refundOf[it.id]) }
            .sortedByDescending { it.record.timeMillis }
    }
}
