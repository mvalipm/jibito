package ir.jibito.app.data.repository

import ir.jibito.app.data.category.Taxonomy
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.CategorySum
import ir.jibito.app.util.JalaliMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** برگشت پول درآمد نیست (سند EventTaxonomy) ولی جدا دیده می‌شود؛ خودپرداز مثل هر برداشتی خرج است. */
class CashAndRefundSummaryTest {

    private val food = CategoryEntity(id = 1, name = "خوراک", flowType = 2, code = "food")
    private val savings = CategoryEntity(id = 3, name = "پس‌انداز و قرض", flowType = 2, countsAsSpend = false, code = "savings")
    private val salary = CategoryEntity(id = 10, name = "حقوق", flowType = 1)
    private val refund = CategoryEntity(id = 11, name = "برگشت پول", flowType = 1, countsAsSpend = false, code = Taxonomy.CODE_REFUND)

    @Test
    fun refundIsNotIncome() {
        val s = BudgetRepositoryImpl.buildSummary(
            month = JalaliMonth(1405, 7),
            // ۵ میلیون بی‌دسته = مثلاً برداشت خودپرداز؛ خرج حساب می‌شود
            spendSums = listOf(CategorySum(1, 1_000_000), CategorySum(3, 2_000_000), CategorySum(null, 5_000_000)),
            incomeSums = listOf(CategorySum(10, 40_000_000), CategorySum(11, 700_000)),
            categories = listOf(food, savings, salary, refund),
            budgets = emptyMap(),
            overallBudgetRial = null,
        )
        assertEquals(6_000_000L, s.totalSpentRial)
        assertEquals(2_000_000L, s.excludedRial)
        assertEquals(40_000_000L, s.totalIncomeRial)
        assertEquals(700_000L, s.refundRial)
        // «برگشت پول» در فهرست دسته‌های درآمد هم نمی‌آید
        assertEquals(listOf("حقوق"), s.incomeCategories.map { it.name })
    }

    @Test
    fun refundIsADefaultCategoryAndThereIsNoCashCategory() {
        assertEquals(false, Taxonomy.byCode(Taxonomy.CODE_REFUND)?.countsAsSpend)
        // برداشت از خودپرداز خرج است؛ دسته‌ی «بیرون از خرج» برایش نداریم
        assertNull(Taxonomy.byCode("cash"))
    }
}
