package ir.jibito.app.data.review

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.NonTransactionFilter

/** یک عدد داخل متن پیامک که کاربر می‌تواند رویش بزند. */
data class NumberToken(
    /** مقدار عددی (همان واحدی که در پیامک آمده) */
    val value: Long,
    /** شکل اصلی‌اش در متن، مثلاً «1,250,000» */
    val raw: String,
    /** شماره‌ی این عدد بین «همه‌ی» عددهای متن (برای یادگیری قالب) */
    val pos: Int = 0,
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

    /** وزن کلمه‌ها برای حدس نوع: کلمه‌های قطعی ۲، کلمه‌های مبهم (مثل «خرید» در «برای تکمیل خرید») ۱ */
    private val withdrawalWords = mapOf("برداشت" to 2, "کسر" to 2, "بدهکار" to 2, "خرید" to 1, "انتقال از" to 1, "پرداخت" to 1)
    private val depositWords = mapOf("واریز" to 2, "شارژ شد" to 2, "بستانکار" to 2, "انتقال به" to 1, "سود" to 1)
    private val balanceWords = listOf("مانده", "موجودی")

    /** تاریخ (۱۴۰۵/۰۷/۰۹) و ساعت (۱۲:۳۰) عدد مبلغ نیستند */
    private val datePattern = Regex("\\d{2,4}[/.\\-]\\d{1,2}[/.\\-]\\d{1,2}")
    private val timePattern = Regex("\\d{1,2}:\\d{2}(:\\d{2})?")
    private val numberPattern = Regex("\\d[\\d,،]*\\d|\\d")

    /**
     * کلمه‌های قوی: تقریباً فقط در پیامک تراکنش می‌آیند (وزن ۳).
     * کلمه‌های ضعیف: در پیامک‌های دیگر هم پیدا می‌شوند (وزن ۱).
     */
    private val strongWords = listOf("برداشت", "واریز", "مانده", "موجودی", "کسر", "بستانکار", "بدهکار", "شارژ شد")
    private val weakWords = listOf("حساب", "کارت", "انتقال", "خرید", "پرداخت", "شتاب", "پایا", "ساتنا", "ریال", "تومان")

    /** کارت/حساب ماسک‌شده: 6037***1234 ، 6037-xx-1234 ، ...1234 ، **1234 */
    private val maskedCardPattern = Regex("\\d{2,6}[-\\s]?[*xX×]{2,}[-\\s]?\\d{2,4}|(\\.{2,}|[*xX×]{2,})\\d{3,4}")
    /** تاریخ شمسی کامل، مثل ۱۴۰۵/۰۷/۰۹ یا ۰۵/۰۷/۰۹ */
    private val jalaliDatePattern = Regex("(1[34])?\\d{2}/\\d{1,2}/\\d{1,2}")
    /** سرشماره‌های خدماتی ایران (۱۰۰۰… ، ۲۰۰۰… ، ۳۰۰۰… ، ۵۰۰۰… ، ۹۰۰۰…) */
    private val servicePrefixes = listOf("1000", "2000", "3000", "5000", "9000")

    /** از این امتیاز به بالا، پیامک به صندوق بررسی می‌رود. */
    const val REVIEW_THRESHOLD = 5

    /** امتیاز اضافه برای فرستنده‌ای که سرشماره‌ی رسمی یک بانک است */
    const val BANK_SENDER_BONUS = 2
    /** امتیاز اضافه برای فرستنده‌ای که قبلاً «رمز پویا/رمز دوم» فرستاده (یعنی احتمالاً بانک است) */
    const val OTP_SENDER_BONUS = 2
    /** امتیاز اضافه برای سرشماره‌ی خدماتی */
    const val SERVICE_NUMBER_BONUS = 1

    /** سرشماره (نرمال‌شده) شبیه سرشماره‌های پیامکی خدماتی است؟ */
    fun isServiceNumber(normalizedSender: String): Boolean =
        normalizedSender.length >= 6 && normalizedSender.all { it in '0'..'9' } &&
            servicePrefixes.any { normalizedSender.startsWith(it) }

    /**
     * امتیاز محتوای پیامک: چقدر شبیه پیامک تراکنش بانکی است؟
     * رمز/تبلیغ، یا متن بدون هیچ عدد مبلغ‌مانند ← صفر.
     */
    fun contentScore(text: String): Int {
        if (NonTransactionFilter.isNotTransaction(text)) return 0
        val nums = numbers(text)
        if (nums.isEmpty()) return 0
        var score = 0
        score += strongWords.count { text.contains(it) } * 3
        score += weakWords.count { text.contains(it) }
        if (nums.any { it.raw.contains(',') || it.raw.contains('،') }) score += 1   // مبلغ سه‌رقم‌سه‌رقم
        if (nums.any { signOf(text, it.raw) != null }) score += 2                    // مبلغ با + یا −
        if (maskedCardPattern.containsMatchIn(text)) score += 2                       // کارت/حساب ماسک‌شده
        if (jalaliDatePattern.containsMatchIn(text)) score += 1                       // تاریخ
        if (timePattern.containsMatchIn(text)) score += 1                             // ساعت
        return score
    }

    /** امتیاز کل = محتوا + نشانه‌های فرستنده. اگر محتوا صفر باشد (رمز/تبلیغ/بی‌عدد)، فرستنده چیزی را عوض نمی‌کند. */
    fun score(text: String, senderBonus: Int = 0): Int {
        val content = contentScore(text)
        return if (content == 0) 0 else content + senderBonus
    }

    /** شبیه تراکنش است و باید به صندوق بررسی برود؟ */
    fun isCandidate(text: String, senderBonus: Int = 0): Boolean = score(text, senderBonus) >= REVIEW_THRESHOLD

    /** متن بدون تاریخ و ساعت (تاریخ/ساعت هیچ‌وقت مبلغ نیستند) */
    internal fun withoutDateTime(text: String): String =
        text.replace(datePattern, " ‹D› ").replace(timePattern, " ‹T› ")

    /** همه‌ی عددهای متن (بعد از حذف تاریخ و ساعت)، به ترتیب — بدون هیچ فیلتری */
    internal fun allNumberMatches(text: String): List<MatchResult> =
        numberPattern.findAll(withoutDateTime(text)).toList()

    /** عددهای مبلغ‌مانند متن، به ترتیب ظاهر شدن. */
    fun numbers(text: String): List<NumberToken> =
        allNumberMatches(text).mapIndexedNotNull { index, m ->
            val raw = m.value
            val digits = raw.filter { it in '0'..'9' }
            val grouped = raw.contains(',') || raw.contains('،')
            when {
                digits.length < 3 -> null                    // عددهای خیلی کوچک (مثل ۰۷) مبلغ نیستند
                !grouped && digits.length > 10 -> null       // شماره حساب/کارت
                digits.length > 13 -> null
                else -> NumberToken(digits.toLong(), raw, index)
            }
        }.filter { it.value > 0 }

    /** علامت کنار یک عدد: «−» ← برداشت، «+» ← واریز، وگرنه null */
    fun signOf(text: String, raw: String): FlowType? {
        val at = text.indexOf(raw)
        if (at < 0) return null
        val before = text.getOrNull(at - 1)
        val after = text.getOrNull(at + raw.length)
        return when {
            before == '-' || after == '-' -> FlowType.WITHDRAWAL
            before == '+' || after == '+' -> FlowType.DEPOSIT
            else -> null
        }
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

        val signed = amountIndex?.let { signOf(text, nums[it].raw) }
        val score = depositWords.entries.sumOf { (w, v) -> if (text.contains(w)) v else 0 } -
            withdrawalWords.entries.sumOf { (w, v) -> if (text.contains(w)) v else 0 }
        val type = signed ?: when {
            score > 0 -> FlowType.DEPOSIT
            score < 0 -> FlowType.WITHDRAWAL
            else -> null // مطمئن نیستیم؛ کاربر انتخاب می‌کند
        }
        val inToman = text.contains("تومان") && !text.contains("ریال")
        return ReviewGuess(nums, amountIndex, balanceIndex, type, inToman)
    }

    /** برای «ارسال برای بهبود اپ»: همه‌ی رقم‌ها پوشانده می‌شوند تا هیچ شماره و مبلغ واقعی بیرون نرود. */
    fun mask(text: String): String = buildString {
        for (ch in text) append(if (ch.isDigit()) '#' else ch)
    }
}
