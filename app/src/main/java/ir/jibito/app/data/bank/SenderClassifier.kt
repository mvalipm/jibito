package ir.jibito.app.data.bank

/** نتیجه‌ی بررسی فرستنده‌ی یک پیامک. */
sealed interface SenderType {
    /** از سرشماره‌ی یک بانک شناخته‌شده آمده. */
    data class BankSender(val bank: Bank) : SenderType

    /** از یک شماره‌ی موبایل شخصی آمده — اصلاً وارد سیستم نمی‌شود. */
    data object Personal : SenderType

    /** هیچ‌کدام (اپراتور، تبلیغات، سرشماره‌ی ناشناس...). */
    data object Unknown : SenderType
}

object SenderClassifier {

    /** موبایل ایران بعد از نرمال‌سازی: دقیقاً ۱۰ رقم که با 9 شروع می‌شود. */
    private val mobilePattern = Regex("^9\\d{9}$")

    fun classify(rawSender: String?): SenderType {
        // ۱. اول فهرست بانک‌ها — چون بعضی بانک‌ها (مثل بلوبانک: 0999987641)
        //    سرشماره‌ای شبیه موبایل دارند و نباید «شخصی» حساب شوند.
        BankDirectory.findBySender(rawSender)?.let { return SenderType.BankSender(it) }

        // ۲. بعد شماره‌های شخصی
        if (isPersonalMobileNumber(rawSender)) return SenderType.Personal

        return SenderType.Unknown
    }

    /**
     * true یعنی فرستنده احتمالاً یک شماره‌ی موبایل شخصی است.
     * (برگردان Kotlin از PersianMobileNumberUtil اپ قدیمی.)
     */
    fun isPersonalMobileNumber(rawAddress: String?): Boolean {
        if (rawAddress.isNullOrEmpty()) return false
        var digits = rawAddress.filter { it in '0'..'9' }
        digits = when {
            digits.startsWith("0098") -> digits.substring(4)
            // فقط وقتی طولش ۱۲ است، تا با سرشماره‌های کوتاه بانکی که با 98 شروع می‌شوند قاطی نشود
            digits.startsWith("98") && digits.length == 12 -> digits.substring(2)
            digits.startsWith("0") && digits.length == 11 -> digits.substring(1)
            else -> digits
        }
        return mobilePattern.matches(digits)
    }
}
