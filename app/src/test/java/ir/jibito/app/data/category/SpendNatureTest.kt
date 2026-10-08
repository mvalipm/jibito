package ir.jibito.app.data.category

import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.DatedAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpendNatureTest {

    private val home = CategoryEntity(id = 1, name = "خانه", code = "home")
    private val rent = CategoryEntity(id = 2, name = "اجاره", code = "home.rent", parentId = 1)
    private val furniture = CategoryEntity(id = 3, name = "اثاث", code = "home.furniture", parentId = 1)
    private val repair = CategoryEntity(id = 4, name = "تعمیر", code = "home.repair", parentId = 1)
    private val customSub = CategoryEntity(id = 5, name = "باغچه", parentId = 1, isCustom = true)
    private val customRoot = CategoryEntity(id = 6, name = "کار", isCustom = true)
    private val dining = CategoryEntity(id = 7, name = "رستوران", code = "dining")
    private val savings = CategoryEntity(id = 8, name = "پس‌انداز", code = "savings", countsAsSpend = false)
    private val all = listOf(home, rent, furniture, repair, customSub, customRoot, dining, savings)
    private val byId = all.associateBy { it.id }

    @Test
    fun `پیش‌فرض از زیردسته، بعد از دسته‌ی بالاتر`() {
        assertEquals(Nature.MUST, SpendNature.defaultFor("home.rent"))
        assertEquals(Nature.NEED, SpendNature.defaultFor("home.repair"))
        assertEquals(Nature.NEED, SpendNature.defaultFor("transport.car.fuel"))
        assertEquals(Nature.MUST, SpendNature.defaultFor("finance.bills.water"))
        assertEquals(Nature.WANT, SpendNature.defaultFor("finance.gifts"))
        assertNull(SpendNature.defaultFor("savings"))
        assertNull(SpendNature.defaultFor(null))
    }

    @Test
    fun `انتخاب کاربر، پیش‌فرض و دسته‌ی بالاتر`() {
        assertEquals(Nature.MUST, SpendNature.of(rent, byId))
        assertEquals(Nature.WANT, SpendNature.of(furniture, byId))
        // زیردسته‌ی شخصی زیر «خانه» ← ماهیت خانه
        assertEquals(Nature.NEED, SpendNature.of(customSub, byId))
        // دسته‌ی شخصیِ اصلی، تا کاربر انتخاب نکرده
        assertNull(SpendNature.of(customRoot, byId))
        // انتخاب کاربر بر پیش‌فرض مقدم است، و زیردسته‌های شخصی‌اش هم از آن پیروی می‌کنند
        val homeWant = byId + (1L to home.copy(nature = Nature.WANT.code))
        assertEquals(Nature.WANT, SpendNature.of(customSub, homeWant))
        assertEquals(Nature.MUST, SpendNature.of(rent, homeWant))
    }

    @Test
    fun `حلقه‌ی اشتباهی در درخت گیر نمی‌اندازد`() {
        val a = CategoryEntity(id = 20, name = "a", parentId = 21)
        val b = CategoryEntity(id = 21, name = "b", parentId = 20)
        assertNull(SpendNature.of(a, mapOf(20L to a, 21L to b)))
    }

    @Test
    fun `سهم هر ماهیت بدون یک‌باره‌ها و بیرون از خرج`() {
        val rows = listOf(
            DatedAmount(2, 60_000_000, 10),
            DatedAmount(4, 20_000_000, 11),
            DatedAmount(7, 15_000_000, 12),
            DatedAmount(7, 5_000_000, 13),
            DatedAmount(null, 0, 14),
            DatedAmount(6, 100_000_000, 15, isOneOff = true),
            DatedAmount(8, 500_000_000, 16),
            // بیرون از بازه
            DatedAmount(7, 99_000_000, 100),
        )
        val r = NatureReport.compute(rows, all, from = 0, to = 50)
        assertEquals(100_000_000L, r.totalRial)
        assertEquals(60, r.percent(Nature.MUST))
        assertEquals(20, r.percent(Nature.NEED))
        assertEquals(20, r.percent(Nature.WANT))
        assertEquals(listOf(NatureItem(7, 20_000_000)), r.items[Nature.WANT])
        assertNull(r.change(Nature.WANT))
    }

    @Test
    fun `تغییر نسبت به همین موقعِ ماه قبل`() {
        val rows = listOf(
            DatedAmount(7, 10_000_000, 5),
            DatedAmount(7, 14_000_000, 105),
            DatedAmount(2, 60_000_000, 6),
            DatedAmount(2, 60_000_000, 106),
        )
        val r = NatureReport.compute(rows, all, from = 100, to = 200, previousFrom = 0, previousTo = 100)
        assertEquals(40, r.change(Nature.WANT))
        // اجاره تغییری نکرده
        assertNull(r.change(Nature.MUST))
    }
}
