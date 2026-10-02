package ir.jibito.app.ui.smslist

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionFilterTest {

    private val day = 1_759_400_000_000L

    private fun tx(id: Long, amountRial: Long, merchant: String? = null, categoryId: Long? = null, type: FlowType = FlowType.WITHDRAWAL) =
        Transaction(
            id = id, bank = null, body = "", dateMillis = day - id * 60_000, transaction = ParsedTransaction(type, amountRial),
            merchant = merchant, suggestedCategory = null, isFailedPurchase = false, categoryId = categoryId,
            categoryName = null, categoryIcon = null, isAutoCategorized = false,
        )

    private val all = listOf(
        tx(1, 1_250_000, merchant = "کافه نادری"),
        tx(2, 500_000, merchant = "اسنپ", categoryId = 10),
        tx(3, 9_000_000, type = FlowType.DEPOSIT),
        tx(4, 300_000, merchant = "قرض به علی", categoryId = 21),
    )

    private val categories = listOf(
        Category(10, "رفت‌وآمد", null, null, flowType = 2),
        Category(20, "پس‌انداز", null, null, flowType = 2, countsAsSpend = false),
        Category(21, "قرض دادم", null, null, flowType = 2, parentId = 20),
    )

    @Test
    fun `فقط خرج‌های بی‌دسته`() {
        val v = visibleTransactions(all, categories, onlyUncategorized = true, search = TxSearch(), now = day)
        assertEquals(listOf(1L), v.list.map { it.id })
    }

    @Test
    fun `جست‌وجو با اسم و با مبلغ به تومان`() {
        assertEquals(listOf(1L), visibleTransactions(all, categories, false, TxSearch(text = "نادری"), day).list.map { it.id })
        assertEquals(listOf(2L), visibleTransactions(all, categories, false, TxSearch(text = "۵۰٬۰۰۰"), day).list.map { it.id })
    }

    @Test
    fun `زیردسته‌ی پس‌انداز در جمع خرج روز نیست`() {
        val v = visibleTransactions(all, categories, false, TxSearch(), day)
        assertEquals(setOf(20L, 21L), nonSpendCategoryIds(categories))
        assertEquals(1_250_000L + 500_000L, v.groups.sumOf { it.spendRial })
    }
}
