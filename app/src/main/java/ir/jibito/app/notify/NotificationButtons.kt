package ir.jibito.app.notify

import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.domain.CategoryTree

/** دکمه‌های دسته‌ی نوتیفیکیشن (جدا از TransactionNotifier تا بی‌اندروید تست شود). */
object NotificationButtons {

    /**
     * اول دسته‌ی پیشنهادی (از مقصد خرید)، بعد پرکاربردترین دسته‌های خود کاربر؛
     * همه در عمقی که کاربر در تنظیمات گذاشته («سوخت» ← «حمل‌ونقل») و بدون دسته‌های اصلی پنهان، مثل برگه‌ی انتخاب دسته.
     * @param byUsage دسته‌ها به ترتیب «بیشترین استفاده»
     * @param hiddenRoots دسته‌های اصلی‌ای که کاربر نمی‌خواهد ببیند (با همه‌ی زیردسته‌هایشان)
     */
    fun choose(
        suggestedName: String?,
        byUsage: List<CategoryEntity>,
        count: Int,
        maxDepth: Int,
        hiddenRoots: Set<Long>,
    ): List<CategoryEntity> {
        val byId = byUsage.associateBy { it.id }
        val parentOf = byUsage.associate { it.id to it.parentId }
        fun lift(c: CategoryEntity) = byId[CategoryTree.idAtDepth(c.id, parentOf, maxDepth)] ?: c
        val suggested = suggestedName?.let { name -> byUsage.firstOrNull { it.name == name } }
        return (listOfNotNull(suggested) + byUsage.filter { !it.name.startsWith("سایر") })
            .filter { CategoryTree.idAtDepth(it.id, parentOf, 1) !in hiddenRoots }
            .map(::lift)
            .distinctBy { it.id }
            .take(count)
    }
}
