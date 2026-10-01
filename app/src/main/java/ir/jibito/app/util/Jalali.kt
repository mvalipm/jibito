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

    val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    /** شمسی ← میلادی (الگوریتم ۳۳ساله، همان خانواده‌ی fromGregorian). */
    fun toGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val y = jy + 1595
        var days = -355668 + 365 * y + (y / 33) * 8 + ((y % 33) + 3) / 4 + jd +
            if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val leap = (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0
        val monthDays = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > monthDays[gm]) {
            gd -= monthDays[gm]
            gm++
        }
        return Triple(gy, gm, gd)
    }
}

/** یک ماه شمسی، مثلاً مهر ۱۴۰۵. */
data class JalaliMonth(val year: Int, val month: Int) {

    val title: String get() = Jalali.MONTH_NAMES[month - 1] + " " + Jalali.toPersianDigits(year.toString())

    /** کلید عددی برای ذخیره، مثلاً 140507 */
    val key: Int get() = year * 100 + month

    fun plus(months: Int): JalaliMonth {
        val index = year * 12 + (month - 1) + months
        return JalaliMonth(Math.floorDiv(index, 12), Math.floorMod(index, 12) + 1)
    }

    /** ساعت ۰۰:۰۰ روز اول این ماه (به وقت گوشی) */
    fun startMillis(): Long {
        val (gy, gm, gd) = Jalali.toGregorian(year, month, 1)
        return Calendar.getInstance().apply {
            clear()
            set(gy, gm - 1, gd, 0, 0, 0)
        }.timeInMillis
    }

    /** شروع ماه بعد (انتهای این ماه، خودش جزو این ماه نیست) */
    fun endMillis(): Long = plus(1).startMillis()

    companion object {
        fun of(epochMillis: Long): JalaliMonth {
            val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
            val (jy, jm, _) = Jalali.fromGregorian(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
            )
            return JalaliMonth(jy, jm)
        }

        fun current(): JalaliMonth = of(System.currentTimeMillis())
    }
}
