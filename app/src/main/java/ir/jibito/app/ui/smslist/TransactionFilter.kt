package ir.jibito.app.ui.smslist

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.CategoryTree
import ir.jibito.app.domain.Transaction

/** آنچه فهرست تراکنش‌ها نشان می‌دهد: بعد از فیلتر «بی‌دسته» و جست‌وجو، روزبه‌روز گروه‌شده */
data class VisibleTransactions(
    val list: List<Transaction>,
    val groups: List<DayGroup>,
)

/**
 * فیلتر و گروه‌بندی فهرست تراکنش‌ها؛ بیرون از رشته‌ی اصلی (در ViewModel) اجرا می‌شود
 * تا با هزاران تراکنش، تایپ در جست‌وجو یا اسکرول گیر نکند.
 */
fun visibleTransactions(
    all: List<Transaction>,
    categories: List<Category>,
    onlyUncategorized: Boolean,
    search: TxSearch,
    now: Long = System.currentTimeMillis(),
): VisibleTransactions {
    val base = if (onlyUncategorized) {
        all.filter {
            it.categoryId == null && !it.isSelfTransfer && !it.isFailedPurchase &&
                it.transaction.type == FlowType.WITHDRAWAL
        }
    } else {
        all
    }
    val list = if (search.isActive) {
        val range = search.range(now)
        base.filter { search.matches(it, range) }
    } else {
        base
    }
    return VisibleTransactions(list, groupByDay(list, nonSpendCategoryIds(categories)))
}

/** دسته‌هایی مثل پس‌انداز (یا زیردسته‌هایشان) که در جمع خرج روز حساب نمی‌شوند، مثل صفحه‌ی خلاصه */
fun nonSpendCategoryIds(categories: List<Category>): Set<Long> {
    val byId = categories.associateBy { it.id }
    return categories.filter { !it.countsAsSpend || !CategoryTree.rootOf(it, byId).countsAsSpend }.mapTo(HashSet()) { it.id }
}
