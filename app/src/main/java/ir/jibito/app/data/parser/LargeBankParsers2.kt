package ir.jibito.app.data.parser

import ir.jibito.app.data.parser.FlowType.DEPOSIT
import ir.jibito.app.data.parser.FlowType.WITHDRAWAL

// ادامه‌ی پارسرهای بانک‌های بزرگ (بخش اول: LargeBankParsers.kt؛ ابزارهای مشترک: ParserHelpers.kt)

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
