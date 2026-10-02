package ir.jibito.app.ui.welcome

import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.JalaliMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class RevealStatsTest {

    private fun tx(id: Long, date: Long, bankId: Int?) = Transaction(
        id = id, bank = BankDirectory.byId(bankId), body = "", dateMillis = date,
        transaction = ParsedTransaction(FlowType.WITHDRAWAL, 1), merchant = null, suggestedCategory = null,
        isFailedPurchase = false, categoryId = null, categoryName = null, categoryIcon = null, isAutoCategorized = false,
    )

    @Test
    fun `تعداد، ماه‌ها و بانک‌ها`() {
        val m = JalaliMonth(1404, 7)
        val s = RevealStats.of(
            listOf(
                tx(1, m.startMillis() + 1, 11),
                tx(2, m.startMillis() + 2, 11),
                tx(3, m.plus(-1).startMillis() + 1, 15),
                tx(4, m.plus(-3).startMillis() + 1, null),
            )
        )
        assertEquals(RevealStats(count = 4, months = 3, banks = 2), s)
    }
}
