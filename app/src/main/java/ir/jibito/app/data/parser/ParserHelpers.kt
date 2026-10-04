package ir.jibito.app.data.parser

import ir.jibito.app.data.parser.FlowType.DEPOSIT
import ir.jibito.app.data.parser.FlowType.WITHDRAWAL

/*
 * برگردان Kotlin همه‌ی پارسرهای اختصاصی اپ قدیمی (پکیج parsers_all):
 * این فایل ابزارهای مشترک؛ پارسرها در SmallBankParsers.kt و LargeBankParsers.kt.
 *
 * چه چیزی عمداً فرق دارد:
 * - تاریخ و ساعت دیگر از متن خوانده نمی‌شود (زمان رسیدن پیامک استفاده می‌شود)، پس هر کدی که فقط
 *   تاریخ/ساعت/شرح/شماره‌حساب را در می‌آورد حذف شده است.
 * - پیامک‌های «گردش حساب» که چند تراکنش دارند، فعلاً فقط اولین تراکنش را می‌دهند.
 * - متن از قبل نرمال شده است (SmsTextNormalizer)، پس نسخه‌های «ﺑﺮﺩﺍﺷﺖ»-مانند کلمه‌ها لازم نیستند.
 * هر جا اپ قدیمی با خطا (Exception) آن خط را نادیده می‌گرفت، این‌جا هم همان خط نادیده گرفته می‌شود.
 */


/** جمع‌کننده‌ی نتیجه — معادل mFlowType / mAmount / mRemain اپ قدیمی */
internal class Acc {
    var type: FlowType? = null
    var amount: Long? = null
    var balance: Long? = null
    fun result(): ParsedTransaction? = Kw.valid(type, amount, balance)
}

/** معادل Long.valueOf(s.replaceAll("[^0-9]", "")) — اگر عددی نبود null */
internal fun num(s: String?): Long? = s?.let(::digitsToLong)

/** معادل Long.valueOf(...) روی متنی که باید «فقط عدد» باشد (بعد از حذف , + − و فاصله) */
internal fun strictNum(s: String?): Long? =
    s?.replace(",", "")?.replace("+", "")?.replace("-", "")?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        ?.takeIf { it.length <= MAX_AMOUNT_DIGITS }?.toLong()

/** خط‌ها مثل split("\n") جاوا (خط‌های خالی هم می‌مانند) */
internal fun rawLines(text: String): List<String> = text.split("\n")

/** خط‌ها مثل split("\n+") جاوا */
internal fun packedLines(text: String): List<String> = text.split(Regex("\n+"))

/** هر خط جدا (معادل parseLine)؛ خط‌ها trim شده‌اند */
internal inline fun eachLine(lines: List<String>, block: Acc.(String) -> Unit): ParsedTransaction? {
    val acc = Acc()
    for (line in lines) acc.block(line.trim())
    return acc.result()
}

/** «کلید + مقدار» با حداکثر دو تکه؛ اگر جداکننده نبود null (اپ قدیمی هم آن خط را نادیده می‌گرفت) */
internal fun kv(line: String, separator: Regex): Pair<String, String>? {
    val parts = line.split(separator, limit = 2)
    return if (parts.size < 2) null else parts[0].trim() to parts[1].trim()
}

internal val COLON = Regex(":")
internal val SPACE_OR_COLON = Regex("[\\s|:]")
internal val SPACES_OR_COLONS = Regex("[\\s:]+")

/** اولین پارسری که جواب داد */
internal fun firstOf(vararg attempts: () -> ParsedTransaction?): ParsedTransaction? {
    for (attempt in attempts) attempt()?.let { return it }
    return null
}

/** نوع از روی + / − در یک خط */
internal fun signType(line: String?): FlowType? = when {
    line == null -> null
    line.contains("+") -> DEPOSIT
    line.contains("-") -> WITHDRAWAL
    else -> null
}

/** پیامک «گردش حساب»: خط دوم تاریخ/شرح، خط بعدی مبلغ با +/− ؛ فقط اولین تراکنش */
internal fun firstStatementEntry(text: String, amountLine: Int = 3): ParsedTransaction? {
    val l = packedLines(text)
    val line = l.getOrNull(amountLine) ?: return null
    return Kw.valid(signType(line), num(line), null)
}
