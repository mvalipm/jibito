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
}
