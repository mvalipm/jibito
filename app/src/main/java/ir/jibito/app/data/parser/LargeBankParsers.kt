package ir.jibito.app.data.parser

import ir.jibito.app.data.parser.FlowType.DEPOSIT
import ir.jibito.app.data.parser.FlowType.WITHDRAWAL

// پارسرهای بانک‌های بزرگ و پارسر عمومی (ابزارهای مشترک: ParserHelpers.kt)

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
