package ir.jibito.app.util

import java.util.Locale

/** نمایش پول. داخل اپ همه‌چیز ریال است؛ برای نمایش به تومان تبدیل می‌کنیم. */
object Money {

    /** مثلاً 81500000 ریال ← «۸٬۱۵۰٬۰۰۰ تومان» */
    fun toman(rial: Long): String = tomanNumber(rial) + " تومان"

    /**
     * عدد کوتاه برای «یک نگاه» (به تومان، بدون کلمه‌ی «تومان»):
     * «۴۸٫۹ میلیون»، «۹۸۱ هزار»، «۱٫۲ میلیارد». رقم آخر گرد نمی‌شود، بریده می‌شود
     * (تا مثلاً ۴۸٫۹۵ میلیون «۴۹ میلیون» دیده نشود). عدد کامل در صفحه‌ی جزئیات می‌آید.
     */
    fun compact(rial: Long): String {
        val toman = kotlin.math.abs(rial / 10)
        val sign = if (rial < 0) "−" else ""
        val text = when {
            toman >= 1_000_000_000L -> oneDecimal(toman, 1_000_000_000L) + " میلیارد"
            toman >= 1_000_000L -> oneDecimal(toman, 1_000_000L) + " میلیون"
            toman >= 1_000L -> (toman / 1_000L).toString() + " هزار"
            else -> toman.toString()
        }
        return Jalali.toPersianDigits(sign + text)
    }

    /** مثلاً ۴۸٬۹۵۲٬۲۵۰ ÷ ۱٬۰۰۰٬۰۰۰ ← «48٫9»؛ اعشار صفر نوشته نمی‌شود */
    private fun oneDecimal(value: Long, unit: Long): String {
        val tenths = value * 10 / unit
        val whole = tenths / 10
        val frac = tenths % 10
        return if (frac == 0L) whole.toString() else "$whole٫$frac"
    }

    /** فقط عدد، بدون «تومان»: «۸٬۱۵۰٬۰۰۰» */
    fun tomanNumber(rial: Long): String {
        val grouped = String.format(Locale.US, "%,d", rial / 10).replace(',', '٬')
        return Jalali.toPersianDigits(grouped)
    }
}
