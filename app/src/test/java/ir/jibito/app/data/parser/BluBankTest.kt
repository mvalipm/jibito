package ir.jibito.app.data.parser

import ir.jibito.app.data.bank.BankDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** نمونه‌ی واقعی بلوبانک: مبلغ و موجودی برابرند (اولین واریز) — مانده نباید دور ریخته شود */
class BluBankTest {

    private val blu = BankDirectory.byId(40)!!

    private fun check(sms: String) {
        val tx = TransactionParser.parse(blu, sms)
        assertNotNull(sms, tx)
        assertEquals(FlowType.DEPOSIT, tx!!.type)
        assertEquals(1_000_000L, tx.amountRial)
        assertEquals(1_000_000L, tx.balanceRial)
    }

    @Test
    fun `واریز بلو - چندخطی`() = check(
        "بلو\nواریز پول\nمحمد عزیز، 1,000,000 ریال به حساب شما نشست.\nموجودی: 1,000,000 ریال\n۲۱:۴۸\n۱۴۰۵.۰۷.۰۹"
    )

    @Test
    fun `واریز بلو - یک‌خطی`() = check(
        "بلو واریز پول محمد عزیز، 1,000,000 ریال به حساب شما نشست. موجودی: 1,000,000 ریال ۲۱:۴۸ ۱۴۰۵.۰۷.۰۹"
    )

    @Test
    fun `برداشت بلو با موجودی متفاوت`() {
        val tx = TransactionParser.parse(
            blu,
            "بلو\nبرداشت پول\nمحمد عزیز، 250,000 ریال از حساب شما برداشت شد.\nموجودی: 750,000 ریال\n۲۲:۱۰\n۱۴۰۵.۰۷.۰۹",
        )
        assertNotNull(tx)
        assertEquals(FlowType.WITHDRAWAL, tx!!.type)
        assertEquals(250_000L, tx.amountRial)
        assertEquals(750_000L, tx.balanceRial)
    }
}
