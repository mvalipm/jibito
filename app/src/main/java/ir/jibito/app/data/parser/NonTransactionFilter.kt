package ir.jibito.app.data.parser

/**
 * پیامک‌هایی که از سرشماره‌ی بانک می‌آیند ولی تراکنش نیستند را تشخیص می‌دهد:
 * رمز یک‌بارمصرف، کد فعال‌سازی، تبلیغ و پیام خوش‌آمد.
 *
 * چرا لازم است؟ مثلاً پیامک رمز انتقال پاسارگاد «مبلغ: 20,000,000» دارد،
 * ولی هنوز پولی جابه‌جا نشده — اگر رد نشود، یک واریز/برداشت جعلی ثبت می‌شود.
 */
object NonTransactionFilter {

    private val otpPatterns = listOf(
        Regex("رمز(\\s|‌)*(پویا|یکبار|یک‌بار|یک بار|دوم|عبور)?\\s*[:：]?\\s*\\d{4,}"),
        Regex("(کد|code|Code|CODE|OTP|otp)\\s*(فعال(\\s|‌)?سازی|تایید|تأیید|ورود|امنیتی)?\\s*[:：]?\\s*\\d{4,}"),
        Regex("(کد|رمز)\\s*(فعال(\\s|‌)?سازی|تایید|تأیید|ورود|یکبار|یک‌بار|پویا)"),
    )

    private val adWords = listOf("جشنواره", "قرعه", "جایزه", "تخفیف", "خوش آمدید", "خوش‌آمدید", "ثبت نام", "ثبت‌نام", "نصب", "دانلود", "پیشنهاد", "فرصت", "تسهیلات")

    private val urlPattern = Regex("(https?://|www\\.|\\.ir\\b|\\.com\\b)", RegexOption.IGNORE_CASE)

    private val balanceWords = listOf("مانده", "موجودی")

    fun isNotTransaction(text: String): Boolean {
        if (otpPatterns.any { it.containsMatchIn(text) }) return true
        val hasBalance = balanceWords.any { text.contains(it) }
        // تبلیغ و اطلاع‌رسانی معمولاً لینک یا این کلمه‌ها را دارند، ولی «مانده» ندارند
        if (!hasBalance && urlPattern.containsMatchIn(text)) return true
        if (!hasBalance && adWords.any { text.contains(it) }) return true
        return false
    }
}
