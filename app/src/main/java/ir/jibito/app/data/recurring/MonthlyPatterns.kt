package ir.jibito.app.data.recurring

import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import java.util.Calendar
import kotlin.math.abs

/** الگوی پرداخت ماهانه: مبلغ معمول، روز معمول در ماه شمسی، و چند ماه دیده شده */
data class MonthlyPattern(val amountRial: Long, val dayOfMonth: Int, val months: Int)

/**
 * «آیا این پرداخت‌ها ماهانه‌اند؟» — قاعده‌ی مشترک RecurringDetector (یادآوری پرداخت) و حدس دسته‌ی کارت‌به‌کارت.
 * پرداخت‌ها ماهانه حساب می‌شوند وقتی:
 * - در دست‌کم ۳ ماه شمسیِ مختلف، مبلغ نزدیک (±۱۵٪ میانه) داشته باشند،
 * - در هر ماه تقریباً یک بار (نه خریدهای پشت سر هم)،
 * - حدوداً در یک روز ماه (پراکندگی روز حداکثر ۴ روز)،
 * - و هنوز فعال باشند (این ماه یا ماه قبل هم پرداخت شده).
 *
 * پنجره‌ی زمانی (مثلاً ۶ ماه اخیر) را صدا زننده جدا می‌کند.
 */
object MonthlyPatterns {

    const val MIN_MONTHS = 3
    private const val AMOUNT_TOLERANCE = 0.15
    private const val MAX_DAY_SPREAD = 4

    /** @param payments جفت‌های (مبلغ به ریال، زمان پرداخت) */
    fun detect(payments: List<Pair<Long, Long>>, now: Long): MonthlyPattern? {
        if (payments.isEmpty()) return null
        val current = JalaliMonth.of(now)
        val median = median(payments.map { it.first })
        val similar = payments.filter { abs(it.first - median) <= median * AMOUNT_TOLERANCE }
        val byMonth = similar.groupBy { JalaliMonth.of(it.second).key }
        if (byMonth.size < MIN_MONTHS) return null
        // تقریباً یک بار در ماه
        if (similar.size > byMonth.size * 4 / 3) return null
        // هنوز فعال
        if (current.key !in byMonth && current.plus(-1).key !in byMonth) return null
        val days = byMonth.values.map { jalaliDay(it.first().second) }
        val typicalDay = median(days.map { it.toLong() }).toInt()
        if (days.any { abs(it - typicalDay) > MAX_DAY_SPREAD }) return null
        return MonthlyPattern(median(similar.map { it.first }), typicalDay.coerceIn(1, 31), byMonth.size)
    }

    private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]

    private fun jalaliDay(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return Jalali.fromGregorian(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)).third
    }
}
