package ir.jibito.app.data.parser

/**
 * پیامک «رمز دوم» خرید اینترنتی.
 * خودش تراکنش نیست، ولی دو چیز مهم دارد: مقصد خرید (مثلاً «اسنپ») و معمولاً مبلغ.
 */
data class PurchaseOtp(
    val amountRial: Long?,
    val merchant: String?,
    /** رمزِ «انتقال» (کارت‌به‌کارت، پایا، ساتنا) است، نه خرید — برداشتش ممکن است کارمزد هم داشته باشد */
    val isTransfer: Boolean = false,
)

object OtpParser {

    private val transferWords = listOf("انتقال", "کارت به کارت", "کارت‌به‌کارت", "پایا", "ساتنا")
    private val amountAfterKey = Regex("مبلغ[\\s:]*([\\d,،]+)")
    private val groupedNumber = Regex("(?:^|[^\\d,،])([1-9]\\d?\\d?([,،]\\d\\d\\d)+)(?=$|[^\\d,،])")

    /** متن باید از قبل نرمال شده باشد (SmsTextNormalizer). */
    fun parse(text: String): PurchaseOtp? {
        if (!NonTransactionFilter.looksLikeOtp(text)) return null

        val amountRaw = amountAfterKey.find(text)?.groupValues?.get(1)?.let(::digitsToLong)
            ?: groupedNumber.find(text)?.groupValues?.get(1)?.let(::digitsToLong)
        val inToman = text.contains("تومان") && !text.contains("ریال")
        val amount = amountRaw?.takeIf { it > 0 }?.let { if (inToman) it * 10 else it }

        val merchant = MerchantExtractor.find(text) ?: merchantAfterPurchaseLine(text)

        // کد فعال‌سازی و ورود نه مبلغ دارد نه مقصد — رمز خرید نیست
        if (amount == null && merchant == null) return null
        val isTransfer = transferWords.any { text.contains(it) }
        return PurchaseOtp(amount, merchant, isTransfer)
    }

    /** خطی که فقط «نوع رمز» است و اسم فروشگاه خط بعدش می‌آید */
    private val purchaseLines = setOf("خرید", "خرید اینترنتی", "خرید کالا", "پرداخت", "پرداخت اینترنتی")

    /**
     * قالب پاسارگاد (و بانک‌های مشابه): اسم فروشگاه بدون کلید، در خطِ بعد از «خرید»:
     * «پاسارگاد / خرید / اسنپ مارکت / مبلغ:12,321,000 / رمز: 04720».
     */
    private fun merchantAfterPurchaseLine(text: String): String? {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val at = lines.indexOfFirst { it in purchaseLines }
        if (at < 0) return null
        val next = lines.getOrNull(at + 1) ?: return null
        // خطِ عددی (مبلغ، رمز، ساعت) اسم فروشگاه نیست
        if (next.any { it.isDigit() }) return null
        return MerchantExtractor.clean(next)
    }
}
