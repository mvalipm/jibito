package ir.jibito.app.domain

/**
 * کمک‌های درخت دسته‌ها برای نمایش.
 * عمق: دسته‌ی اصلی = ۱، زیردسته = ۲، جزئیات = ۳.
 */
object CategoryTree {

    fun depthOf(c: Category, byId: Map<Long, Category>): Int {
        var d = 1
        var current = c
        while (current.parentId != null && d < 10) {
            current = byId[current.parentId] ?: break
            d++
        }
        return d
    }

    /** دسته‌هایی مثل پس‌انداز (یا زیردسته‌هایشان) که خرج حساب نمی‌شوند؛ همان قاعده‌ی SpendRollup.excludedIds */
    fun nonSpendIds(categories: List<Category>): Set<Long> {
        val byId = categories.associateBy { it.id }
        return categories.filter { !it.countsAsSpend || !rootOf(it, byId).countsAsSpend }.mapTo(HashSet()) { it.id }
    }

    fun rootOf(c: Category, byId: Map<Long, Category>): Category {
        var current = c
        var steps = 0
        while (current.parentId != null && steps < 10) {
            current = byId[current.parentId] ?: break
            steps++
        }
        return current
    }

    /** آیکون نزدیک‌ترین دسته‌ی بالادستی که آیکون دارد (زیردسته‌ی شخصی می‌تواند آیکون خودش را داشته باشد) */
    fun iconOf(c: Category, byId: Map<Long, Category>): String? {
        var current = c
        var steps = 0
        while (current.icon == null && current.parentId != null && steps < 10) {
            current = byId[current.parentId] ?: break
            steps++
        }
        return current.icon
    }

    /**
     * دسته‌ی هم‌ارز در عمق مجاز: اگر کاربر فقط «دسته‌ی اصلی» را می‌بیند، پیشنهادِ «سوخت» ← «حمل‌ونقل».
     */
    fun atDepth(c: Category, byId: Map<Long, Category>, maxDepth: Int): Category {
        var current = c
        while (depthOf(current, byId) > maxDepth) {
            current = current.parentId?.let { byId[it] } ?: break
        }
        return current
    }
}
