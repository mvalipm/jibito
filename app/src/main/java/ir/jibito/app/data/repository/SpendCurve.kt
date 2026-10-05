package ir.jibito.app.data.repository

import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import java.util.Calendar

/**
 * خرج تجمعی روزبه‌روزِ یک ماه در برابر ماه قبل (نمودار اصلی «گزارش‌ها»).
 * همان تعریف خرج صفحه‌ی خلاصه و روند: بدون خرید ناموفق، انتقال به خودم و دسته‌هایی مثل پس‌انداز.
 * همه‌ی مبلغ‌ها ریال.
 */
data class SpendCurve(
    val month: JalaliMonth,
    /** چند روز دارد (۲۹ تا ۳۱) */
    val days: Int,
    /** خرج از اول ماه تا آخر هر روز؛ برای ماه جاری فقط تا امروز (آخرین عضو = امروز) */
    val cumulative: List<Long>,
    /** همین منحنی برای کل ماه قبل */
    val previous: List<Long>,
    val isCurrent: Boolean,
    /**
     * پیش‌بینی خرج تجمعی از امروز تا آخر ماه؛ اولین عضو همان امروز است (روز cumulative.lastIndex).
     * با الگوی ماه قبل: «از امروز تا آخر ماه، همان‌قدر که ماه قبل از همین روز تا آخرش خرج شد».
     * ماه قبل خرج کافی نداشته باشد: با ریتم همین ماه. خالی برای ماه تمام‌شده یا سه روز اول بی‌پایه.
     */
    val projection: List<Long>,
    /** پیش‌بینی با الگوی ماه قبل است (نه ریتم همین ماه) */
    val projectionFromPattern: Boolean,
    /** بودجه‌ی کل ماه؛ null یعنی تعیین نشده */
    val budgetRial: Long?,
) {
    val todayIndex: Int get() = cumulative.lastIndex
    val spentRial: Long get() = cumulative.lastOrNull() ?: 0L
    val projectedEndRial: Long? get() = projection.lastOrNull()

    /** خرج تجمعی ماه قبل تا همین روز (ماه کوتاه‌تر: تا آخرش) */
    fun previousAt(day: Int): Long? = previous.getOrNull(day.coerceAtMost(previous.lastIndex))

    /** مقدار نمودار در یک روز: واقعی تا امروز، بعدش پیش‌بینی */
    fun valueAt(day: Int): Long? =
        cumulative.getOrNull(day) ?: projection.getOrNull(day - todayIndex)

    companion object {
        /** کمتر از ۱۰۰ هزار تومان در ماه قبل: الگویش پایه‌ی پیش‌بینی نیست */
        private const val MIN_PATTERN_RIAL = 1_000_000L

        /** پیش‌بینی با ریتم همین ماه فقط بعد از سه روز اول */
        private const val MIN_RHYTHM_DAYS = 3

        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        fun daysIn(month: JalaliMonth): Int =
            ((month.endMillis() - month.startMillis() + DAY_MILLIS / 2) / DAY_MILLIS).toInt()

        /** روز ماه شمسی (۱ تا ۳۱) به وقت گوشی */
        fun dayOfMonth(epochMillis: Long): Int {
            val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
            return Jalali.fromGregorian(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)).third
        }

        private fun cumulativeOf(spends: List<Pair<Long, Long>>, month: JalaliMonth): LongArray {
            val days = daysIn(month)
            val daily = LongArray(days)
            val from = month.startMillis()
            val to = month.endMillis()
            for ((time, amount) in spends) {
                if (time < from || time >= to) continue
                daily[(dayOfMonth(time) - 1).coerceIn(0, days - 1)] += amount
            }
            for (i in 1 until days) daily[i] += daily[i - 1]
            return daily
        }

        /**
         * @param spends (زمان، مبلغ ریال) خرج‌هایی که حساب می‌شوند؛ حداقل از اول ماه قبل تا آخر month
         */
        fun compute(spends: List<Pair<Long, Long>>, month: JalaliMonth, now: Long, budgetRial: Long?): SpendCurve {
            val days = daysIn(month)
            val full = cumulativeOf(spends, month)
            val previous = cumulativeOf(spends, month.plus(-1)).toList()
            val isCurrent = now >= month.startMillis() && now < month.endMillis()
            val today = if (isCurrent) dayOfMonth(now) - 1 else days - 1
            val cumulative = full.take(today + 1)

            val fromPattern = isCurrent && (previous.lastOrNull() ?: 0L) >= MIN_PATTERN_RIAL
            val projection = when {
                !isCurrent -> emptyList()
                fromPattern -> {
                    val base = previous[today.coerceAtMost(previous.lastIndex)]
                    (today until days).map { d -> cumulative[today] + (previous[d.coerceAtMost(previous.lastIndex)] - base) }
                }
                today + 1 >= MIN_RHYTHM_DAYS -> (today until days).map { d -> cumulative[today] * (d + 1) / (today + 1) }
                else -> emptyList()
            }
            return SpendCurve(month, days, cumulative, previous, isCurrent, projection, fromPattern, budgetRial)
        }
    }
}
