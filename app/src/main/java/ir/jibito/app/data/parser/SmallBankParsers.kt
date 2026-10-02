package ir.jibito.app.data.parser

import ir.jibito.app.data.parser.FlowType.DEPOSIT
import ir.jibito.app.data.parser.FlowType.WITHDRAWAL

// پارسرهای موسسه‌های اعتباری و بانک‌های کوچک‌تر (ابزارهای مشترک: ParserHelpers.kt)

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
