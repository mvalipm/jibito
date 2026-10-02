package ir.jibito.app.data.category

import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.CategorySum

/**
 * جمع زدن مبلغ‌ها روی «دسته‌ی اصلی»: خرج «سوخت» جزو «خودرو شخصی» و آن هم جزو «حمل‌ونقل» است.
 * بودجه و نمودار روی دسته‌ی اصلی‌اند؛ تراکنش می‌تواند روی هر لایه‌ای باشد.
 */
object SpendRollup {

    data class Result(
        /** دسته‌ی اصلی ← جمع (فقط دسته‌هایی که خرج حساب می‌شوند) */
        val byRoot: Map<Long, Long>,
        /** بی‌دسته (یا دسته‌ای که دیگر نیست) */
        val uncategorized: Long,
        /** «بیرون از خرج»: پس‌انداز، قرض دادن */
        val excluded: Long,
    ) {
        /** کل خرج = دسته‌ها + بی‌دسته (بیرون از خرج حساب نمی‌شود) */
        val total: Long get() = byRoot.values.sum() + uncategorized
    }

    fun rollup(categories: List<CategoryEntity>, sums: List<CategorySum>): Result {
        val byId = categories.associateBy { it.id }
        val byRoot = HashMap<Long, Long>()
        var uncategorized = 0L
        var excluded = 0L
        for (s in sums) {
            val cat = s.categoryId?.let { byId[it] }
            if (cat == null) {
                uncategorized += s.totalRial
                continue
            }
            val root = rootOf(cat, byId)
            when {
                !cat.countsAsSpend || !root.countsAsSpend -> excluded += s.totalRial
                root.isArchived -> uncategorized += s.totalRial
                else -> byRoot[root.id] = (byRoot[root.id] ?: 0L) + s.totalRial
            }
        }
        return Result(byRoot, uncategorized, excluded)
    }

    /** دسته‌ی اصلی (بالاترین والد)؛ در برابر حلقه‌ی اشتباهی هم امن است */
    fun rootOf(cat: CategoryEntity, byId: Map<Long, CategoryEntity>): CategoryEntity {
        var current = cat
        var steps = 0
        while (current.parentId != null && steps < 10) {
            current = byId[current.parentId] ?: break
            steps++
        }
        return current
    }
}
