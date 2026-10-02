package ir.jibito.app.data.category

import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.CategorySum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaxonomyTest {

    private val all = Taxonomy.flatten(Taxonomy.expense)

    @Test
    fun `اسم‌ها و کدها تکراری نیستند (پیشنهاد دسته با اسم پیدا می‌شود)`() {
        assertEquals(all.size, all.map { it.name }.toSet().size)
        assertEquals(all.size + Taxonomy.income.size, (all + Taxonomy.income).map { it.code }.toSet().size)
    }

    @Test
    fun `۱۳ دسته‌ی اصلی خرج + پس‌انداز و قرض، بدون «سایر»`() {
        assertEquals(13, Taxonomy.expense.count { it.countsAsSpend })
        assertFalse(all.any { it.name == "سایر" })
        assertFalse(Taxonomy.expense.single { it.code == "savings" }.countsAsSpend)
    }

    @Test
    fun `حداکثر سه لایه، و کد هر زیردسته با کد پدرش شروع می‌شود`() {
        fun check(defs: List<CategoryDef>, depth: Int, parent: String?) {
            for (d in defs) {
                assertTrue(depth <= 3)
                if (parent != null) assertTrue(d.code.startsWith("$parent."))
                check(d.children, depth + 1, d.code)
            }
        }
        check(Taxonomy.expense, 1, null)
    }

    @Test
    fun `همه‌ی پیشنهادهای کلمه‌ای و انتقال‌های قدیمی به دسته‌ی موجود می‌رسند`() {
        val names = all.map { it.name }.toSet()
        for (t in CategorySuggester.targets) assertTrue("پیشنهاد «$t» در ساختار نیست", t in names)
        for (code in Taxonomy.OLD_TO_NEW.values.filterNotNull()) assertTrue(code, Taxonomy.byCode(code) != null)
    }

    @Test
    fun `جمع روی دسته‌ی اصلی، و پس‌انداز بیرون از خرج`() {
        val transport = CategoryEntity(id = 1, name = "حمل‌ونقل")
        val car = CategoryEntity(id = 2, name = "خودرو شخصی", parentId = 1)
        val fuel = CategoryEntity(id = 3, name = "سوخت", parentId = 2)
        val savings = CategoryEntity(id = 4, name = "پس‌انداز و قرض", countsAsSpend = false)
        val invest = CategoryEntity(id = 5, name = "پس‌انداز", parentId = 4, countsAsSpend = false)
        val r = SpendRollup.rollup(
            listOf(transport, car, fuel, savings, invest),
            listOf(CategorySum(3, 500), CategorySum(1, 200), CategorySum(5, 1_000), CategorySum(null, 70), CategorySum(99, 30)),
        )
        assertEquals(mapOf(1L to 700L), r.byRoot)
        assertEquals(100L, r.uncategorized)
        assertEquals(1_000L, r.excluded)
        assertEquals(800L, r.total)
    }
}
