package ir.jibito.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTreeTest {

    private val transport = Category(1, "حمل‌ونقل", "🚗", null, 2)
    private val car = Category(2, "خودرو شخصی", null, null, 2, parentId = 1)
    private val fuel = Category(3, "سوخت", null, null, 2, parentId = 2)
    private val byId = listOf(transport, car, fuel).associateBy { it.id }

    @Test
    fun `عمق و دسته‌ی اصلی`() {
        assertEquals(3, CategoryTree.depthOf(fuel, byId))
        assertEquals(transport, CategoryTree.rootOf(fuel, byId))
    }

    @Test
    fun `پیشنهاد در عمق مجاز`() {
        assertEquals(transport, CategoryTree.atDepth(fuel, byId, 1))
        assertEquals(car, CategoryTree.atDepth(fuel, byId, 2))
        assertEquals(fuel, CategoryTree.atDepth(fuel, byId, 3))
    }

    @Test
    fun `همان کار با جدول پدرها (برای CategoryEntity)`() {
        val parentOf = mapOf(1L to null, 2L to 1L, 3L to 2L)
        assertEquals(1L, CategoryTree.idAtDepth(3, parentOf, 1))
        assertEquals(2L, CategoryTree.idAtDepth(3, parentOf, 2))
        assertEquals(3L, CategoryTree.idAtDepth(3, parentOf, 3))
        // دسته‌ی کم‌عمق‌تر از حد، خودش می‌ماند
        assertEquals(1L, CategoryTree.idAtDepth(1, parentOf, 2))
        // پدرِ ناپیدا (مثلاً بایگانی‌شده): بالاترین چیزی که پیدا شد
        assertEquals(2L, CategoryTree.idAtDepth(3, mapOf(3L to 2L), 1))
    }

    @Test
    fun `پیشنهاد ردیف تراکنش در عمق مجاز و بدون دسته‌ی پنهان`() {
        val income = Category(4, "سوخت", null, null, 1) // هم‌نام، ولی دسته‌ی درآمد
        val all = listOf(transport, car, fuel, income)
        fun at(name: String?, depth: Int, hidden: Set<Long> = emptySet()) =
            CategoryTree.suggestionAt(name, 2, all, byId, depth, hidden)

        assertEquals(fuel, at("سوخت", 3))
        assertEquals(car, at("سوخت", 2))
        assertEquals(transport, at("سوخت", 1))
        // دسته‌ی اصلی‌اش پنهان است ← پیشنهادی نیست
        assertEquals(null, at("سوخت", 1, hidden = setOf(1L)))
        // دسته‌ی ناشناخته یا بی‌پیشنهاد
        assertEquals(null, at("چیزی که نیست", 3))
        assertEquals(null, at(null, 3))
        // نوع تراکنش مهم است: واریز ← دسته‌ی درآمد
        assertEquals(income, CategoryTree.suggestionAt("سوخت", 1, all, byId, 3, emptySet()))
    }
}
