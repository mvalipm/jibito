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
    /** متن پیامک «اصلاحیه» است (بانک تراکنشی را برگردانده) */
    val isCorrection: Boolean = false,
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
 *
 * برگشت پولِ هر برداشتی (حتی بدون رمز دوم، مثل انتقالِ ناموفق) هم همین‌طور حساب می‌شود، اگر واریزِ هم‌مبلغ به همان بانک:
 * - تا ۳ دقیقه بعد آمد و مانده‌ها نشان می‌دهند همان حساب است (مانده‌ی قبل از واریز = مانده‌ی بعد از برداشت)، یا
 * - پیامکش «اصلاحیه» است و تا ۷۲ ساعت بعد آمد.
 */
object PurchaseLinker {

    /**
     * قانون کارفرما (برای همه‌ی بانک‌ها): اگر بعد از رمز دوم، تا ۳ دقیقه، از همان بانک برداشتی آمد که
     * **بیشتر** از مبلغ رمز است ولی اختلافش **کمتر از ۲٪** مبلغ رمز است ← کارت‌به‌کارت یا انتقال بین‌بانکی
     * با کارمزد؛ اختلاف = کارمزد. (مثلاً رمز ۱۰٬۰۰۰٬۰۰۰ و برداشت ۱۰٬۰۱۱٬۰۰۰ ریال.)
     * مبلغ دقیقاً برابر ← خرید (یا انتقال بی‌کارمزد).
     */
    const val MAX_FEE_PERCENT = 2

    fun isTransferFee(grossRial: Long, netRial: Long): Boolean {
        val fee = grossRial - netRial
        if (fee <= 0 || netRial <= 0) return false
        return fee * 100 < netRial * MAX_FEE_PERCENT
    }

    /** مهلت رمز دوم و مهلت برگشت پول. */
    const val WINDOW_MILLIS: Long = 3 * 60 * 1000L

    /** مهلت پیامک «اصلاحیه» برای برگشت پول (برگشتِ تراکنش‌های ناموفق شاپرک تا چند روز طول می‌کشد) */
    const val CORRECTION_WINDOW_MILLIS: Long = 72 * 60 * 60 * 1000L

    /** متنِ نرمال‌شده‌ی پیامک «اصلاحیه» است؟ (کشیده‌ی «اصـلاحیه» هم حساب است) */
    fun isCorrectionText(normalizedBody: String): Boolean =
        normalizedBody.replace("\u0640", "").contains("اصلاحیه")

    /** واریزِ [d] پولِ برگشتیِ برداشتِ [w] است؟ */
    private fun isRefund(w: TxRecord, d: TxRecord, linkedToOtp: Boolean): Boolean {
        if (d.tx.type != FlowType.DEPOSIT || d.bankId != w.bankId || d.tx.amountRial != w.tx.amountRial) return false
        val delay = d.timeMillis - w.timeMillis
        if (delay < 0) return false
        if (d.isCorrection && delay <= CORRECTION_WINDOW_MILLIS) return true
        if (delay > WINDOW_MILLIS) return false
        if (linkedToOtp) return true
        val wb = w.tx.balanceRial ?: return false
        val db = d.tx.balanceRial ?: return false
        return db - d.tx.amountRial == wb
    }

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
            // اول: مبلغ دقیقاً برابر (خرید)؛ بعد: برداشت = مبلغ رمز + کارمزد زیر ۲٪ (انتقال)
            val otp = inWindow.filter { it.otp.amountRial == w.tx.amountRial }.maxByOrNull { it.timeMillis }
                ?: inWindow.filter { isTransferFee(w.tx.amountRial, it.otp.amountRial!!) }
                    .maxByOrNull { it.timeMillis }
                ?: continue
            usedOtps += otp.id
            linkedToOtp += w.id
            merchantOf[w.id] = otp.otp.merchant
            val fee = w.tx.amountRial - otp.otp.amountRial!!
            if (fee > 0) feeOf[w.id] = fee
        }

        // مرحله‌ی ۲: برداشت ← واریز همان مبلغ به همان بانک = برگشت پول
        // (خریدِ تأییدشده: تا ۳ دقیقه؛ بقیه: همان حساب تا ۳ دقیقه، یا «اصلاحیه» تا ۷۲ ساعت)
        val refundOf = HashMap<Long, TxRecord>()
        for (w in txs) {
            if (w.tx.type != FlowType.WITHDRAWAL) continue
            val withOtp = w.id in linkedToOtp
            val refund = txs.firstOrNull { d -> d.id !in usedRefunds && isRefund(w, d, withOtp) } ?: continue
            usedRefunds += refund.id
            refundOf[w.id] = refund
        }

        return txs
            .filter { it.id !in usedRefunds }
            .map { LinkedTransaction(it, merchantOf[it.id], refundOf[it.id], feeOf[it.id]) }
            .sortedByDescending { it.record.timeMillis }
    }
}
