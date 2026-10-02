package ir.jibito.app.data.review

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Transaction

/**
 * گزارش «این پیامک اشتباه خوانده شده» برای بهبود پارسرها.
 * همه‌ی رقم‌ها پوشانده می‌شوند (مثل گزارش صندوق بررسی)، ولی عددی که اپ «مبلغ» یا «مانده» خوانده
 * با برچسب [مبلغ] و [مانده] جایگزین می‌شود؛ پس معلوم است کدام عدد برداشته شده، بی‌آن‌که خود عدد فرستاده شود.
 */
object WrongReadingReport {

    private val number = Regex("[0-9۰-۹٠-٩][0-9۰-۹٠-٩,٬،.]*")

    fun text(t: Transaction): String = buildString {
        appendLine("پیامک اشتباه خوانده‌شده — جیبیتو")
        t.bank?.let { appendLine("بانک: ${it.name}") }
        appendLine("اپ خواند: " + if (t.transaction.type == FlowType.DEPOSIT) "واریز" else "برداشت")
        if (t.isFailedPurchase) appendLine("اپ خواند: خرید ناموفق")
        t.merchant?.let { appendLine("طرف حساب: ${maskDigits(it)}") }
        appendLine("———")
        append(labelAndMask(t.body, t.transaction.amountRial, t.transaction.balanceRial))
    }

    /** عدد مبلغ و مانده (به ریال یا تومان) برچسب می‌خورند؛ بقیه‌ی رقم‌ها # می‌شوند */
    fun labelAndMask(body: String, amountRial: Long, balanceRial: Long?): String {
        var amountUsed = false
        var balanceUsed = false
        val labeled = number.replace(body) { m ->
            val value = parse(m.value)
            when {
                value != null && !amountUsed && matches(value, amountRial) -> { amountUsed = true; "[مبلغ]" }
                value != null && balanceRial != null && !balanceUsed && matches(value, balanceRial) -> { balanceUsed = true; "[مانده]" }
                else -> m.value
            }
        }
        return maskDigits(labeled)
    }

    private fun matches(value: Long, rial: Long) = value == rial || value * 10 == rial

    private fun parse(token: String): Long? {
        val digits = buildString {
            for (ch in token) when (ch) {
                in '0'..'9' -> append(ch)
                in '۰'..'۹' -> append('0' + (ch - '۰'))
                in '٠'..'٩' -> append('0' + (ch - '٠'))
                ',', '٬', '،' -> Unit
                else -> return null // اعشار یا چیز دیگر: مبلغ نیست
            }
        }
        return digits.takeIf { it.isNotEmpty() && it.length <= 18 }?.toLongOrNull()
    }

    private fun maskDigits(text: String): String = buildString {
        for (ch in text) append(if (ch.isDigit()) '#' else ch)
    }
}
