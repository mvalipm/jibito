package ir.jibito.app.ui.smslist

import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import java.util.Calendar

/** بازه‌ی تاریخ جست‌وجو */
enum class DatePreset { ALL, TODAY, YESTERDAY, WEEK, THIS_MONTH, LAST_MONTH, CUSTOM }

/**
 * جست‌وجوی تراکنش‌ها:
 * - text: اسم طرف حساب/یادداشت، بانک، دسته — یا یک عدد (مبلغ به تومان؛ هر مبلغی که این رقم‌ها را دارد).
 * - تاریخ: یکی از بازه‌های آماده، یا یک ماه دلخواه (و اگر بخواهد یک روز از آن).
 * - مبلغ: از … تا … تومان.
 */
data class TxSearch(
    val text: String = "",
    val preset: DatePreset = DatePreset.ALL,
    /** برای CUSTOM: کلید ماه شمسی (مثلاً 140507) و روز (اختیاری) */
    val monthKey: Int? = null,
    val day: Int? = null,
    val minToman: Long? = null,
    val maxToman: Long? = null,
) {
    val isActive: Boolean
        get() = text.isNotBlank() || preset != DatePreset.ALL || minToman != null || maxToman != null

    /** بازه‌ی زمانی [از، تا) به میلی‌ثانیه؛ null یعنی همه‌ی زمان‌ها */
    fun range(now: Long = System.currentTimeMillis()): Pair<Long, Long>? {
        val dayMs = 24L * 60 * 60 * 1000
        val todayStart = startOfDay(now)
        return when (preset) {
            DatePreset.ALL -> null
            DatePreset.TODAY -> todayStart to todayStart + dayMs
            DatePreset.YESTERDAY -> todayStart - dayMs to todayStart
            DatePreset.WEEK -> todayStart - 6 * dayMs to todayStart + dayMs
            DatePreset.THIS_MONTH -> JalaliMonth.current().let { it.startMillis() to it.endMillis() }
            DatePreset.LAST_MONTH -> JalaliMonth.current().plus(-1).let { it.startMillis() to it.endMillis() }
            DatePreset.CUSTOM -> {
                val key = monthKey ?: return null
                val month = JalaliMonth(key / 100, key % 100)
                val d = day
                if (d == null) {
                    month.startMillis() to month.endMillis()
                } else {
                    val start = jalaliDayStart(month.year, month.month, d)
                    start to start + dayMs
                }
            }
        }
    }

    fun matches(t: Transaction, range: Pair<Long, Long>?): Boolean {
        if (range != null && (t.dateMillis < range.first || t.dateMillis >= range.second)) return false
        val toman = t.transaction.amountRial / 10
        if (minToman != null && toman < minToman) return false
        if (maxToman != null && toman > maxToman) return false
        val q = text.trim()
        if (q.isEmpty()) return true
        val digits = toLatinDigits(q).filter { it.isDigit() }
        val isNumber = digits.isNotEmpty() && toLatinDigits(q).all { it.isDigit() || it == ',' || it == '٬' || it == ' ' }
        if (isNumber) return toman.toString().contains(digits)
        val key = q.normalizedKey()
        return listOfNotNull(t.merchant, t.bank?.name, t.categoryName, t.body)
            .any { it.normalizedKey().contains(key) }
    }

    companion object {
        fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun jalaliDayStart(jy: Int, jm: Int, jd: Int): Long {
            val (gy, gm, gd) = Jalali.toGregorian(jy, jm, jd)
            return Calendar.getInstance().apply {
                clear()
                set(gy, gm - 1, gd, 0, 0, 0)
            }.timeInMillis
        }

        fun toLatinDigits(s: String): String = buildString {
            for (ch in s) append(
                when (ch) {
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    else -> ch
                }
            )
        }

        private fun String.normalizedKey(): String =
            replace('ي', 'ی').replace('ك', 'ک').replace("‌", "").replace(" ", "").lowercase()
    }
}
