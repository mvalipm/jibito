package ir.jibito.app.data.repository

import ir.jibito.app.data.category.Taxonomy
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.CategorySum
import ir.jibito.app.util.JalaliMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/** خودپرداز خرج نیست و برگشت پول درآمد نیست (سند EventTaxonomy) — ولی هر دو جدا دیده می‌شوند. */
class CashAndRefundSummaryTest {

    private val food = CategoryEntity(id = 1, name = "خوراک", flowType = 2, code = "food")
    private val cash = CategoryEntity(id = 2, name = "پول نقد (خودپرداز)", flowType = 2, countsAsSpend = false, code = Taxonomy.CODE_CASH)
    private val savings = CategoryEntity(id = 3, name = "پس‌انداز و قرض", flowType = 2, countsAsSpend = false, code = "savings")
    private val salary = CategoryEntity(id = 10, name = "حقوق", flowType = 1)
    private val refund = CategoryEntity(id = 11, name = "برگشت پول", flowType = 1, countsAsSpend = false, code = Taxonomy.CODE_REFUND)

    @Test
    fun cashIsNeitherSpendNorSavingsAndRefundIsNotIncome() {
        val s = BudgetRepositoryImpl.buildSummary(
            month = JalaliMonth(1405, 7),
            spendSums = listOf(CategorySum(1, 1_000_000), CategorySum(2, 5_000_000), CategorySum(3, 2_000_000), CategorySum(null, 300_000)),
            incomeSums = listOf(CategorySum(10, 40_000_000), CategorySum(11, 700_000)),
            categories = listOf(food, cash, savings, salary, refund),
            budgets = emptyMap(),
            overallBudgetRial = null,
        )
        assertEquals(1_300_000L, s.totalSpentRial)
        assertEquals(5_000_000L, s.cashRial)
        assertEquals(2_000_000L, s.excludedRial)
        assertEquals(40_000_000L, s.totalIncomeRial)
        assertEquals(700_000L, s.refundRial)
        // «برگشت پول» در فهرست دسته‌های درآمد هم نمی‌آید
        assertEquals(listOf("حقوق"), s.incomeCategories.map { it.name })
    }

    @Test
    fun cashAndRefundCategoriesAreDefaults() {
        assertEquals(false, Taxonomy.byCode(Taxonomy.CODE_CASH)?.countsAsSpend)
        assertEquals(false, Taxonomy.byCode(Taxonomy.CODE_REFUND)?.countsAsSpend)
    }
}
