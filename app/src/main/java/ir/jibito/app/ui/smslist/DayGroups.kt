package ir.jibito.app.ui.smslist

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.Jalali

/** تراکنش‌های یک روز، برای سرتیتر «امروز · ۴۵۰ هزار تومان خرج» */
data class DayGroup(
    /** ساعت ۰۰:۰۰ آن روز */
    val dayStartMillis: Long,
    val items: List<Transaction>,
    /**
     * جمع خرج آن روز (بدون خرید ناموفق، انتقال به خودم و دسته‌هایی که خرج حساب نمی‌شوند)،
     * بدون خرج‌های یک‌باره: نوار روزها با هم مقایسه می‌شود و یک خرید خانه بقیه‌ی روزها را صاف نکند.
     */
    val spendRial: Long,
    /** خرج یک‌باره‌ی آن روز (خرید خانه…)؛ جدا کنار نوار نوشته می‌شود */
    val oneOffRial: Long = 0,
)

/**
 * فهرست (تازه‌ترین اول) را روزبه‌روز گروه می‌کند؛ ترتیب خود فهرست حفظ می‌شود.
 * @param nonSpendCategoryIds دسته‌هایی مثل پس‌انداز که خرج حساب نمی‌شوند (مثل صفحه‌ی خلاصه)
 */
fun groupByDay(list: List<Transaction>, nonSpendCategoryIds: Set<Long> = emptySet()): List<DayGroup> {
    val groups = ArrayList<DayGroup>()
    var currentDay = Long.MIN_VALUE
    var current = ArrayList<Transaction>()
    fun flush() {
        if (current.isNotEmpty()) {
            val (oneOffs, routine) = current.filter { countsAsSpend(it, nonSpendCategoryIds) }.partition { it.isOneOff }
            groups += DayGroup(
                currentDay, current,
                spendRial = routine.sumOf { it.transaction.amountRial },
                oneOffRial = oneOffs.sumOf { it.transaction.amountRial },
            )
        }
    }
    for (t in list) {
        val day = Jalali.startOfDay(t.dateMillis)
        if (day != currentDay) {
            flush()
            currentDay = day
            current = ArrayList()
        }
        current += t
    }
    flush()
    return groups
}

private fun countsAsSpend(t: Transaction, nonSpend: Set<Long>): Boolean =
    t.transaction.type == FlowType.WITHDRAWAL && !t.isFailedPurchase && !t.isSelfTransfer &&
        (t.categoryId == null || t.categoryId !in nonSpend)
