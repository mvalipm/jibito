package ir.jibito.app.data.repository

import ir.jibito.app.util.JalaliMonth

/** خرج یک ماه */
data class MonthSpend(val month: JalaliMonth, val spentRial: Long)

/**
 * روند خرج چند ماه اخیر + مقایسه‌ی «تا امروزِ این ماه» با «تا همین روزِ ماه قبل».
 * همان تعریف خرج صفحه‌ی خلاصه: بدون خرید ناموفق، انتقال به خودم و دسته‌هایی مثل پس‌انداز.
 */
data class SpendTrend(
    /** قدیمی‌ترین اول؛ آخری همان ماهِ انتخاب‌شده است */
    val months: List<MonthSpend>,
    /** فقط برای ماه جاری: خرج ماه قبل از اولش تا همین نقطه از ماه؛ null یعنی مقایسه معنی ندارد */
    val lastMonthSameTimeRial: Long?,
    /** ماه انتخاب‌شده هنوز تمام نشده (آخرین ستون «تا امروز» است و با ماه کامل مقایسه نمی‌شود) */
    val isCurrent: Boolean = false,
    /** فقط برای ماه جاری: اگر با همین ریتم خرج شود، آخر ماه به چند می‌رسد؛ null اگر هنوز زود است */
    val projectedRial: Long? = null,
) {
    /** میانگین ماه‌های تمام‌شده‌ای که خرج داشته‌اند (ماه جاری حساب نمی‌شود)؛ null اگر هیچ‌کدام نیست */
    val averageRial: Long?
        get() {
            val done = (if (isCurrent) months.dropLast(1) else months).filter { it.spentRial > 0 }
            return if (done.isEmpty()) null else done.sumOf { it.spentRial } / done.size
        }

    /**
     * چند درصد بیشتر (+) یا کمتر (−) از همین موقعِ ماه قبل.
     * null وقتی ماه قبل تا این نقطه تقریباً خرجی نداشته (درصد گمراه‌کننده می‌شد).
     */
    val vsLastMonthPercent: Int?
        get() {
            val before = lastMonthSameTimeRial ?: return null
            if (before < MIN_COMPARABLE_RIAL) return null
            val now = months.lastOrNull()?.spentRial ?: return null
            return (((now - before) * 100.0) / before).let { kotlin.math.round(it).toInt() }
        }

    companion object {
        /** کمتر از ۱۰۰ هزار تومان: پایه‌ی مقایسه نیست */
        private const val MIN_COMPARABLE_RIAL = 1_000_000L

        /** پیش‌بینی آخر ماه فقط بعد از سه روز اول */
        private const val MIN_PROJECTION_MILLIS = 3L * 24 * 60 * 60 * 1000

        /**
         * @param spends (زمان، مبلغ ریال) خرج‌هایی که حساب می‌شوند، از اول قدیمی‌ترین ماه تا آخر ماه انتخاب‌شده
         */
        fun compute(spends: List<Pair<Long, Long>>, month: JalaliMonth, count: Int, now: Long): SpendTrend {
            val months = (count - 1 downTo 0).map { month.plus(-it) }
            val byKey = HashMap<Int, Long>()
            for ((time, amount) in spends) {
                val key = JalaliMonth.of(time).key
                byKey[key] = (byKey[key] ?: 0L) + amount
            }
            val list = months.map { MonthSpend(it, byKey[it.key] ?: 0L) }

            val isCurrent = now >= month.startMillis() && now < month.endMillis()
            val sameTime = if (isCurrent) {
                val previous = month.plus(-1)
                val cutoff = (previous.startMillis() + (now - month.startMillis())).coerceAtMost(previous.endMillis())
                spends.filter { (time, _) -> time >= previous.startMillis() && time < cutoff }.sumOf { it.second }
            } else {
                null
            }
            // پیش‌بینی آخر ماه از ریتم تا امروز؛ سه روز اول ماه هنوز ریتمی ندارد
            val elapsed = now - month.startMillis()
            val projected = if (isCurrent && elapsed >= MIN_PROJECTION_MILLIS) {
                val spent = list.last().spentRial
                (spent.toDouble() * (month.endMillis() - month.startMillis()) / elapsed).toLong()
            } else {
                null
            }
            return SpendTrend(list, sameTime, isCurrent = isCurrent, projectedRial = projected)
        }
    }
}
