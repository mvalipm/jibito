package ir.jibito.app.util

import java.util.Locale

/** نمایش پول. داخل اپ همه‌چیز ریال است؛ برای نمایش به تومان تبدیل می‌کنیم. */
object Money {

    /** مثلاً 81500000 ریال ← «۸٬۱۵۰٬۰۰۰ تومان» */
    fun toman(rial: Long): String = tomanNumber(rial) + " تومان"

    /** فقط عدد، بدون «تومان»: «۸٬۱۵۰٬۰۰۰» */
    fun tomanNumber(rial: Long): String {
        val grouped = String.format(Locale.US, "%,d", rial / 10).replace(',', '٬')
        return Jalali.toPersianDigits(grouped)
    }
}
