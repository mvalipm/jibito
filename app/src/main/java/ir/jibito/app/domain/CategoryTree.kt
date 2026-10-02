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

    fun rootOf(c: Category, byId: Map<Long, Category>): Category {
        var current = c
        var steps = 0
        while (current.parentId != null && steps < 10) {
            current = byId[current.parentId] ?: break
            steps++
        }
        return current
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
