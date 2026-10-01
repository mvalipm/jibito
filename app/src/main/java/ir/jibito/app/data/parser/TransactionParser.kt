package ir.jibito.app.data.parser

import ir.jibito.app.data.bank.Bank

/**
 * نقطه‌ی ورود همه‌ی پارس‌ها:
 * ۱. متن یکدست می‌شود.
 * ۲. اگر رمز/کد/تبلیغ بود، کنار گذاشته می‌شود.
 * ۳. اول پارسر اختصاصی بانک (اگر داشته باشیم)، بعد پارسر هوشمند عمومی.
 * ۴. اگر مبلغ به «تومان» بود، به ریال تبدیل می‌شود (همه‌چیز داخل اپ ریال است).
 */
object TransactionParser {

    private val bankParsers: Map<String, SmsParser> = mapOf(
        "mellat" to MellatParser,
        "pasargad" to PasargadParser,
    )

    fun parse(bank: Bank, rawBody: String): ParsedTransaction? {
        val text = SmsTextNormalizer.normalize(rawBody)
        if (text.isEmpty() || NonTransactionFilter.isNotTransaction(text)) return null

        val result = bankParsers[bank.parserKey]?.parse(text) ?: SmartParser.parse(text) ?: return null

        val inToman = text.contains("تومان") && !text.contains("ریال")
        return if (inToman) {
            result.copy(amountRial = result.amountRial * 10, balanceRial = result.balanceRial?.times(10))
        } else {
            result
        }
    }

    /** اگر پیامک «رمز دوم» خرید بود، مبلغ و مقصدش را برمی‌گرداند. */
    fun parseOtp(rawBody: String): PurchaseOtp? = OtpParser.parse(SmsTextNormalizer.normalize(rawBody))
}
