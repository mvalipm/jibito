package ir.jibito.app.data.review

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.MAX_AMOUNT_DIGITS
import ir.jibito.app.data.parser.ParsedTransaction

/**
 * «قالب یادگرفته‌شده»: وقتی کاربر یک پیامک را از صندوق بررسی ثبت می‌کند،
 * اپ اسکلت آن پیامک (متن بدون عددها) و جای مبلغ/مانده را یاد می‌گیرد.
 */
data class LearnedTemplate(
    val skeleton: String,
    /** تعداد همه‌ی عددهای متن (بعد از حذف تاریخ و ساعت) */
    val numberCount: Int,
    /** کدام عدد مبلغ است (شماره بین همه‌ی عددها) */
    val amountPos: Int,
    val balancePos: Int?,
    val typeMode: Int,
) {
    companion object {
        const val TYPE_DEPOSIT = 1
        const val TYPE_WITHDRAWAL = 2
        /** نوع از روی علامت کنار مبلغ (+/−) تعیین می‌شود */
        const val TYPE_BY_SIGN = 3
    }
}

object TemplateMatcher {

    /** حداقل شباهت کلمه‌های دو پیامک تا «هم‌قالب» حساب شوند */
    private const val MIN_SIMILARITY = 0.7

    /** کلمه‌هایی که نوع تراکنش (واریز یا برداشت) را نشان می‌دهند */
    private val typeWords = listOf(
        "واریز", "برداشت", "کسر", "خرید", "پرداخت", "انتقال", "حواله", "سود", "برگشت", "اصلاحیه", "وصول", "شارژ",
    )

    private fun typeWordsIn(text: String): Set<String> = typeWords.filterTo(HashSet()) { text.contains(it) }

    private val numberPattern = Regex("\\d[\\d,،]*\\d|\\d")
    private val wordSplit = Regex("[\\s:،,.\\-_/()+]+")

    /** متن بدون عددها، تاریخ و ساعت — مثلاً «خرید از # مبلغ # مانده # ‹D›» */
    fun skeleton(text: String): String =
        ReviewDetector.withoutDateTime(text).replace(numberPattern, "#").replace(Regex("\\s+"), " ").trim()

    private fun words(skeleton: String): Set<String> =
        skeleton.split(wordSplit).filter { it.isNotBlank() && it != "#" }.toSet()

    fun similarity(a: String, b: String): Double {
        val wa = words(a)
        val wb = words(b)
        if (wa.isEmpty() && wb.isEmpty()) return 1.0
        val union = (wa union wb).size
        return if (union == 0) 0.0 else (wa intersect wb).size.toDouble() / union
    }

    /** از تأیید کاربر یک قالب می‌سازد. */
    fun learn(text: String, amount: NumberToken, balance: NumberToken?, chosenType: FlowType): LearnedTemplate {
        val sign = ReviewDetector.signOf(text, amount.raw)
        val mode = when {
            sign != null && sign == chosenType -> LearnedTemplate.TYPE_BY_SIGN
            chosenType == FlowType.DEPOSIT -> LearnedTemplate.TYPE_DEPOSIT
            else -> LearnedTemplate.TYPE_WITHDRAWAL
        }
        return LearnedTemplate(
            skeleton = skeleton(text),
            numberCount = ReviewDetector.allNumberMatches(text).size,
            amountPos = amount.pos,
            balancePos = balance?.pos,
            typeMode = mode,
        )
    }

    /** اگر پیامک با یکی از قالب‌ها جور بود، تراکنشش را برمی‌گرداند. متن باید نرمال شده باشد. */
    fun match(text: String, templates: List<LearnedTemplate>): ParsedTransaction? {
        if (templates.isEmpty()) return null
        val matches = ReviewDetector.allNumberMatches(text)
        val sk = skeleton(text)
        val words = typeWordsIn(sk)
        val template = templates
            .filter { it.numberCount == matches.size && it.amountPos < matches.size }
            // کلمه‌های نوع تراکنش باید یکی باشند: قالبی که از «برداشت» یاد گرفته شده روی «واریز» اعمال نشود
            // (دو پیامکی که فقط در همین یک کلمه فرق دارند، شباهتشان از آستانه بیشتر است)
            .filter { typeWordsIn(it.skeleton) == words }
            .maxByOrNull { similarity(sk, it.skeleton) }
            ?.takeIf { similarity(sk, it.skeleton) >= MIN_SIMILARITY }
            ?: return null

        fun valueAt(pos: Int): Long? =
            matches.getOrNull(pos)?.value?.filter { it in '0'..'9' }?.takeIf { it.length in 1..MAX_AMOUNT_DIGITS }?.toLong()

        val amountRaw = matches[template.amountPos].value
        val amount = valueAt(template.amountPos)?.takeIf { it > 0 } ?: return null
        val balance = template.balancePos?.let { valueAt(it) }
        val type = when (template.typeMode) {
            LearnedTemplate.TYPE_DEPOSIT -> FlowType.DEPOSIT
            LearnedTemplate.TYPE_WITHDRAWAL -> FlowType.WITHDRAWAL
            else -> ReviewDetector.signOf(text, amountRaw) ?: return null
        }
        val factor = if (text.contains("تومان") && !text.contains("ریال")) 10 else 1
        val amountRial = amount * factor
        return ParsedTransaction(type, amountRial, balance?.times(factor))
    }
}
