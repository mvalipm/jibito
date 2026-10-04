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

/**
 * سقف مبلغ نداریم (خرید ماشین و ملک هم باید ثبت شود). تنها محدودیت: عدد حداکثر ۱۳ رقم
 * (حدود ۱۰۰۰ میلیارد تومان)، تا شماره کارت ۱۶ رقمی هیچ‌وقت مبلغ خوانده نشود.
 */
internal const val MAX_AMOUNT_DIGITS = 13

internal fun digitsToLong(s: String): Long? = s.filter { it in '0'..'9' }.takeIf { it.isNotEmpty() && it.length <= MAX_AMOUNT_DIGITS }?.toLong()
