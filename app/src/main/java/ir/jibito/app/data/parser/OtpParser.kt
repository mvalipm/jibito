package ir.jibito.app.data.parser

/**
 * پیامک «رمز دوم» خرید اینترنتی.
 * خودش تراکنش نیست، ولی دو چیز مهم دارد: مقصد خرید (مثلاً «اسنپ») و معمولاً مبلغ.
 */
data class PurchaseOtp(
    val amountRial: Long?,
    val merchant: String?,
)

object OtpParser {

    /** کلیدهایی که بعدشان اسم مقصد/پذیرنده می‌آید. ترتیب مهم است: دقیق‌ترها اول. */
    private val merchantKeys = listOf(
        "نام پذیرنده", "پذیرنده", "نام فروشگاه", "فروشگاه", "خرید از", "پرداخت به",
        "انتقال به", "بابت", "مقصد", "در وجه", "به نام",
    )

    /** کلمه‌هایی که اگر خط با آن‌ها شروع شود، دیگر اسم مقصد نیست. */
    private val notMerchantStarts = listOf("مبلغ", "رمز", "کد", "زمان", "تاریخ", "اعتبار", "مهلت", "مانده", "ساعت")

    private val amountAfterKey = Regex("مبلغ[\\s:]*([\\d,،]+)")
    private val groupedNumber = Regex("(?:^|[^\\d,،])([1-9]\\d?\\d?([,،]\\d\\d\\d)+)(?=$|[^\\d,،])")

    /** متن باید از قبل نرمال شده باشد (SmsTextNormalizer). */
    fun parse(text: String): PurchaseOtp? {
        if (!NonTransactionFilter.looksLikeOtp(text)) return null

        val amountRaw = amountAfterKey.find(text)?.groupValues?.get(1)?.let(::digitsToLong)
            ?: groupedNumber.find(text)?.groupValues?.get(1)?.let(::digitsToLong)
        val inToman = text.contains("تومان") && !text.contains("ریال")
        val amount = amountRaw?.takeIf { it in 1 until MAX_AMOUNT_RIAL }?.let { if (inToman) it * 10 else it }

        val merchant = findMerchant(text)

        // کد فعال‌سازی و ورود نه مبلغ دارد نه مقصد — رمز خرید نیست
        if (amount == null && merchant == null) return null
        return PurchaseOtp(amount, merchant)
    }

    private fun findMerchant(text: String): String? {
        val lines = text.split("\n").map { it.trim() }
        for (key in merchantKeys) {
            for ((i, line) in lines.withIndex()) {
                val at = line.indexOf(key)
                if (at < 0) continue
                var value = line.substring(at + key.length).trim().trimStart(':', '：', '-').trim()
                if (value.isEmpty()) value = lines.getOrNull(i + 1).orEmpty()
                cleanMerchant(value)?.let { return it }
            }
        }
        return null
    }

    private fun cleanMerchant(raw: String): String? {
        val v = raw.trim().trimEnd('.', '،', ',')
        if (v.isEmpty() || notMerchantStarts.any { v.startsWith(it) }) return null
        // فقط شماره کارت/حساب (مثلاً انتقال به 6037XXXX1234) ← «کارت/حساب …1234»
        val compact = v.replace(Regex("[\\s\\-]"), "")
        if (compact.matches(Regex("[\\dXx*]{6,}"))) {
            val tail = compact.takeLast(4)
            return "کارت/حساب …$tail"
        }
        return v.take(40)
    }
}
