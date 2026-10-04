package ir.jibito.app.data.parser

/** کمک‌کننده‌های مشترک پارسرهای اختصاصی بانک‌ها. */
internal object Kw {
    val withdrawal = listOf("برداشت", "خرید", "پرداخت", "انتقال از", "حواله")
    val deposit = listOf("واریز", "اصلاحیه", "وصول", "انتقال به")

    fun typeOf(line: String, withdrawalWords: List<String> = withdrawal): FlowType? = when {
        withdrawalWords.any { line.contains(it) } -> FlowType.WITHDRAWAL
        deposit.any { line.contains(it) } -> FlowType.DEPOSIT
        else -> null
    }

    fun lines(text: String): List<String> = text.split(Regex("\n+")).map { it.trim() }

    fun valid(type: FlowType?, amount: Long?, balance: Long?): ParsedTransaction? {
        if (type == null || amount == null || amount <= 0) return null
        return ParsedTransaction(type, amount, balance)
    }
}

/** برگردان ParseMellat اپ قدیمی (قالب‌های «خط‌به‌خط» و «جایگاهی»). */
object MellatParser : SmsParser {

    override fun parse(text: String): ParsedTransaction? = parseByLineStart(text) ?: parseByPosition(text)

    /**
     * مبلغ در همان خط نوع: «برداشت150,000» یا «واریز: 150,000 ریال».
     * فقط وقتی کل باقی خط یک عدد است؛ «برداشت از 1234567890» (شماره حساب) مبلغ نیست.
     */
    private val amountOnTypeLine = Regex("^(?:برداشت|واریز)\\s*:?\\s*([0-9][0-9,٬]*)\\s*(?:ریال)?\\s*[-+]?$")

    /** قالب ۱: هر خط با کلیدش شروع می‌شود (واریز.../برداشت.../مبلغ.../موجودی...). */
    private fun parseByLineStart(text: String): ParsedTransaction? {
        var type: FlowType? = null
        var amount: Long? = null
        var balance: Long? = null
        for (line in Kw.lines(text)) {
            if (amount == null) amountOnTypeLine.find(line)?.let { amount = digitsToLong(it.groupValues[1]) }
            when {
                line.startsWith("واریز") -> type = FlowType.DEPOSIT
                line.startsWith("برداشت") -> type = FlowType.WITHDRAWAL
                line.startsWith("مبلغ") -> amount = digitsToLong(line)
                line.startsWith("موجودی") || line.startsWith("مانده") -> balance = BalanceFinder.find(line)
            }
        }
        return Kw.valid(type, amount, balance)
    }

    /** قالب ۲: خط اول «بانک ملت»، بعد نوع تراکنش، بعد مبلغ، بعد (شاید) موجودی. */
    private fun parseByPosition(text: String): ParsedTransaction? {
        val l = text.split("\n").map { it.trim() }
        var i = if (l.firstOrNull().isNullOrEmpty()) 2 else 1
        val typeLine = l.getOrNull(i) ?: return null
        val type = Kw.typeOf(typeLine)
        if (typeLine.contains("حواله")) i++
        val amount = l.getOrNull(i + 1)?.let(::digitsToLong)
        val balance = l.getOrNull(i + 2)?.takeIf { it.contains("موجودی") || it.contains("مانده") }?.let(BalanceFinder::find)
        return Kw.valid(type, amount, balance)
    }
}

/** برگردان ParsePasargad اپ قدیمی — برای پاسارگاد، توسعه تعاون و رسالت. */
object PasargadParser : SmsParser {

    override fun parse(text: String): ParsedTransaction? {
        val l = Kw.lines(text)
        val first = l.firstOrNull() ?: return null
        val i = if (first.contains("بانک پاسارگاد") || first.contains("قرض الحسنه رسالت")) 1 else 0
        val type = l.getOrNull(i + 1)?.let { Kw.typeOf(it, listOf("برداشت", "خرید", "پرداخت", "انتقال از")) }
        val amount = l.getOrNull(i + 2)?.takeIf { it.contains("مبلغ") }?.let(::digitsToLong)
        val balance = l.getOrNull(i + 4)?.takeIf { it.contains("موجودی") }?.let(::digitsToLong)
        return Kw.valid(type, amount, balance)
    }
}
