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
    val base = if (onlyUncategorized) all.filter(::isUncategorizedSpend) else all
    val list = if (search.isActive) {
        val range = search.range(now)
        base.filter { search.matches(it, range) }
    } else {
        base
    }
    return VisibleTransactions(list, groupByDay(list, nonSpendCategoryIds(categories)))
}

/** خرجی که هنوز دسته ندارد (فیلتر «بی‌دسته») */
fun isUncategorizedSpend(t: Transaction): Boolean =
    t.categoryId == null && !t.isSelfTransfer && !t.isFailedPurchase && t.transaction.type == FlowType.WITHDRAWAL

/** چند تراکنش با این جست‌وجو پیدا می‌شود (دکمه‌ی «نمایش ۴۸ تراکنش» در برگه‌ی فیلتر، پیش از اعمال) */
fun countMatching(
    all: List<Transaction>,
    onlyUncategorized: Boolean,
    search: TxSearch,
    now: Long = System.currentTimeMillis(),
): Int {
    val range = search.range(now)
    return all.count { (!onlyUncategorized || isUncategorizedSpend(it)) && (!search.isActive || search.matches(it, range)) }
}

/** دسته‌هایی مثل پس‌انداز (یا زیردسته‌هایشان) که در جمع خرج روز حساب نمی‌شوند، مثل صفحه‌ی خلاصه */
fun nonSpendCategoryIds(categories: List<Category>): Set<Long> = CategoryTree.nonSpendIds(categories)
