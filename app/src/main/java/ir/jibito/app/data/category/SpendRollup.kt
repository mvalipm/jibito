package ir.jibito.app.data.category

import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.CategorySum
import ir.jibito.app.data.local.entity.DatedAmount

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
        /** دسته‌ی اصلی ← چه مقدار از خرجش یک‌باره است (جزو byRoot هم هست) */
        val oneOffByRoot: Map<Long, Long> = emptyMap(),
        /** خرج یک‌باره‌ی بی‌دسته (جزو uncategorized هم هست) */
        val oneOffUncategorized: Long = 0,
    ) {
        /** کل خرج = دسته‌ها + بی‌دسته (بیرون از خرج حساب نمی‌شود) */
        val total: Long get() = byRoot.values.sum() + uncategorized

        /** چه مقدار از total خرج یک‌باره است */
        val oneOffTotal: Long get() = oneOffByRoot.values.sum() + oneOffUncategorized

        /** خرجی که از بودجه کم می‌شود: کل خرج بدون خرج‌های یک‌باره */
        val budgetTotal: Long get() = total - oneOffTotal

        /** خرج یک دسته‌ی اصلی که از بودجه‌اش کم می‌شود */
        fun budgetSpentOf(rootId: Long): Long = (byRoot[rootId] ?: 0L) - (oneOffByRoot[rootId] ?: 0L)
    }

    fun rollup(categories: List<CategoryEntity>, sums: List<CategorySum>): Result {
        val byId = categories.associateBy { it.id }
        val byRoot = HashMap<Long, Long>()
        val oneOffByRoot = HashMap<Long, Long>()
        var uncategorized = 0L
        var oneOffUncategorized = 0L
        var excluded = 0L
        for (s in sums) {
            val cat = s.categoryId?.let { byId[it] }
            val root = cat?.let { rootOf(it, byId) }
            when {
                cat == null || root == null || (countsAsSpend(cat, root) && root.isArchived) -> {
                    uncategorized += s.totalRial
                    oneOffUncategorized += s.oneOffRial
                }
                !countsAsSpend(cat, root) -> excluded += s.totalRial
                else -> {
                    byRoot[root.id] = (byRoot[root.id] ?: 0L) + s.totalRial
                    if (s.oneOffRial != 0L) oneOffByRoot[root.id] = (oneOffByRoot[root.id] ?: 0L) + s.oneOffRial
                }
            }
        }
        return Result(byRoot, uncategorized, excluded, oneOffByRoot, oneOffUncategorized)
    }

    /**
     * برای هر دسته‌ی اصلی: خرج هر زیردسته‌ی لایه‌ی ۲ (با جزئیاتش).
     * کلید null یعنی تراکنش مستقیم روی خود دسته‌ی اصلی ثبت شده.
     */
    fun byChild(categories: List<CategoryEntity>, sums: List<CategorySum>): Map<Long, Map<CategoryEntity?, Long>> {
        val byId = categories.associateBy { it.id }
        val result = HashMap<Long, HashMap<CategoryEntity?, Long>>()
        for (s in sums) {
            val cat = s.categoryId?.let { byId[it] } ?: continue
            val root = rootOf(cat, byId)
            if (!countsAsSpend(cat, root) || root.isArchived) continue
            // نزدیک‌ترین والدی که مستقیم زیر دسته‌ی اصلی است
            var child: CategoryEntity? = if (cat.id == root.id) null else cat
            var steps = 0
            while (child != null && child.parentId != root.id && steps < 10) {
                child = child.parentId?.let { byId[it] }
                steps++
            }
            val bucket = result.getOrPut(root.id) { HashMap() }
            bucket[child] = (bucket[child] ?: 0L) + s.totalRial
        }
        return result
    }

    /**
     * تنها تعریفِ بخش دسته‌ایِ «خرج»: دسته‌ای که خودش یا دسته‌ی اصلی‌اش «خرج نیست» (پس‌انداز، قرض دادن).
     * بخش تراکنشی (ناموفق، حذف‌شده، انتقال به خودم) در SQL است: COUNTED_FLOWS.
     */
    fun countsAsSpend(cat: CategoryEntity, root: CategoryEntity): Boolean = cat.countsAsSpend && root.countsAsSpend

    /** دسته‌هایی که خرج حساب نمی‌شوند (با زیردسته‌هایشان) */
    fun excludedIds(categories: List<CategoryEntity>): Set<Long> {
        val byId = categories.associateBy { it.id }
        return categories.filter { !countsAsSpend(it, rootOf(it, byId)) }.mapTo(HashSet()) { it.id }
    }

    /** فقط برداشت‌هایی که خرج حساب می‌شوند (بی‌دسته هم خرج است)؛ برای گزارش‌هایی که تراکنش‌ها را جدا لازم دارند */
    fun spendsOnly(rows: List<DatedAmount>, categories: List<CategoryEntity>): List<DatedAmount> {
        val excluded = excludedIds(categories)
        return rows.filter { it.categoryId == null || it.categoryId !in excluded }
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
