package ir.jibito.app.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BalanceFinderTest {

    private fun n(s: String) = SmsTextNormalizer.normalize(s)

    @Test
    fun `شماره حساب در همان خط مانده`() {
        assertEquals(2_000_000L, BalanceFinder.find(n("بانک ملت\nبرداشت\nمبلغ:1,000,000\nمانده حساب 123456789: 2,000,000")))
    }

    @Test
    fun `تاریخ در همان خط مانده`() {
        assertEquals(5_400_000L, BalanceFinder.find(n("برداشت 300,000\nموجودی:5,400,000 1405/07/09-12:30")))
    }

    @Test
    fun `مانده در خط بعد`() {
        assertEquals(7_250_000L, BalanceFinder.find(n("خرید\n250,000\nمانده:\n7,250,000\n0709-1405")))
    }

    @Test
    fun `بدون مانده`() {
        assertNull(BalanceFinder.find(n("برداشت 300,000\n1405/07/09")))
        assertNull(BalanceFinder.find(n("مانده حساب شما کم است")))
    }

    @Test
    fun `ملت با شماره حساب در خط مانده، از مسیر کامل پارس`() {
        val bank = ir.jibito.app.data.bank.BankDirectory.byId(11)!!
        val tx = TransactionParser.parse(bank, "بانک ملت\nبرداشت\nمبلغ:1,000,000\nمانده حساب 123456789: 2,000,000\n1405/07/09")!!
        assertEquals(1_000_000L, tx.amountRial)
        assertEquals(2_000_000L, tx.balanceRial)
    }
}
