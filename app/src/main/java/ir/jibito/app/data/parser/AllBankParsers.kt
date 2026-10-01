package ir.jibito.app.data.parser

import ir.jibito.app.data.parser.FlowType.DEPOSIT
import ir.jibito.app.data.parser.FlowType.WITHDRAWAL

/*
 * برگردان Kotlin همه‌ی پارسرهای اختصاصی اپ قدیمی (پکیج parsers_all).
 *
 * چه چیزی عمداً فرق دارد:
 * - تاریخ و ساعت دیگر از متن خوانده نمی‌شود (زمان رسیدن پیامک استفاده می‌شود)، پس هر کدی که فقط
 *   تاریخ/ساعت/شرح/شماره‌حساب را در می‌آورد حذف شده است.
 * - پیامک‌های «گردش حساب» که چند تراکنش دارند، فعلاً فقط اولین تراکنش را می‌دهند.
 * - متن از قبل نرمال شده است (SmsTextNormalizer)، پس نسخه‌های «ﺑﺮﺩﺍﺷﺖ»-مانند کلمه‌ها لازم نیستند.
 * هر جا اپ قدیمی با خطا (Exception) آن خط را نادیده می‌گرفت، این‌جا هم همان خط نادیده گرفته می‌شود.
 */

// ---------------------------------------------------------------- ابزارهای مشترک

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
        ?.takeIf { it.length <= 13 }?.toLong()

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

private val COLON = Regex(":")
private val SPACE_OR_COLON = Regex("[\\s|:]")
private val SPACES_OR_COLONS = Regex("[\\s:]+")

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

// ---------------------------------------------------------------- بانک‌ها و موسسه‌ها

object AfzalToosParser : SmsParser {
    override fun parse(text: String) = eachLine(rawLines(text)) { l ->
        when {
            l.startsWith("واریز به") -> type = DEPOSIT
            l.startsWith("برداشت مبلغ") -> { type = WITHDRAWAL; amount = num(l) }
            l.startsWith("از") -> Unit
            l.startsWith("مبلغ") -> amount = num(l)
            l.startsWith("موجودی") -> balance = num(l)
        }
    }
}

object KhavarMianehParser : SmsParser {
    override fun parse(text: String): ParsedTransaction? {
        val l = rawLines(text)
        val t = l.getOrNull(1) ?: return null
        val type = when {
            t.startsWith("برداشت") -> WITHDRAWAL
            t.startsWith("واریز") -> DEPOSIT
            else -> return null
        }
        val amount = l.getOrNull(3)?.takeIf { it.startsWith("مبلغ") }?.let(::num)
        val balance = l.getOrNull(6)?.takeIf { it.startsWith("مانده") }?.let(::num)
        return Kw.valid(type, amount, balance)
    }
}

object KosarParser : SmsParser {
    override fun parse(text: String): ParsedTransaction? {
        val l = packedLines(text)
        val t = l.getOrNull(1) ?: return null
        val type = when {
            t.contains("برداشت") -> WITHDRAWAL
            t.contains("واریز") -> DEPOSIT
            else -> null
        }
        val amount = l.getOrNull(2)?.takeIf { it.contains("مبلغ") }?.let(::num)
        val balance = l.getOrNull(4)?.takeIf { it.contains("موجودی") }?.let(::num)
        return Kw.valid(type, amount, balance)
    }
}

object SinaParser : SmsParser {
    override fun parse(text: String) = eachLine(rawLines(text)) { l ->
        when {
            l.contains("واریز") -> type = DEPOSIT
            l.contains("برداشت") -> type = WITHDRAWAL
            l.contains("مبلغ") -> amount = num(l)
            l.contains("زمان") -> Unit
            l.contains("مانده") -> balance = num(l)
        }
    }
}

object AskariehParser : SmsParser {
    override fun parse(text: String): ParsedTransaction? {
        val t = text.split(Regex("[\\s|\n:]+"))
        val acc = Acc()
        for (i in t.indices) {
            when (t[i]) {
                "از", "به" -> Unit
                "برداشت" -> { acc.type = WITHDRAWAL; acc.amount = num(t.getOrNull(i + 1)) }
                "واریز" -> { acc.type = DEPOSIT; acc.amount = num(t.getOrNull(i + 1)) }
                "مانده" -> acc.balance = num(t.getOrNull(i + 1))
            }
        }
        return acc.result()
    }
}

object MizanParser : SmsParser {
    override fun parse(text: String) = eachLine(rawLines(text)) { l ->
        when {
            l.contains("واریز") -> type = DEPOSIT
            l.contains("برداشت") -> type = WITHDRAWAL
            l.contains("مبلغ") -> amount = num(l)
            l.contains("موجودی") -> balance = num(l)
        }
    }
}

object ArmanParser : SmsParser {
    override fun parse(text: String) = eachLine(rawLines(text)) { l ->
        val (k, v) = kv(l, SPACE_OR_COLON) ?: return@eachLine
        when (k) {
            "برداشت" -> type = WITHDRAWAL
            "واریز" -> type = DEPOSIT
            "مبلغ" -> amount = num(v)
            "موجودی" -> balance = num(v)
        }
    }
}

object MehrImamRezaParser : SmsParser {
    override fun parse(text: String) = eachLine(rawLines(text)) { l ->
        val (k, v) = kv(l, Regex("[:|\\s]+")) ?: return@eachLine
        when {
            k.contains("برداشت") -> type = WITHDRAWAL
            k == "واریز" -> type = DEPOSIT
            k == "مبلغ" -> amount = num(v)
            k == "موجودی" -> balance = num(v)
        }
    }
}

object MehrIranParser : SmsParser {
    override fun parse(text: String) = eachLine(rawLines(text)) { l ->
        val (k, v) = kv(l, COLON) ?: return@eachLine
        when (k) {
            "مبلغ" -> {
                amount = num(v)
                signType(l)?.let { type = it }
            }
            "مانده" -> balance = num(v)
        }
    }
}

object GardeshgariParser : SmsParser {
    override fun parse(text: String): ParsedTransaction? {
        val l = text.split(Regex("[\n+]"))
        val t = l.getOrNull(2) ?: return null
        val type = when {
            t.contains("برداشت") -> WITHDRAWAL
            t.contains("واریز") -> DEPOSIT
            else -> return null
        }
        val i = if (t.trim().endsWith(":")) 3 else 2
        val amount = l.getOrNull(i + 1)?.takeIf { it.contains("مبلغ") }?.let(::num)
        val balance = l.getOrNull(i + 3)?.takeIf { it.contains("موجودی") }?.let(::num)
        return Kw.valid(type, amount, balance)
    }
}

object EtebariToseehParser : SmsParser {
    override fun parse(text: String) = eachLine(rawLines(text)) { l ->
        val (k, _) = kv(l, Regex("\\s+")) ?: return@eachLine
        when {
            k == "برداشت" -> type = WITHDRAWAL
            k == "واریز" || k.contains("سود") -> type = DEPOSIT
            k == "مبلغ" -> amount = num(l)
            k == "مانده" -> balance = num(l)
        }
    }
}

object SamenParser : SmsParser {
    override fun parse(text: String): ParsedTransaction? {
        val l = packedLines(text)
        val i = 1
        var type: FlowType? = null
        val head = l.getOrNull(i) ?: return null
        if (head.startsWith("واریز") || head.startsWith("هبه")) type = DEPOSIT
        val second = l.getOrNull(i + 1)
        when {
            second == null -> Unit
            second.contains("برداشت") -> type = WITHDRAWAL
            second.contains("واریز") -> type = DEPOSIT
        }
        val amount = l.getOrNull(i + 3)?.takeIf { it.contains("مبلغ") }?.let(::num)
        val balance = l.getOrNull(i + 5)?.takeIf { it.contains("موجودی") }?.let(::num)
        return Kw.valid(type, amount, balance)
    }
}

/** پارسر مشترک پک (تجارت الکترونیک پارسیان) و پارسیان */
private fun peccoLike(text: String, amountKeyExact: Boolean, balanceWord: String) = eachLine(rawLines(text)) { l ->
    val (k, _) = kv(l, COLON) ?: return@eachLine
    when {
        Kw.withdrawal.dropLast(1).any { l.contains(it) } -> type = WITHDRAWAL // برداشت، خرید، پرداخت، انتقال از
        listOf("واریز", "اصلاحیه", "وصول", "انتقال به").any { l.contains(it) } -> type = DEPOSIT
        (if (amountKeyExact) k == "مبلغ" else l.contains("مبلغ")) -> amount = num(l)
        l.contains("زمان") && !amountKeyExact -> Unit
        (if (amountKeyExact) k == balanceWord else l.contains(balanceWord)) -> balance = num(l)
    }
}

object PeccoParser : SmsParser {
    override fun parse(text: String) = peccoLike(text, amountKeyExact = true, balanceWord = "موجودی")
}

object ParsianParser : SmsParser {
    override fun parse(text: String) = peccoLike(text, amountKeyExact = false, balanceWord = "مانده")
}

object ToseehSaderatParser : SmsParser {
    override fun parse(text: String): ParsedTransaction? {
        val l = packedLines(text)
        val t = l.getOrNull(1) ?: return null
        val acc = Acc()
        acc.type = when {
            t.contains("واریز") || t.contains("وصول") -> DEPOSIT
            t.contains("برداشت") -> WITHDRAWAL
            else -> null
        }
        for (line in l.drop(3)) {
            val (k, v) = kv(line.trim(), COLON) ?: continue
            when (k) {
                "مبلغ" -> acc.amount = num(v)
                "مانده" -> acc.balance = num(v)
            }
        }
        return acc.result()
    }
}

object IranZaminParser : SmsParser {
    override fun parse(text: String) = eachLine(rawLines(text)) { l ->
        when {
            l.startsWith("واریز") && type == null -> type = DEPOSIT
            l.startsWith("برداشت") && type == null -> type = WITHDRAWAL
            l.startsWith("مبلغ") -> amount = num(l)
            l.startsWith("در") || l.startsWith("تاریخ") || l.startsWith("ساعت") -> Unit
            l.startsWith("مانده") -> balance = num(l)
        }
    }
}

object HekmatParser : SmsParser {
    override fun parse(text: String) = eachLine(rawLines(text)) { line ->
        val (k, v) = kv(line.replace("مانده", "مانده "), SPACE_OR_COLON) ?: return@eachLine
        when (k) {
            "برداشت" -> type = WITHDRAWAL
            "واریز" -> type = DEPOSIT
            "مبلغ" -> amount = num(v)
            "مانده" -> balance = num(v)
        }
    }
}

object SarmayehParser : SmsParser {
    override fun parse(text: String): ParsedTransaction? {
        val l = rawLines(text)
        val t = l.getOrNull(1)?.trim() ?: return null
        val acc = Acc()
        acc.type = when {
            t.contains("واریز") || t.contains("سود") -> DEPOSIT
            t.contains("برداشت") -> WITHDRAWAL
            else -> return null
        }
        for (line in l.drop(2)) {
            val trimmed = line.trim()
            val (k, _) = kv(trimmed, SPACES_OR_COLONS) ?: continue
            when {
                trimmed.contains("مبلغ") -> acc.amount = num(trimmed)
                k == "مانده" -> acc.balance = num(trimmed)
            }
        }
        return acc.result()
    }
}

object AnsarParser : SmsParser {
    override fun parse(text: String) = eachLine(rawLines(text)) { original ->
        var l = original
        if (l.startsWith("مبلغ") && l.length > 4 && l[4] != ' ') l = "مبلغ " + l.substring(4)
        if (l.endsWith("ریال") && !l.startsWith("مبلغ") && !l.startsWith("مانده")) l = "مبلغ $l"
        if (l.startsWith("مانده") && !l.startsWith("مانده ")) l = l.replaceFirst("مانده", "مانده ")
        val (k, v) = kv(l, Regex(":|\\s")) ?: return@eachLine
        when {
            k == "برداشت" && type == null -> type = WITHDRAWAL
            (k == "واریز" || k == "سود") && type == null -> type = DEPOSIT
            k == "مبلغ" -> amount = num(v)
            k == "مانده" -> balance = num(v)
        }
    }
}

object KarafarinParser : SmsParser {
    override fun parse(text: String) = eachLine(rawLines(text)) { l ->
        val (k, v) = kv(l, COLON) ?: return@eachLine
        when {
            k.contains("کارت") -> Unit
            k == "برداشت" || k.contains("خرید") || k.contains("پرداخت") -> { type = WITHDRAWAL; amount = num(v) }
            k == "واریز" -> { type = DEPOSIT; amount = num(v) }
            k == "زمان" -> Unit
            k.contains("مانده") -> balance = num(v)
            k == "حساب" -> Unit
            k == "مبلغ" -> { type = if (v.contains("-")) WITHDRAWAL else DEPOSIT; amount = num(v) }
        }
    }
}

object PostBankParser : SmsParser {
    override fun parse(text: String): ParsedTransaction? {
        val l = packedLines(text)
        val first = l.firstOrNull() ?: return null
        val i = if (first.contains("پست بانک")) 1 else 0
        val t = l.getOrNull(i)?.trim() ?: return null
        val acc = Acc()
        acc.type = when {
            t.contains("برداشت") -> WITHDRAWAL
            t.contains("واریز") -> DEPOSIT
            else -> null
        }
        for (j in (i + 1) until (l.size - 2)) {
            val (k, v) = kv(l[j], COLON) ?: continue
            when (k) {
                "مبلغ" -> acc.amount = num(v)
                "مانده" -> acc.balance = num(v)
            }
        }
        return acc.result()
    }
}

object SepahParser : SmsParser {
    override fun parse(text: String) = firstOf({ parse1(text) }, { parse2(text) })

    private fun parse1(text: String): ParsedTransaction? {
        val l = packedLines(text)
        val t = l.getOrNull(1) ?: return null
        val acc = Acc()
        acc.type = when {
            t.contains("واریز") -> DEPOSIT
            t.contains("برداشت") -> WITHDRAWAL
            else -> null
        }
        val tokens = t.split(Regex(" +"))
        val at = tokens.indexOf("مبلغ")
        if (at >= 0) acc.amount = num(tokens.getOrNull(at + 1))
        for (j in 1 until l.size - 1) {
            if (l[j].split(":", limit = 2)[0].trim() == "مانده") acc.balance = num(l[j])
        }
        return acc.result()
    }

    private fun parse2(text: String): ParsedTransaction? {
        val l = packedLines(text)
        val t = l.getOrNull(1) ?: return null
        val balance = l.getOrNull(3)?.takeIf { it.contains("مانده") }?.let(::num)
        return Kw.valid(signType(t), num(t), balance)
    }
}

object AyandehParser : SmsParser {
    override fun parse(text: String): ParsedTransaction? {
        if (text.contains("Ayandeh")) return firstStatementEntry(text)
        val l = packedLines(text)
        val t = l.getOrNull(1) ?: return null
        val acc = Acc()
        var i = 1
        when {
            t.contains("واریز") || t.contains("وصول") -> acc.type = DEPOSIT
            t.contains("برداشت") || t.contains("درخواست کارت") -> acc.type = WITHDRAWAL
            t.contains("حواله") -> { acc.type = DEPOSIT; i = 0 }
        }
        for (line in l.drop(i + 2)) {
            val (k, v) = kv(line.trim(), COLON) ?: continue
            when (k) {
                "مبلغ" -> acc.amount = num(v)
                "مانده" -> acc.balance = num(v)
            }
        }
        return acc.result()
    }
}

/** ParseGeneric اپ قدیمی — بانک رفاه همین را استفاده می‌کرد */
object GenericParser : SmsParser {
    private val dateTime = Regex("((\\d{2}){1,2})?([/]?\\d{2}){2}[-\\s]?\\d{2}:\\d{2}")
    private val plusAmount = Regex("([\\d]{1,3})(,[\\d]{3})+\\+")
    private val minusAmount = Regex("([\\d]{1,3})(,[\\d]{3})+-")

    override fun parse(text: String) = eachLine(rawLines(text)) { original ->
        var l = original
        if (dateTime.matches(l)) l = "تاریخ:$l"
        if (plusAmount.matches(l)) l = "واریز:$l"
        if (minusAmount.matches(l)) l = "برداشت:$l"
        val (k, v) = kv(l, COLON) ?: return@eachLine
        when {
            // اصلاح نسبت به اپ قدیمی: خط تاریخ (مثل «0709-12:30») به‌خاطر «-» برداشت حساب می‌شد
            k == "تاریخ" -> Unit
            v.contains("+") || k == "واریز" -> num(v)?.let { type = DEPOSIT; amount = it }
            v.contains("-") || k == "برداشت" -> num(v)?.let { type = WITHDRAWAL; amount = it }
            k.startsWith("مانده") || k.startsWith("موجودی") -> balance = num(v)
        }
    }
}

object GhavaminParser : SmsParser {
    override fun parse(text: String) = firstOf({ parse1(text) }, { parse2(text) })

    private fun parse1(text: String): ParsedTransaction? {
        val l = packedLines(text)
        val i = if (l.getOrNull(2)?.contains("شاخص") == true) 3 else 2
        val line = l.getOrNull(i) ?: return null
        if (!line.contains("+") && !line.contains("-")) return null
        val balance = if (l.getOrNull(i + 2)?.contains("مانده") == true) num(l.getOrNull(i + 3)) else null
        return Kw.valid(signType(line), num(line), balance)
    }

    private fun parse2(text: String): ParsedTransaction? {
        val l = packedLines(text)
        var i = if (l.getOrNull(3)?.contains("شاخص") == true) 4 else 3
        var amount: Long? = null
        if (l.getOrNull(i)?.contains("مبلغ") == true) {
            i++
            amount = num(l.getOrNull(i))
        }
        var j = i + 1
        var type: FlowType? = null
        if (l.getOrNull(j)?.contains("حساب") == true) {
            j = i + 2
            type = when {
                l.getOrNull(j)?.contains("واریز") == true -> DEPOSIT
                l.getOrNull(j)?.contains("برداشت") == true -> WITHDRAWAL
                else -> null
            }
        }
        val balance = if (l.getOrNull(j + 1)?.contains("مانده") == true) num(l.getOrNull(j + 2)) else null
        return Kw.valid(type, amount, balance)
    }
}

object SamanParser : SmsParser {
    override fun parse(text: String): ParsedTransaction? = when {
        text.contains("تاریخ") && (text.contains("ساعت") || text.contains("زمان")) -> keyLines(text, Regex("\\s+"))
        text.contains("هوشمند") -> keyLines(text, Regex(":+"), containsKeys = true)
        else -> positional(text)
    }

    private fun keyLines(text: String, sep: Regex, containsKeys: Boolean = false) = eachLine(rawLines(text)) { l ->
        val (k, _) = kv(l, sep) ?: return@eachLine
        when {
            (if (containsKeys) k.contains("برداشت") else k == "برداشت") -> type = WITHDRAWAL
            (if (containsKeys) k.contains("واریز") else k == "واریز") || k.contains("سود") -> type = DEPOSIT
            k == "مبلغ" -> amount = num(l)
            k == "مانده" -> balance = num(l)
        }
    }

    private fun positional(text: String): ParsedTransaction? {
        val l = packedLines(text)
        val t = l.getOrNull(1) ?: return null
        val type = when {
            t.contains("برداشت") -> WITHDRAWAL
            t.contains("واریز") || t.contains("سود") -> DEPOSIT
            else -> null
        }
        val amount = if (t.contains("مبلغ")) num(t) else null
        val balance = l.getOrNull(3)?.takeIf { it.contains("مانده") }?.let(::num)
        return Kw.valid(type, amount, balance)
    }
}

object MaskanParser : SmsParser {
    private val withdrawalWords = listOf("برداشت", "خرید", "انتقال از", "پرداخت", "حواله قسط", "انتقال")
    private val depositWords = listOf("واریز", "انتقال به")

    private fun typeOf(line: String): FlowType? = when {
        withdrawalWords.any { line.contains(it) } -> WITHDRAWAL
        depositWords.any { line.contains(it) } -> DEPOSIT
        else -> null
    }

    private fun Acc.keyLine(line: String) {
        val (k, v) = kv(line.trim(), Regex(":|\\s")) ?: return
        when (k) {
            "مبلغ" -> amount = num(v)
            "مانده" -> balance = num(v)
        }
    }

    override fun parse(text: String) = firstOf({ parse2(text) }, { parse1(text) })

    private fun parse2(text: String): ParsedTransaction? {
        val l = rawLines(text)
        val t = l.getOrNull(1)?.trim() ?: return null
        val acc = Acc()
        acc.type = typeOf(t)
        if (acc.type != null) acc.amount = num(t)
        l.getOrNull(2)?.let { acc.keyLine(it) }
        acc.balance = num(l.getOrNull(5)) ?: return null
        return acc.result()
    }

    private fun parse1(text: String): ParsedTransaction? {
        val l = rawLines(text)
        val t = l.getOrNull(1)?.trim() ?: return null
        val acc = Acc()
        acc.type = typeOf(t)
        for (line in l.drop(2)) acc.keyLine(line)
        return acc.result()
    }
}

object ShahrParser : SmsParser {
    override fun parse(text: String) = firstOf({ parse2(text) }, { parse1(text) })

    private fun parse2(text: String) = eachLine(rawLines(text)) { l ->
        val (k, _) = kv(l, SPACE_OR_COLON) ?: return@eachLine
        when {
            k.startsWith("واریز") -> type = DEPOSIT
            k.startsWith("برداشت") -> type = WITHDRAWAL
            k == "مبلغ" -> amount = num(l)
            k == "موجودی" -> balance = num(l)
        }
    }

    private fun parse1(text: String) = eachLine(rawLines(text)) { l ->
        val k = l.split(":")[0].trim()
        when {
            k.contains("واریز") -> type = DEPOSIT
            k.contains("برداشت") -> type = WITHDRAWAL
            k.contains("مبلغ") -> amount = num(l)
            k.contains("موجودی") -> balance = num(l)
        }
    }
}

object MelliParser : SmsParser {
    private val dateLine1 = Regex("........-..:..")
    private val dateLine2 = Regex("....-..:..")
    private val plusLine = Regex("[0-9+,]+\\+")

    override fun parse(text: String): ParsedTransaction? =
        if (text.contains("Bank Melli Iran")) firstStatementEntry(text) else firstOf({ parse5(text) }, { parse1(text) })

    /** قالب «علامت‌دار»: مبلغ با + یا − ، مانده، تاریخ */
    internal fun parse5(text: String) = eachLine(rawLines(text)) { original ->
        var l = original
        if (dateLine1.matches(l) || dateLine2.matches(l)) l = "تاریخ:$l"
        if (plusLine.matches(l)) l = "واریز:$l"
        val (k, v) = kv(l, COLON) ?: return@eachLine
        when {
            k == "حساب" || k == "تاریخ" -> Unit
            k == "مانده" -> balance = num(v)
            v.contains("+") -> { type = DEPOSIT; amount = num(v) }
            v.contains("-") -> { type = WITHDRAWAL; amount = num(v) }
        }
    }

    private fun parse1(text: String): ParsedTransaction? {
        val l = packedLines(text)
        val i = if (l.firstOrNull().isNullOrEmpty()) 2 else 1
        val t = l.getOrNull(i)?.trim() ?: return null
        val acc = Acc()
        acc.type = when {
            listOf("واریز", "اصلاحیه", "وصول").any { t.contains(it) } -> DEPOSIT
            listOf("برداشت", "پرداخت", "درخواست کارت", "ساتنا").any { t.contains(it) } -> WITHDRAWAL
            else -> return null
        }
        for (j in (i + 1) until (l.size - 2)) {
            var line = l[j].trim()
            if (line.startsWith("مبلغ") && line.length > 4 && line[4] != ':') line = "مبلغ:" + line.substring(4)
            val (k, v) = kv(line, COLON) ?: continue
            when (k) {
                "مبلغ" -> acc.amount = num(v)
                "مانده" -> acc.balance = num(v)
            }
        }
        return acc.result()
    }
}

object SaderatParser : SmsParser {
    override fun parse(text: String): ParsedTransaction? {
        if (text.contains("***")) return firstStatementEntry(text)
        return firstOf(
            { MelliParser.parse5(text) },
            { signedFirstLine(text, 0) },
            { signedFirstLine(text, 1) },
            { parse1(text) },
            { MelliParser.parse(text) },
        )
    }

    /** parse3/parse4: یک خط «کلید:مبلغ» با − یا + ، بعد «حساب»، بعد «مانده» */
    private fun signedFirstLine(text: String, i: Int): ParsedTransaction? {
        val l = packedLines(text)
        var line = l.getOrNull(i) ?: return null
        if (!line.contains(":")) line = "تراکنش:$line"
        val value = line.split(":", limit = 2)[1]
        val type = if (value.contains("-")) WITHDRAWAL else DEPOSIT
        val amount = strictNum(value) ?: return null
        val balanceLine = l.getOrNull(i + 2)?.split(":", limit = 2)
        val balance = if (balanceLine?.getOrNull(0) == "مانده") strictNum(balanceLine.getOrNull(1)) else null
        return Kw.valid(type, amount, balance)
    }

    /** parse1: «مبلغ:۱۲,۰۰۰+» (علامت آخر مقدار)، «مانده:...» */
    private fun parse1(text: String): ParsedTransaction? {
        val l = rawLines(text)
        val acc = Acc()
        for (j in 1 until l.size - 1) {
            val parts = l[j].split(":")
            if (parts.size < 2) continue
            val k = parts[0].trim()
            val v = parts[1].trim()
            when (k) {
                "مبلغ" -> if (v.isNotEmpty()) {
                    acc.amount = strictNum(v.dropLast(1))
                    acc.type = if (v.last() == '+') DEPOSIT else WITHDRAWAL
                }
                "مانده" -> acc.balance = strictNum(v)
            }
        }
        return acc.result()
    }
}

object KeshavarziParser : SmsParser {
    override fun parse(text: String) = firstOf({ parse4(text) }, { parse3(text) }, { parse2(text) })

    private fun parse4(text: String): ParsedTransaction? {
        val l = rawLines(text)
        val t = l.firstOrNull() ?: return null
        val type = when {
            t.startsWith("واریز") -> DEPOSIT
            t.startsWith("برداشت") -> WITHDRAWAL
            else -> return null
        }
        val balance = l.getOrNull(1)?.takeIf { it.startsWith("مانده") }?.let(::num)
        return Kw.valid(type, num(t), balance)
    }

    private fun parse3(text: String) = eachLine(rawLines(text)) { l ->
        when {
            l.startsWith("برداشت") || l.startsWith("خرید") || l.startsWith("پرداخت") -> type = WITHDRAWAL
            l.startsWith("واریز") || l.startsWith("برگشت خرید") -> type = DEPOSIT
            l.startsWith("مبلغ") -> amount = num(l)
            l.startsWith("مانده") -> balance = num(l)
        }
    }

    /** قالب یک‌خطی: «... مبلغ X ... مانده Y ...» */
    private fun parse2(text: String): ParsedTransaction? {
        val t = text.replace('\n', ' ').split(" ").map { it.trim() }
        val acc = Acc()
        for (i in t.indices) {
            when (t[i]) {
                "مبلغ" -> acc.amount = num(t.getOrNull(i + 1))
                "مانده" -> acc.balance = num(t.getOrNull(i + 1))
                "واریز" -> acc.type = DEPOSIT
                "برداشت" -> acc.type = WITHDRAWAL
            }
        }
        return acc.result()
    }
}

object DeyParser : SmsParser {
    override fun parse(text: String): ParsedTransaction? =
        if (text.contains("Bank Day")) firstStatementEntry(text) else firstOf({ parse3(text) }, { parse4(text) }, { parse1(text) })

    private fun parse3(text: String) = eachLine(packedLines(text)) { l ->
        val (k, v) = kv(l, COLON) ?: return@eachLine
        when {
            k.contains("برداشت") -> type = WITHDRAWAL
            k.contains("واریز") -> type = DEPOSIT
            k == "مبلغ" -> amount = num(v)
            k == "موجودی" -> balance = num(v)
        }
    }

    private fun parse4(text: String): ParsedTransaction? {
        val l = packedLines(text)
        val t = l.getOrNull(1) ?: return null
        val type = when {
            t.contains("برداشت") -> WITHDRAWAL
            t.contains("واریز") -> DEPOSIT
            else -> null
        }
        val amount = l.getOrNull(2)?.takeIf { it.contains("مبلغ") }?.let(::num)
        val balance = l.getOrNull(5)?.takeIf { it.contains("مانده") }?.let(::num)
        return Kw.valid(type, amount, balance)
    }

    private fun parse1(text: String): ParsedTransaction? {
        var l = packedLines(text)
        if (l.size == 1) {
            // همه‌چیز در یک خط آمده: قبل از هر کلید یک خط جدید می‌گذاریم
            var t = text
            for (key in listOf("واریز", "مبلغ", "تاریخ", "حساب", "مانده")) t = t.replace(key, "\n$key")
            l = packedLines(t)
        }
        var i = if (l.firstOrNull().isNullOrEmpty()) 2 else 1
        val head = l.getOrNull(i)?.trim() ?: return null
        val acc = Acc()
        acc.type = when {
            listOf("واریز", "اصلاحیه", "وصول", "انتقال ساتنا").any { head.contains(it) } -> DEPOSIT
            head.contains("برداشت") || head.contains("پرداخت") -> WITHDRAWAL
            else -> return null
        }
        if (l.getOrNull(i + 1)?.startsWith("مبلغ") != true) i += 1
        for (line in l.drop(i)) {
            val (k, v) = kv(line.trim(), COLON) ?: continue
            when (k) {
                "مبلغ" -> acc.amount = num(v)
                "مانده" -> acc.balance = num(v)
            }
        }
        return acc.result()
    }
}

object TejaratParser : SmsParser {
    override fun parse(text: String): ParsedTransaction? =
        if (!text.contains("گردشها") && !text.contains("مانده بستانکار")) {
            firstOf({ parse1(text) }, { parse3(text) })
        } else {
            statement(text)
        }

    private fun parse1(text: String) = eachLine(rawLines(text)) { l ->
        val parts = l.split(":")
        if (parts.size < 2) return@eachLine
        val k = parts[0].trim()
        val v = parts[1].trim()
        when (k) {
            "برداشت" -> { type = WITHDRAWAL; amount = strictNum(v.split(" ", limit = 3).getOrNull(1)) }
            "واریز" -> { type = DEPOSIT; amount = strictNum(v.split(" ", limit = 3).getOrNull(1)) }
            "مانده" -> balance = strictNum(v.split(" ", limit = 2)[0])
        }
    }

    private fun parse3(text: String): ParsedTransaction? {
        val l = rawLines(text)
        val acc = Acc()
        for (j in 0 until l.size - 2) {
            val line = l[j].trim()
            val parts = line.split(":")
            if (parts.size < 2) continue
            when (parts[0].trim()) {
                "برداشت" -> { acc.type = WITHDRAWAL; acc.amount = num(line) }
                "واریز" -> { acc.type = DEPOSIT; acc.amount = num(line) }
                "مانده" -> acc.balance = num(line)
            }
        }
        return acc.result()
    }

    /** پیامک «گردش‌ها»: اولین ردیف از خط پنجم */
    private fun statement(text: String): ParsedTransaction? {
        val l = rawLines(text)
        val row = l.getOrNull(4) ?: return null
        val tokens = row.split(Regex(" +"))
        val type = when {
            row.contains("واریز") || tokens.getOrNull(2) == "+" -> DEPOSIT
            row.contains("برداشت") || tokens.getOrNull(2) == "-" -> WITHDRAWAL
            else -> null
        }
        return Kw.valid(type, num(tokens.getOrNull(3)), null)
    }
}

object EghtesadNovinParser : SmsParser {
    override fun parse(text: String) = firstOf(
        { parse4(text) },
        { keyLines(text, SPACES_OR_COLONS) },
        { parse2(text) },
        { keyLines(text, Regex("\\s+")) },
    )

    private fun parse4(text: String): ParsedTransaction? {
        val l = packedLines(text)
        val (k, _) = kv(l.getOrNull(1) ?: return null, Regex("[ :]+")) ?: return null
        val type = when (k) {
            "برداشت" -> WITHDRAWAL
            "واریز" -> DEPOSIT
            else -> null
        }
        val balanceLine = l.getOrNull(5)
        val balance = if (balanceLine?.split(Regex("[ :]+"), limit = 2)?.get(0) == "مانده") num(balanceLine) else null
        return Kw.valid(type, num(l.getOrNull(2)), balance)
    }

    private fun keyLines(text: String, sep: Regex) = eachLine(rawLines(text)) { l ->
        val (k, v) = kv(l, sep) ?: return@eachLine
        val firstWord = v.split(" ", limit = 2)[0]
        when {
            k.contains("واریز") -> type = DEPOSIT
            k.contains("برداشت") -> type = WITHDRAWAL
            k == "مبلغ" -> amount = num(firstWord)
            k == "مانده" -> balance = num(firstWord)
        }
    }

    private fun parse2(text: String): ParsedTransaction? {
        val l = packedLines(text).map { it.trim() }
        val t1 = l.getOrNull(1)?.split(SPACES_OR_COLONS) ?: return null
        val type = when {
            t1[0].contains("واریز") -> DEPOSIT
            t1[0].contains("برداشت") -> WITHDRAWAL
            else -> null
        }
        val t2 = l.getOrNull(2)?.split(SPACES_OR_COLONS)
        val amount = if (t2?.getOrNull(0)?.contains("مبلغ") == true) num(t2.getOrNull(1)) else null
        val t4 = l.getOrNull(4)?.split(SPACES_OR_COLONS)
        val balance = if (t4?.getOrNull(0)?.contains("مانده") == true) num(t4.getOrNull(1)) else null
        return Kw.valid(type, amount, balance)
    }
}

object MehrParser : SmsParser {
    override fun parse(text: String): ParsedTransaction? {
        val l = rawLines(text)
        if (l.getOrNull(1) == "واریز سود") parse5(l)?.let { return it }
        parse4(l)?.let { return it }
        return when {
            text.contains("مبلغ سود") -> parse3(l)
            l.firstOrNull()?.contains("موسسه مهر") == true -> parse2(l)
            else -> parse1(l)
        }
    }

    private fun parse5(l: List<String>): ParsedTransaction? {
        val amount = l.getOrNull(3)?.takeIf { it.startsWith("مبلغ") }?.let(::num)
        val balance = l.getOrNull(4)?.takeIf { it.startsWith("مانده") }?.let(::num)
        return Kw.valid(DEPOSIT, amount, balance)
    }

    private fun parse4(l: List<String>): ParsedTransaction? {
        val t = l.getOrNull(1) ?: return null
        val type = when {
            t.contains("برداشت") -> WITHDRAWAL
            t.contains("واریز") -> DEPOSIT
            else -> null
        }
        val amount = l.getOrNull(3)?.takeIf { it.contains("مبلغ") }?.let(::num)
        val balance = l.getOrNull(4)?.takeIf { it.contains("مانده") }?.let(::num)
        return Kw.valid(type, amount, balance)
    }

    private fun parse3(l: List<String>) = eachLine(l) { line ->
        val (k, v) = kv(line, Regex("[\\s|:]+")) ?: return@eachLine
        when {
            line.startsWith("مبلغ سود") -> type = DEPOSIT
            k == "مبلغ" -> amount = num(v)
            k == "مانده" -> balance = num(v)
        }
    }

    private fun parse2(l: List<String>) = eachLine(l) { line ->
        val parts = line.split(":")
        val k = parts[0].trim()
        when {
            k.contains("سود") || k.contains("واریز") -> type = DEPOSIT
            k.contains("برداشت") -> type = WITHDRAWAL
            k.contains("مبلغ") -> parts.getOrNull(1)?.let { amount = num(it) }
            k.contains("مانده") -> parts.getOrNull(1)?.let { balance = num(it) }
        }
    }

    private fun parse1(l: List<String>): ParsedTransaction? {
        val head = l.firstOrNull()?.split(" ")?.firstOrNull()?.trim() ?: return null
        val acc = Acc()
        acc.type = when (head) {
            "برداشت" -> WITHDRAWAL
            "واریز" -> DEPOSIT
            else -> null
        }
        val start = if (l.getOrNull(1)?.contains("مبلغ") == true) 1 else 2
        for (line in l.drop(start)) {
            val trimmed = line.trim()
            val k = trimmed.split(" ", limit = 2).takeIf { it.size == 2 }?.get(0)?.trim() ?: continue
            when (k) {
                "مبلغ" -> acc.amount = num(trimmed.substring(k.length))
                "مانده" -> acc.balance = num(trimmed.substring(k.length))
            }
        }
        return acc.result()
    }
}
