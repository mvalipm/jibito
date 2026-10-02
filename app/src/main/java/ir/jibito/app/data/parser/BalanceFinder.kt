package ir.jibito.app.data.parser

/**
 * «مانده/موجودی» را از متن (نرمال‌شده) پیدا می‌کند — مستقل از قالب بانک.
 *
 * چرا جدا؟ قبلاً همه‌ی رقم‌های یک خط به هم چسبانده می‌شد؛ اگر در همان خطِ «مانده»
 * شماره حساب یا تاریخ هم بود («مانده حساب ۱۲۳۴...: ۲,۰۰۰,۰۰۰»)، عدد بی‌معنی می‌شد و مانده گم می‌شد.
 *
 * قانون: بعد از کلمه‌ی «مانده» یا «موجودی» (تا آخر همان خط، و اگر خالی بود خط بعد):
 * ۱) اولین عدد سه‌رقم‌سه‌رقم (با ویرگول)؛ ۲) وگرنه اولین عددی که تاریخ/ساعت/شماره حساب نیست.
 */
object BalanceFinder {

    private val keys = listOf("مانده", "موجودی")
    private val datePattern = Regex("\\d{2,4}[/.\\-]\\d{1,2}[/.\\-]\\d{1,2}")
    private val timePattern = Regex("\\d{1,2}:\\d{2}(:\\d{2})?")
    private val masked = Regex("\\d*[*xX×.]{2,}\\d*")
    private val number = Regex("\\d[\\d,،]*\\d|\\d")

    fun find(text: String): Long? {
        for (key in keys) {
            var from = 0
            while (true) {
                val at = text.indexOf(key, from)
                if (at < 0) break
                from = at + key.length
                val rest = text.substring(from)
                val firstLine = rest.substringBefore('\n')
                val candidate = pick(firstLine) ?: pick(rest.substringAfter('\n', "").substringBefore('\n').takeIf { firstLine.isBlank() || firstLine.trim().trimEnd(':').isEmpty() } ?: "")
                if (candidate != null) return candidate
            }
        }
        return null
    }

    private fun pick(segment: String): Long? {
        val clean = segment
            .replace(datePattern, " ")
            .replace(timePattern, " ")
            .replace(masked, " ")
        val tokens = number.findAll(clean).map { it.value }.toList()
        val grouped = tokens.firstOrNull { it.contains(',') || it.contains('،') }
        val chosen = grouped ?: tokens.firstOrNull { t ->
            val digits = t.count { it.isDigit() }
            // شماره حساب/کارت (بی‌ویرگول و خیلی بلند) مانده نیست
            digits in 1..12
        } ?: return null
        val value = chosen.filter { it in '0'..'9' }.toLongOrNull() ?: return null
        return value.takeIf { it in 0 until MAX_AMOUNT_RIAL * 10 }
    }
}
