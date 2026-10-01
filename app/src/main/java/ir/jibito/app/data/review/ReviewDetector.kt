package ir.jibito.app.data.review

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.NonTransactionFilter

/** یک عدد داخل متن پیامک که کاربر می‌تواند رویش بزند. */
data class NumberToken(
    /** مقدار عددی (همان واحدی که در پیامک آمده) */
    val value: Long,
    /** شکل اصلی‌اش در متن، مثلاً «1,250,000» */
    val raw: String,
)

/** حدس اولیه برای پیامکی که پارسرها نتوانستند بخوانند. کاربر فقط تأیید یا اصلاح می‌کند. */
data class ReviewGuess(
    val numbers: List<NumberToken>,
    val amountIndex: Int?,
    val balanceIndex: Int?,
    val type: FlowType?,
    /** مبلغ‌ها به تومان نوشته شده‌اند (موقع ثبت ×۱۰ می‌شود) */
    val inToman: Boolean,
)

/**
 * تشخیص «پیامکی که شبیه تراکنش است ولی خوانده نشد» و حدس زدن مبلغ و نوعش.
 * متن ورودی باید نرمال شده باشد (SmsTextNormalizer).
 */
object ReviewDetector {

    private val financialWords = listOf(
        "برداشت", "واریز", "خرید", "انتقال", "مانده", "موجودی", "شارژ شد", "کسر", "بستانکار", "بدهکار",
    )
    private val withdrawalWords = listOf("برداشت", "خرید", "انتقال از", "کسر", "بدهکار", "پرداخت")
    private val depositWords = listOf("واریز", "انتقال به", "شارژ شد", "بستانکار", "سود")
    private val balanceWords = listOf("مانده", "موجودی")

    /** تاریخ (۱۴۰۵/۰۷/۰۹) و ساعت (۱۲:۳۰) عدد مبلغ نیستند */
    private val datePattern = Regex("\\d{2,4}[/.\\-]\\d{1,2}[/.\\-]\\d{1,2}")
    private val timePattern = Regex("\\d{1,2}:\\d{2}(:\\d{2})?")
    private val numberPattern = Regex("\\d[\\d,،]*\\d|\\d")

    /** شبیه تراکنش است؟ (کلمه‌ی مالی + یک عدد مبلغ‌مانند، و رمز/تبلیغ نیست) */
    fun isCandidate(text: String): Boolean {
        if (NonTransactionFilter.isNotTransaction(text)) return false
        if (financialWords.none { text.contains(it) }) return false
        return numbers(text).isNotEmpty()
    }

    /** عددهای مبلغ‌مانند متن، به ترتیب ظاهر شدن. */
    fun numbers(text: String): List<NumberToken> {
        val cleaned = text.replace(datePattern, " ").replace(timePattern, " ")
        return numberPattern.findAll(cleaned).mapNotNull { m ->
            val raw = m.value
            val digits = raw.filter { it in '0'..'9' }
            val grouped = raw.contains(',') || raw.contains('،')
            when {
                digits.length < 3 -> null                    // عددهای خیلی کوچک (مثل ۰۷) مبلغ نیستند
                !grouped && digits.length > 10 -> null       // شماره حساب/کارت
                digits.length > 13 -> null
                else -> NumberToken(digits.toLong(), raw)
            }
        }.filter { it.value > 0 }.toList()
    }

    fun guess(text: String): ReviewGuess {
        val nums = numbers(text)

        // مانده: اولین عددی که بعد از «مانده/موجودی» می‌آید
        val balanceIndex = balanceWords.firstNotNullOfOrNull { word ->
            val at = text.indexOf(word)
            if (at < 0) null else nums.indexOfFirst { text.indexOf(it.raw, at) >= 0 }.takeIf { it >= 0 }
        }
        // مبلغ: اولین عدد سه‌رقم‌سه‌رقم که مانده نیست؛ وگرنه اولین عددی که مانده نیست
        val amountIndex = nums.indices.firstOrNull { it != balanceIndex && nums[it].raw.contains(',') }
            ?: nums.indices.firstOrNull { it != balanceIndex }

        val signed = amountIndex?.let { i ->
            val raw = nums[i].raw
            val at = text.indexOf(raw)
            val before = text.getOrNull(at - 1)
            val after = text.getOrNull(at + raw.length)
            when {
                before == '-' || after == '-' -> FlowType.WITHDRAWAL
                before == '+' || after == '+' -> FlowType.DEPOSIT
                else -> null
            }
        }
        val type = signed ?: when {
            withdrawalWords.any { text.contains(it) } && depositWords.none { text.contains(it) } -> FlowType.WITHDRAWAL
            depositWords.any { text.contains(it) } && withdrawalWords.none { text.contains(it) } -> FlowType.DEPOSIT
            else -> null
        }
        val inToman = text.contains("تومان") && !text.contains("ریال")
        return ReviewGuess(nums, amountIndex, balanceIndex, type, inToman)
    }

    /** برای «ارسال برای بهبود اپ»: همه‌ی رقم‌ها پوشانده می‌شوند تا هیچ شماره و مبلغ واقعی بیرون نرود. */
    fun mask(text: String): String = buildString {
        for (ch in text) append(if (ch.isDigit()) '#' else ch)
    }
}
