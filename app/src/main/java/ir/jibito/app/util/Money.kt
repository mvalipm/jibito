package ir.jibito.app.util

import java.util.Locale

/** نمایش پول. داخل اپ همه‌چیز ریال است؛ برای نمایش به تومان تبدیل می‌کنیم. */
object Money {

    /** مثلاً 81500000 ریال ← «۸٬۱۵۰٬۰۰۰ تومان» */
    fun toman(rial: Long): String {
        val toman = rial / 10
        val grouped = String.format(Locale.US, "%,d", toman).replace(',', '٬')
        return Jalali.toPersianDigits(grouped) + " تومان"
    }
}
