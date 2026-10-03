package ir.jibito.app.data.review

import ir.jibito.app.data.bank.SenderClassifier
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Transaction

/**
 * گزارش «این پیامک اشتباه خوانده شده» برای بهبود پارسرها.
 * همه‌ی رقم‌ها پوشانده می‌شوند (مثل گزارش صندوق بررسی)، ولی عددی که اپ «مبلغ» یا «مانده» خوانده
 * با برچسب [مبلغ] و [مانده] جایگزین می‌شود؛ پس معلوم است کدام عدد برداشته شده، بی‌آن‌که خود عدد فرستاده شود.
 */
object WrongReadingReport {

    /** کاربر می‌گوید چه چیزی غلط است (برچسب‌ها همان متن گزارش‌اند) */
    enum class Reason(val label: String) {
        AMOUNT("مبلغ غلطه"),
        TYPE("نوع غلطه"),
        MERCHANT("طرف حساب غلطه"),
        SHOULD_BE_TRANSFER("باید انتقال باشه"),
        OTHER("چیز دیگه"),
    }

    private val number = Regex("[0-9۰-۹٠-٩][0-9۰-۹٠-٩,٬،.]*")

    /**
     * @param sender سرشماره‌ی پیامک؛ شماره‌ی شبه‌موبایل (شخصی) پوشانده می‌شود
     * @param appVersion نسخه‌ی اپ، تا معلوم باشد گزارش مال کدام پارسر است
     */
    fun text(t: Transaction, reason: Reason? = null, sender: String? = null, appVersion: String? = null): String = buildString {
        appendLine("پیامک اشتباه خوانده‌شده — جیبیتو")
        reason?.let { appendLine("مشکل: ${it.label}") }
        t.bank?.let { appendLine("بانک: ${it.name}") }
        sender?.takeIf { it.isNotBlank() }?.let { appendLine("سرشماره: ${maskSender(it)}") }
        appVersion?.takeIf { it.isNotBlank() }?.let { appendLine("نسخه‌ی اپ: $it") }
        appendLine("اپ خواند: " + if (t.transaction.type == FlowType.DEPOSIT) "واریز" else "برداشت")
        if (t.isFailedPurchase) appendLine("اپ خواند: تراکنش ناموفق (پول برگشت)")
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

    /** سرشماره‌ی بانکی (مثل 200033 یا MofidCard) عمومی است؛ شماره‌ی موبایل شخصی نه */
    internal fun maskSender(sender: String): String =
        if (SenderClassifier.isPersonalMobileNumber(sender)) maskDigits(sender) else sender.trim()

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
