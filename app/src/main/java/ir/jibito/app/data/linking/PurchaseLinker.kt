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
    /** کارمزد انتقال (برداشت − مبلغ رمز)، اگر برداشت به رمزِ انتقال وصل شده باشد */
    val feeRial: Long? = null,
) {
    val isFailedPurchase: Boolean get() = refund != null
}

/**
 * منطق خرید اینترنتی (طبق توضیح کارفرما):
 *
 * ۱. رمز دوم ← اگر تا ۳ دقیقه بعدش برداشتی از همان بانک نیامد، فقط یک رمز منقضی است؛ هیچ چیزی ثبت نمی‌شود.
 * ۲. رمز دوم + برداشت تا ۳ دقیقه بعد، با مبلغ دقیقاً برابر ← خرید تأییدشده؛
 *    مقصد خرید از پیامک رمز به برداشت اضافه می‌شود. (مبلغ‌ها هر دو به ریال مقایسه می‌شوند.)
 * ۳. بعد از آن برداشت، اگر تا ۳ دقیقه همان مبلغ به همان بانک واریز شد ← خرید ناموفق، پول برگشته.
 *    (این دو پیامک یک «خرید ناموفق» می‌شوند، نه یک خرج و یک درآمد.)
 */
object PurchaseLinker {

    /**
     * کارمزد انتقال: برداشتِ انتقال می‌تواند کمی بیشتر از مبلغ رمز باشد
     * (مثلاً رمز ۱۰٬۰۰۰٬۰۰۰ و برداشت ۱۰٬۰۱۱٬۰۰۰ ریال). حداکثر ۰٫۵٪ مبلغ، حداقل سقف ۲۰ هزار و حداکثر ۵۰۰ هزار ریال.
     * برای خرید این تحمل نیست: مبلغ باید دقیقاً برابر باشد.
     */
    fun isTransferFee(grossRial: Long, netRial: Long): Boolean {
        val fee = grossRial - netRial
        if (fee <= 0) return false
        val cap = minOf(500_000L, maxOf(20_000L, grossRial / 200))
        return fee <= cap
    }

    /** مهلت رمز دوم و مهلت برگشت پول. */
    const val WINDOW_MILLIS: Long = 3 * 60 * 1000L

    fun link(transactions: List<TxRecord>, otps: List<OtpRecord>): List<LinkedTransaction> {
        val txs = transactions.sortedBy { it.timeMillis }
        val usedOtps = HashSet<Long>()
        val usedRefunds = HashSet<Long>()
        val merchantOf = HashMap<Long, String?>()
        val feeOf = HashMap<Long, Long>()
        val linkedToOtp = HashSet<Long>()

        // مرحله‌ی ۱: هر برداشت ← آخرین رمز دوم مناسبِ قبل از آن
        for (w in txs) {
            if (w.tx.type != FlowType.WITHDRAWAL) continue
            val inWindow = otps.filter { o ->
                o.id !in usedOtps &&
                    o.bankId == w.bankId &&
                    o.timeMillis <= w.timeMillis &&
                    w.timeMillis - o.timeMillis <= WINDOW_MILLIS &&
                    o.otp.amountRial != null // رمزِ بدون مبلغ وصل نمی‌شود
            }
            // اول: مبلغ دقیقاً برابر (خرید و انتقال)؛ بعد: فقط برای رمزِ انتقال، برداشت = مبلغ + کارمزد
            val otp = inWindow.filter { it.otp.amountRial == w.tx.amountRial }.maxByOrNull { it.timeMillis }
                ?: inWindow.filter { it.otp.isTransfer && isTransferFee(w.tx.amountRial, it.otp.amountRial!!) }
                    .maxByOrNull { it.timeMillis }
                ?: continue
            usedOtps += otp.id
            linkedToOtp += w.id
            merchantOf[w.id] = otp.otp.merchant
            val fee = w.tx.amountRial - otp.otp.amountRial!!
            if (fee > 0) feeOf[w.id] = fee
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
            .map { LinkedTransaction(it, merchantOf[it.id], refundOf[it.id], feeOf[it.id]) }
            .sortedByDescending { it.record.timeMillis }
    }
}
