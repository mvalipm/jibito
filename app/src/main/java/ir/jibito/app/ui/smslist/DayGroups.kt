package ir.jibito.app.ui.smslist

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.Jalali

/** تراکنش‌های یک روز، برای سرتیتر «امروز · ۴۵۰ هزار تومان خرج» */
data class DayGroup(
    /** ساعت ۰۰:۰۰ آن روز */
    val dayStartMillis: Long,
    val items: List<Transaction>,
    /** جمع خرج آن روز (بدون خرید ناموفق، انتقال به خودم و دسته‌هایی که خرج حساب نمی‌شوند) */
    val spendRial: Long,
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
            groups += DayGroup(currentDay, current, current.sumOf { spendOf(it, nonSpendCategoryIds) })
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

private fun spendOf(t: Transaction, nonSpend: Set<Long>): Long =
    if (t.transaction.type == FlowType.WITHDRAWAL && !t.isFailedPurchase && !t.isSelfTransfer &&
        (t.categoryId == null || t.categoryId !in nonSpend)
    ) t.transaction.amountRial else 0L
