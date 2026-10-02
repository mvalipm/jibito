package ir.jibito.app.data.parser

/**
 * پارسر عمومیِ «هوشمند» — برگردان Kotlin از ParseSmart اپ قدیمی.
 * برای هر بانکی کار می‌کند و وقتی پارسر اختصاصی بانک جواب نداد، از این استفاده می‌شود.
 *
 * ایده:
 * ۱. نوع تراکنش با «امتیاز» کلمه‌ها: «واریز» مثبت، «برداشت/خرید» منفی (کلمه‌های نیمه‌ی اول متن وزن بیشتری دارند).
 * ۲. عدد با علامت (مثل 8,150,000- یا +1,000,000) هم نوع را قطعی می‌کند هم مبلغ را.
 * ۳. اولین عدد سه‌رقم‌سه‌رقم = مبلغ، آخری = مانده.
 */
object SmartParser : SmsParser {

    private val flowWeights = mapOf(
        "واریز" to 3, "سود" to 3, "انتقال به" to 3, "وصول" to 2,
        "اصلاحیه" to 1, "انتقالی" to 1, "به" to 1,
        "برداشت" to -3, "خرید" to -3, "انتقال از" to -3, "پرداخت" to -2,
        "نقدی" to -1, "انتقال" to -1, "از" to -1,
    ).mapKeys { (word, _) -> Regex("(^|[^\\p{L}])${Regex.escape(word)}($|[^\\p{L}])") }

    private const val VALID_VALUE = "([1-9]\\d?\\d?([,،]\\d\\d\\d)+)"
    private val reValue = Regex("(?:^|[^\\d,،])$VALID_VALUE(?=$|[^\\d,،])")
    private val reLeftSigned = Regex("(?:^|[^\\d])([+\\-])\\s?$VALID_VALUE(?=$|[^\\d,،])")
    private val reRightSigned = Regex("(?:^|[^\\d,،])$VALID_VALUE\\s?([+\\-])(?=$|[^\\d])")

    private val amountKeys = listOf("مبلغ", "انتقال", "برداشت", "واریز")
    private val balanceKeys = listOf("مانده", "موجودی")

    override fun parse(text: String): ParsedTransaction? {
        // ۱. امتیاز کلمه‌ها
        var score = 0
        var hasFlowWord = false
        val half = text.length / 2
        for ((regex, weight) in flowWeights) {
            for (m in regex.findAll(text)) {
                hasFlowWord = true
                score += weight + if (m.range.first <= half) (if (weight >= 0) 1 else -1) else 0
            }
        }
        var type = if (score >= 0) FlowType.DEPOSIT else FlowType.WITHDRAWAL

        // ۲. اعداد سه‌رقم‌سه‌رقم: اولی مبلغ، بعدی‌ها مانده
        var amount: Long? = null
        var balance: Long? = null
        for (m in reValue.findAll(text)) {
            val v = digitsToLong(m.groupValues[1]) ?: continue
            if (amount == null) amount = v else balance = v
        }

        // ۳. عدد علامت‌دار (اولویت با علامت سمت راست، مثل اپ قدیمی)
        var signed = false
        val right = reRightSigned.find(text)
        val left = reLeftSigned.find(text)
        when {
            right != null -> {
                signed = true
                type = if (right.groupValues[3] == "-") FlowType.WITHDRAWAL else FlowType.DEPOSIT
                amount = digitsToLong(right.groupValues[1])
            }
            left != null -> {
                signed = true
                type = if (left.groupValues[1] == "-") FlowType.WITHDRAWAL else FlowType.DEPOSIT
                amount = digitsToLong(left.groupValues[2])
            }
        }

        // ۴. اگر عدد سه‌رقمی پیدا نشد، دنبال «مبلغ: 250000» و «مانده: ...» بگرد
        if (amount == null) amount = findAfterKey(text, amountKeys)
        if (balance == null) balance = findAfterKey(text, balanceKeys)

        val finalAmount = amount ?: return null
        if (finalAmount <= 0 || finalAmount >= MAX_AMOUNT_RIAL) return null
        // مانده‌ی برابر با مبلغ معمولاً تکرار اشتباهی همان عدد است — مگر صریحاً بعد از «مانده/موجودی» آمده باشد
        // (مثلاً اولین واریز به حساب بلوبانک: «۱,۰۰۰,۰۰۰ ریال به حساب شما نشست. موجودی: ۱,۰۰۰,۰۰۰ ریال»)
        if (balance == finalAmount && !signed && BalanceFinder.find(text) != finalAmount) balance = null

        // محافظ در برابر پیامک‌های غیرتراکنشی: یا علامت داشته باشد، یا مانده، یا حداقل کلمه‌ی نوع تراکنش + «مبلغ»
        val trustworthy = signed || balance != null || (hasFlowWord && text.contains("مبلغ"))
        if (!trustworthy) return null

        return ParsedTransaction(type, finalAmount, balance)
    }

    private fun findAfterKey(text: String, keys: List<String>): Long? {
        for (key in keys) {
            val m = Regex("$key[\\s:]*([\\d,،]+)($|[^/:,\\d])").find(text) ?: continue
            digitsToLong(m.groupValues[1])?.let { return it }
        }
        return null
    }
}
