package ir.jibito.app.data.parser

/** نوع تراکنش. (عددها همان عددهای اپ قدیمی‌اند: ۱=واریز، ۲=برداشت) */
enum class FlowType(val code: Int) {
    DEPOSIT(1),
    WITHDRAWAL(2),
}

/** چیزی که پارسر از متن یک پیامک بیرون می‌کشد. همه‌ی مبلغ‌ها به ریال. */
data class ParsedTransaction(
    val type: FlowType,
    val amountRial: Long,
    val balanceRial: Long? = null,
)

/** هر پارسر: متن نرمال‌شده‌ی پیامک را می‌گیرد؛ اگر تراکنش نبود null برمی‌گرداند. */
fun interface SmsParser {
    fun parse(text: String): ParsedTransaction?
}

/** سقف منطقی مبلغ (مثل اپ قدیمی: کمتر از ۱۰ میلیارد ریال). */
internal const val MAX_AMOUNT_RIAL = 10_000_000_000L

internal fun digitsToLong(s: String): Long? = s.filter { it in '0'..'9' }.takeIf { it.isNotEmpty() && it.length <= 13 }?.toLong()
