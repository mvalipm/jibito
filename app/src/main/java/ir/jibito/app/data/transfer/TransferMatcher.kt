package ir.jibito.app.data.transfer

/** فقط چیزهایی از یک تراکنش که برای پیدا کردن جفت لازم است. */
data class TransferCandidate(
    val id: Long,
    val isWithdrawal: Boolean,
    val amountRial: Long,
    val dateMillis: Long,
    /** بانکِ پیامک؛ برای تشخیص «همان حساب» */
    val bankId: Int? = null,
    /** مانده‌ی حساب بعد از تراکنش، اگر پیامک داشت */
    val balanceRial: Long? = null,
    /** متن پیامک از پایا/ساتنا/حواله می‌گوید ← انتقال بین‌بانکی که دیر می‌نشیند */
    val isInterbank: Boolean = false,
    /** پیامکِ «اصلاحیه» (برگشت پول از طرف بانک) ← هیچ‌وقت انتقال نیست */
    val isCorrection: Boolean = false,
    /** شماره‌ی حساب/کارتِ خود پیامک، اگر در متن بود */
    val account: String? = null,
)

/** یک پیشنهاد: «این برداشت و این واریز احتمالاً انتقال بین حساب‌های خودت است». */
data class TransferPair(val withdrawalId: Long, val depositId: Long)

/**
 * پیدا کردن جفت‌های «برداشت ← واریزِ هم‌مبلغ» که می‌توانند انتقال بین حساب‌های خود کاربر باشند.
 *
 * قانون‌ها:
 * - مبلغ دقیقاً برابر، یا برداشت = واریز + کارمزد که بین ۵۰۰ تا ۵۰٬۰۰۰ تومان است (و کمتر از خود مبلغ).
 * - واریز بعد از برداشت (یا هم‌زمان): حداکثر ۳۰ دقیقه بعد (کارت‌به‌کارت و انتقال داخلی فوری‌اند)؛
 *   اگر متن یکی از دو پیامک از پایا/ساتنا/حواله بگوید، حداکثر ۷۲ ساعت (تعطیلات آخر هفته).
 * - برداشت و واریز روی «همان حساب» جفت نمی‌شوند: هم‌بانک و (شماره‌حساب یکی، یا مانده‌ی قبل از واریز =
 *   مانده‌ی بعد از برداشت) ← پول برگشته، نه انتقال. انتقال از یک حساب به خودش معنی ندارد.
 * - واریزِ «اصلاحیه» هیچ‌وقت انتقال نیست (بانک پولِ تراکنشی را برگردانده).
 * - هر تراکنش حداکثر در یک جفت.
 * - برداشت‌ها به ترتیب زمان؛ هر برداشت نزدیک‌ترین واریزِ مناسبِ آزاد بعد از خودش را می‌گیرد.
 *
 * فقط پیشنهاد است؛ تا کاربر تأیید نکند چیزی عوض نمی‌شود.
 */
object TransferMatcher {

    const val WINDOW_MILLIS = 30L * 60 * 1000
    const val INTERBANK_WINDOW_MILLIS = 72L * 60 * 60 * 1000

    /** کارمزد کارت‌به‌کارت/پایا/ساتنا: ۵۰۰ تا ۵۰٬۰۰۰ تومان */
    const val MIN_FEE_RIAL = 5_000L
    const val MAX_FEE_RIAL = 500_000L

    private val INTERBANK_KEYWORDS = listOf("پایا", "ساتنا", "حواله")

    /** آیا متن پیامک از انتقال بین‌بانکیِ دیرنشین (پایا/ساتنا/حواله) می‌گوید؟ */
    fun isInterbankText(body: String): Boolean = INTERBANK_KEYWORDS.any { body.contains(it) }

    /** برداشت = واریز + کارمزدی در بازه‌ی مجاز */
    fun isTransferFee(grossRial: Long, netRial: Long): Boolean {
        val fee = grossRial - netRial
        return netRial > 0 && fee in MIN_FEE_RIAL..MAX_FEE_RIAL && fee < netRial
    }

    /** واریزِ [d] حداکثر چقدر بعد از برداشتِ [w] می‌تواند بنشیند */
    fun windowFor(w: TransferCandidate, d: TransferCandidate): Long =
        if (w.isInterbank || d.isInterbank) INTERBANK_WINDOW_MILLIS else WINDOW_MILLIS

    /** هم‌بانک و (شماره‌حساب یکی، یا مانده‌ها پشت سر هم) ← واریز روی همان حسابی نشسته که برداشت از آن بوده */
    fun isSameAccount(w: TransferCandidate, d: TransferCandidate): Boolean {
        if (w.bankId == null || w.bankId != d.bankId) return false
        if (w.account != null && d.account != null) return w.account == d.account
        val wb = w.balanceRial ?: return false
        val db = d.balanceRial ?: return false
        return db - d.amountRial == wb
    }

    /** ورودی‌ها باید از قبل فیلتر شده باشند (حذف‌شده، خرید ناموفق، انتقال‌های قبلی و ردشده‌ها کنار رفته‌اند). */
    fun findPairs(items: List<TransferCandidate>): List<TransferPair> {
        val order = compareBy<TransferCandidate>({ it.dateMillis }, { it.id })
        val withdrawals = items.filter { it.isWithdrawal }.sortedWith(order)
        val deposits = items.filter { !it.isWithdrawal && !it.isCorrection }.sortedWith(order)
        val depositsByAmount = deposits.groupBy { it.amountRial }
        val datesByAmount = depositsByAmount.mapValues { (_, list) -> LongArray(list.size) { list[it].dateMillis } }
        val depositDates = LongArray(deposits.size) { deposits[it].dateMillis }
        val used = HashSet<Long>()
        val pairs = mutableListOf<TransferPair>()
        for (w in withdrawals) {
            fun ok(d: TransferCandidate) =
                d.id !in used && d.dateMillis >= w.dateMillis &&
                    d.dateMillis - w.dateMillis <= windowFor(w, d) && !isSameAccount(w, d)
            // اول مبلغ دقیقاً برابر؛ وگرنه واریزی که به اندازه‌ی کارمزد انتقال کمتر است (برداشت = واریز + کارمزد).
            // فقط واریزهای بازه‌ی زمانی بررسی می‌شوند (جستجوی دودویی روی زمان)، نه همه‌ی واریزها.
            val match = depositsByAmount[w.amountRial]?.let { sameAmount ->
                firstInWindow(sameAmount, datesByAmount.getValue(w.amountRial), w.dateMillis) { ok(it) }
            }
                ?: firstInWindow(deposits, depositDates, w.dateMillis) { ok(it) && isTransferFee(w.amountRial, it.amountRial) }
                ?: continue
            used += match.id
            pairs += TransferPair(w.id, match.id)
        }
        return pairs
    }

    /** اولین واریز (به ترتیب زمان) از [from] تا بلندترین بازه بعدش که شرط را دارد */
    private inline fun firstInWindow(
        deposits: List<TransferCandidate>,
        dates: LongArray,
        from: Long,
        predicate: (TransferCandidate) -> Boolean,
    ): TransferCandidate? {
        var i = lowerBound(dates, from)
        while (i < deposits.size && dates[i] - from <= INTERBANK_WINDOW_MILLIS) {
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
