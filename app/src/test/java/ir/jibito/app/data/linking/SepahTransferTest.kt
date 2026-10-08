package ir.jibito.app.data.linking

import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.TransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نمونه‌های کاربر (سپه، رمز «انتقال وجه پایا عادی» و «انتقال وجه پل»): خط «به:» فقط شباست،
 * اسم گیرنده در «به نام» می‌آید؛ پایین پیامک هم قالب Web OTP («@ebank… #رمز») است.
 * (اسم‌ها و شماره‌ها ساختگی‌اند.)
 */
class SepahTransferTest {

    private val sepah = BankDirectory.byId(24)!!

    private val payaOtp = "بانک سپه\nانتقال وجه پایا عادی\n 3120001234567 \nبه:  IR120170000000123456789001 \n" +
        "مبلغ 385,000,000 ريال\nبه نام: علی رضايی‌ پور\nرمز: 507289\nZwFQsa9kcO+\n@ebank.sphbank.ir #507289\nزمان اعتبار 14:51:16"

    private val polOtp = "بانک سپه\nانتقال وجه پل\n 3120001234567 \nبه:  IR120170000000123456789001 \n" +
        "مبلغ 299,500,000 ريال\nبه نام: علی رضايی‌ پور\nرمز: 620526\nZwFQsa9kcO+\n@ebank.banksepah.ir #620526\nزمان اعتبار 19:25:12"

    @Test
    fun `رمز پایا و پل سپه تراکنش نیستند`() {
        assertNull(TransactionParser.parse(sepah, payaOtp))
        assertNull(TransactionParser.parse(sepah, polOtp))
    }

    @Test
    fun `رمز پایا سپه - انتقال، مبلغ و اسم گیرنده`() {
        val otp = TransactionParser.parseOtp(payaOtp)!!
        assertEquals(385_000_000L, otp.amountRial)
        assertEquals("علی رضایی‌ پور", otp.merchant)
        assertTrue(otp.isTransfer)
    }

    @Test
    fun `رمز پل سپه - انتقال، مبلغ و اسم گیرنده`() {
        val otp = TransactionParser.parseOtp(polOtp)!!
        assertEquals(299_500_000L, otp.amountRial)
        assertEquals("علی رضایی‌ پور", otp.merchant)
        assertTrue(otp.isTransfer)
    }

    @Test
    fun `برداشت سپه با کارمزد به رمز پل وصل می‌شود`() {
        val otp = TransactionParser.parseOtp(polOtp)!!
        val withdrawal = TransactionParser.parse(sepah, "بانک سپه\n-299,520,000\nحساب 123\nمانده 4,000,000\n19:23 1405,07,16")!!
        assertEquals(FlowType.WITHDRAWAL, withdrawal.type)
        val linked = PurchaseLinker.link(
            listOf(TxRecord(2, 60_000, sepah.id, withdrawal)),
            listOf(OtpRecord(1, 0, sepah.id, otp)),
        ).single()
        assertEquals("علی رضایی‌ پور", linked.merchant)
        assertEquals(20_000L, linked.feeRial)
    }
}
