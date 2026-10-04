package ir.jibito.app.ui.transfers

import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.domain.Transaction
import ir.jibito.app.domain.TransferSuggestion
import org.junit.Assert.assertEquals
import org.junit.Test

class TransferGroupsTest {

    private fun tx(id: Long, date: Long, type: FlowType, bankId: Int?) = Transaction(
        id = id, bank = BankDirectory.byId(bankId), body = "", dateMillis = date,
        transaction = ParsedTransaction(type, 1_000_000, null), merchant = null, suggestedCategory = null,
        isFailedPurchase = false, categoryId = null, categoryName = null, categoryIcon = null, isAutoCategorized = false,
    )

    private fun pair(id: Long, date: Long, from: Int?, to: Int?) =
        TransferSuggestion(tx(id, date, FlowType.WITHDRAWAL, from), tx(id + 100, date + 1, FlowType.DEPOSIT, to))

    @Test
    fun groupsByBankPairLargestFirst() {
        val groups = groupTransfers(
            listOf(pair(1, 10, 11, 15), pair(2, 20, 11, 11), pair(3, 30, 11, 11), pair(4, 40, null, 11)),
        )
        assertEquals(listOf(2, 1, 1), groups.map { it.items.size })
        // پرتعدادترین اول؛ داخل الگو تازه‌ترین اول
        assertEquals(listOf(3L, 2L), groups[0].items.map { it.withdrawal.id })
        // هم‌تعدادها: الگویی که تازه‌ترین مورد را دارد اول
        assertEquals(listOf(4L, 1L), groups.drop(1).map { it.items.first().withdrawal.id })
        assertEquals(null, groups[1].from)
        assertEquals(2_000_000L, groups[0].totalRial)
    }
}
