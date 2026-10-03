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

    /**
     * عدد بزرگ سرصفحه، جدا از واحدش: «۱۸٫۴» + «میلیون»، «۸۵۰» + «هزار»، «۹۰۰» + "".
     * (طرح: عدد درشت و «میلیون تومان» کنارش با اندازه‌ی کوچک‌تر)
     */
    fun compactParts(rial: Long): Pair<String, String> {
        val text = compact(rial)
        val cut = text.lastIndexOf(' ')
        return if (cut < 0) text to "" else text.substring(0, cut) to text.substring(cut + 1)
    }

    /** «بودجه‌ی ۳۰ میلیونی»: همان عدد کوتاه با «ی» نسبت (برای عدد بی‌واحد: «۹۰۰ تومانی») */
    fun compactAdjective(rial: Long): String {
        val (number, unit) = compactParts(rial)
        return if (unit.isEmpty()) "$number تومانی" else "$number ${unit}ی"
    }

    /** خیلی کوتاه، برای کارت‌های کوچک مانده‌ی بانک‌ها: «۴۱٫۲ م»، «۹۸۱ هزار»، «۱٫۲ میلیارد» */
    fun short(rial: Long): String = compact(rial).replace(" میلیون", " م")

    /** عدد ستون نمودار به واحد داده‌شده با یک رقم اعشار: ۲۳٬۶۰۰٬۰۰۰ تومان با واحد میلیون ← «۲۳٫۶» */
    fun inUnit(rial: Long, unitToman: Long): String {
        val toman = kotlin.math.abs(rial / 10)
        return Jalali.toPersianDigits(oneDecimal(toman, unitToman))
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
