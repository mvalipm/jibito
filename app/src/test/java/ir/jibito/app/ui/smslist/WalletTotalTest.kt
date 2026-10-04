package ir.jibito.app.ui.smslist

import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.wallet.WalletExclusion
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
        assertEquals(301_000L, includedTotal(balances, setOf(WalletExclusion.key(12, null))))
    }

    @Test
    fun `excluding every account gives zero`() {
        assertEquals(0L, includedTotal(balances, setOf(WalletExclusion.key(1, null), WalletExclusion.wholeBank(12), WalletExclusion.key(15, null))))
    }

    @Test
    fun `two accounts of one bank are both counted and excluded separately`() {
        val mellat = BankDirectory.byId(11)!!
        val two = listOf(BankBalance(mellat, 214_000_000, 0L, "1234"), BankBalance(mellat, 125_000_000, 0L, "5678"))
        assertEquals(339_000_000L, includedTotal(two, emptySet()))
        assertEquals(214_000_000L, includedTotal(two, setOf(WalletExclusion.key(11, "5678"))))
    }

    @Test
    fun `label is the user's name, else bank and last four digits`() {
        val mellat = BankDirectory.byId(11)!!
        assertEquals("ملت ۵۶۷۸", accountLabel(BankBalance(mellat, 1, 0L, "6104XXXX5678")))
        assertEquals("ملت حقوق", accountLabel(BankBalance(mellat, 1, 0L, "6104XXXX5678", "ملت حقوق")))
        assertEquals("ملت", accountLabel(BankBalance(mellat, 1, 0L)))
    }
}
