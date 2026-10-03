package ir.jibito.app.ui.smslist

import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.domain.BankBalance
import org.junit.Assert.assertEquals
import org.junit.Test

class WalletTotalTest {

    private fun balance(bankId: Int, rial: Long) = BankBalance(BankDirectory.byId(bankId)!!, rial, 0L)

    private val balances = listOf(balance(1, 1_000), balance(12, 20_000), balance(15, 300_000))

    @Test
    fun `all accounts count when none is excluded`() {
        assertEquals(321_000L, includedTotal(balances, emptySet()))
    }

    @Test
    fun `excluded account is left out of the total`() {
        assertEquals(301_000L, includedTotal(balances, setOf(12)))
    }

    @Test
    fun `excluding every account gives zero`() {
        assertEquals(0L, includedTotal(balances, setOf(1, 12, 15)))
    }
}
