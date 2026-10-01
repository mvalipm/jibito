package ir.jibito.app.util

import java.util.Calendar

/** تبدیل تاریخ میلادی به شمسی و اعداد انگلیسی به فارسی — فقط برای نمایش. */
object Jalali {

    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun toPersianDigits(text: String): String = buildString {
        text.forEach { ch -> append(if (ch in '0'..'9') persianDigits[ch - '0'] else ch) }
    }

    /** مثلاً: ۱۴۰۵/۰۷/۰۹ - ۱۷:۰۲ */
    fun format(epochMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val (jy, jm, jd) = fromGregorian(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
        val text = "%04d/%02d/%02d - %02d:%02d".format(
            jy, jm, jd, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
        )
        return toPersianDigits(text)
    }

    fun fromGregorian(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysBeforeMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 +
            (gy2 + 399) / 400 + gd + gDaysBeforeMonth[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return Triple(jy, jm, jd)
    }
}
