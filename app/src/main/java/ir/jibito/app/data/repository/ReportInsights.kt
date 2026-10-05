package ir.jibito.app.data.repository

import ir.jibito.app.data.category.SpendRollup
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.DatedAmount
import ir.jibito.app.util.JalaliMonth
import java.util.Calendar

/** یک نکته‌ی کوتاه برای «جیبی چی فهمید؟» در «گزارش‌ها». مبلغ‌ها ریال. */
sealed interface Insight {
    /** خرج یک دسته‌ی اصلی نسبت به همین موقعِ ماه قبل بیشتر/کمتر شده */
    data class CategoryChange(
        val categoryId: Long,
        val name: String,
        val icon: String?,
        val colorHex: String?,
        val deltaRial: Long,
        /** درصد تغییر (مثبت = بیشتر) */
        val percent: Int,
    ) : Insight

    /** پرخرج‌ترین روز ماه */
    data class BusiestDay(val month: JalaliMonth, val day: Int, val amountRial: Long, val count: Int) : Insight

    /** یکی از روزهای هفته چند برابر بقیه خرج دارد */
    data class WeekdayPeak(
        /** Calendar.SATURDAY … Calendar.FRIDAY */
        val weekday: Int,
        /** چند برابرِ میانگین بقیه‌ی روزها */
        val times: Double,
    ) : Insight
}

object ReportInsights {
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** کمتر از ۱۰۰ هزار تومان در ماه قبل: پایه‌ی درصد نیست */
    private const val MIN_BASE_RIAL = 1_000_000L

    /** تغییر کمتر از ۲۰۰ هزار تومان یا ۱۵٪ ارزش گفتن ندارد */
    private const val MIN_DELTA_RIAL = 2_000_000L
    private const val MIN_PERCENT = 15

    /** الگوی هفته: ۱۲ هفته‌ی اخیر، به شرط دست‌کم ۴ هفته سابقه */
    const val WEEKDAY_WINDOW_DAYS = 84
    private const val WEEKDAY_MIN_DAYS = 28
    private const val WEEKDAY_MIN_TIMES = 1.5

    /**
     * @param rows خرج‌ها (هر تراکنش جدا)، دست‌کم از min(اول ماه قبل، ۸۴ روز پیش) تا آخر month
     * @param categories همه‌ی دسته‌ها (برای پیدا کردن دسته‌ی اصلی و کنار گذاشتن «بیرون از خرج»)
     */
    fun compute(rows: List<DatedAmount>, categories: List<CategoryEntity>, month: JalaliMonth, now: Long): List<Insight> {
        val byId = categories.associateBy { it.id }
        val excluded = BudgetRepositoryImpl.excludedFromSpend(categories)
        val spends = rows.filter { it.categoryId == null || it.categoryId !in excluded }
        return listOfNotNull(
            *categoryChanges(spends, byId, month, now).toTypedArray(),
            busiestDay(spends, month),
            weekdayPeak(spends, now),
        )
    }

    /** بیشترین افزایش و بیشترین کاهش دسته‌ها نسبت به همین موقعِ ماه قبل */
    fun categoryChanges(spends: List<DatedAmount>, byId: Map<Long, CategoryEntity>, month: JalaliMonth, now: Long): List<Insight.CategoryChange> {
        val start = month.startMillis()
        val end = now.coerceIn(start, month.endMillis())
        val previous = month.plus(-1)
        val prevEnd = (previous.startMillis() + (end - start)).coerceAtMost(previous.endMillis())
        fun sums(from: Long, to: Long): Map<Long, Long> {
            val out = HashMap<Long, Long>()
            for (s in spends) {
                if (s.dateEpoch < from || s.dateEpoch >= to) continue
                val cat = s.categoryId?.let { byId[it] } ?: continue
                val root = SpendRollup.rootOf(cat, byId)
                if (root.isArchived) continue
                out[root.id] = (out[root.id] ?: 0L) + s.amount
            }
            return out
        }
        val now0 = sums(start, end)
        val before = sums(previous.startMillis(), prevEnd)
        val changes = (now0.keys + before.keys).mapNotNull { id ->
            val a = now0[id] ?: 0L
            val b = before[id] ?: 0L
            if (b < MIN_BASE_RIAL) return@mapNotNull null
            val delta = a - b
            val percent = Math.round(delta * 100.0 / b).toInt()
            if (kotlin.math.abs(delta) < MIN_DELTA_RIAL || kotlin.math.abs(percent) < MIN_PERCENT) return@mapNotNull null
            val c = byId.getValue(id)
            Insight.CategoryChange(id, c.name, c.icon, c.colorHex, delta, percent)
        }
        return listOfNotNull(changes.filter { it.deltaRial > 0 }.maxByOrNull { it.deltaRial }, changes.filter { it.deltaRial < 0 }.minByOrNull { it.deltaRial })
    }

    /** پرخرج‌ترین روز ماه؛ فقط وقتی دست‌کم دو روز خرج داشته (وگرنه «پرخرج‌ترین» معنی ندارد) */
    fun busiestDay(spends: List<DatedAmount>, month: JalaliMonth): Insight.BusiestDay? {
        val from = month.startMillis()
        val to = month.endMillis()
        val sums = HashMap<Int, Long>()
        val counts = HashMap<Int, Int>()
        for (s in spends) {
            if (s.dateEpoch < from || s.dateEpoch >= to) continue
            val d = SpendCurve.dayOfMonth(s.dateEpoch)
            sums[d] = (sums[d] ?: 0L) + s.amount
            counts[d] = (counts[d] ?: 0) + 1
        }
        if (sums.size < 2) return null
        val (day, amount) = sums.maxWith(compareBy<Map.Entry<Int, Long>> { it.value }.thenBy { -it.key }).toPair()
        return Insight.BusiestDay(month, day, amount, counts[day] ?: 0)
    }

    /** روزی از هفته که دست‌کم ۱٫۵ برابر میانگین بقیه خرج دارد (۱۲ هفته‌ی اخیر) */
    fun weekdayPeak(spends: List<DatedAmount>, now: Long): Insight.WeekdayPeak? {
        val from = now - WEEKDAY_WINDOW_DAYS * DAY_MILLIS
        val window = spends.filter { it.dateEpoch in from until now }
        val earliest = window.minOfOrNull { it.dateEpoch } ?: return null
        if (now - earliest < WEEKDAY_MIN_DAYS * DAY_MILLIS) return null
        val cal = Calendar.getInstance()
        val sums = LongArray(8)
        for (s in window) {
            cal.timeInMillis = s.dateEpoch
            sums[cal.get(Calendar.DAY_OF_WEEK)] += s.amount
        }
        val days = (Calendar.SUNDAY..Calendar.SATURDAY).toList()
        val peak = days.maxBy { sums[it] }
        val others = days.filter { it != peak }.map { sums[it] }.average()
        if (others <= 0.0) return null
        val times = sums[peak] / others
        return if (times >= WEEKDAY_MIN_TIMES) Insight.WeekdayPeak(peak, times) else null
    }
}
