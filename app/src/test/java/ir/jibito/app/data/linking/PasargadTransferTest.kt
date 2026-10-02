package ir.jibito.app.data.linking

import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.data.parser.PurchaseOtp
import ir.jibito.app.data.parser.TransactionParser
import ir.jibito.app.data.transfer.TransferCandidate
import ir.jibito.app.data.transfer.TransferMatcher
import ir.jibito.app.data.transfer.TransferPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** نمونه‌های واقعی پاسارگاد (کارت‌به‌کارت): رمز ۱۰٬۰۰۰٬۰۰۰ و برداشت ۱۰٬۰۱۱٬۰۰۰ = ۱۱٬۰۰۰ ریال کارمزد */
class PasargadTransferTest {

    private val pasargad = BankDirectory.byId(12)!!
    private val otpSms = "پاسارگاد\nانتقال به 610433*2805\nمبلغ:10,000,000\nرمز: 44410\n19:50:19"
    private val withdrawalSms = "777.888.16305454.1\n-10,011,000\n07/09_19:50\nمانده: 2,254,180"

    @Test
    fun `رمز کارت‌به‌کارت - انتقال، مبلغ و مقصد`() {
        assertNull(TransactionParser.parse(pasargad, otpSms)) // رمز، تراکنش نیست
        val otp = TransactionParser.parseOtp(otpSms)
        assertNotNull(otp)
        assertEquals(10_000_000L, otp!!.amountRial)
        assertTrue(otp.isTransfer)
        assertTrue(otp.merchant.orEmpty().contains("2805"))
    }

    @Test
    fun `برداشت پاسارگاد - مبلغ با منفی و مانده`() {
        val tx = TransactionParser.parse(pasargad, withdrawalSms)
        assertNotNull(tx)
        assertEquals(FlowType.WITHDRAWAL, tx!!.type)
        assertEquals(10_011_000L, tx.amountRial)
        assertEquals(2_254_180L, tx.balanceRial)
    }

    @Test
    fun `رمز انتقال و برداشت با کارمزد به هم وصل می‌شوند`() {
        val linked = PurchaseLinker.link(
            listOf(TxRecord(2, 30_000, 12, ParsedTransaction(FlowType.WITHDRAWAL, 10_011_000, 2_254_180))),
            listOf(OtpRecord(1, 0, 12, PurchaseOtp(10_000_000, "کارت/حساب …2805", isTransfer = true))),
        ).single()
        assertEquals("کارت/حساب …2805", linked.merchant)
        assertEquals(11_000L, linked.feeRial)
    }

    @Test
    fun `قانون همه‌ی بانک‌ها - اختلاف زیر ۲٪ حتی بدون کلمه‌ی انتقال در رمز`() {
        val linked = PurchaseLinker.link(
            listOf(TxRecord(2, 30_000, 5, ParsedTransaction(FlowType.WITHDRAWAL, 1_011_000, null))),
            listOf(OtpRecord(1, 0, 5, PurchaseOtp(1_000_000, "کارت/حساب …1234", isTransfer = false))),
        ).single()
        assertEquals("کارت/حساب …1234", linked.merchant)
        assertEquals(11_000L, linked.feeRial)
    }

    @Test
    fun `اختلاف ۲٪ یا بیشتر وصل نمی‌شود`() {
        val linked = PurchaseLinker.link(
            listOf(TxRecord(2, 30_000, 5, ParsedTransaction(FlowType.WITHDRAWAL, 1_020_000, null))),
            listOf(OtpRecord(1, 0, 5, PurchaseOtp(1_000_000, "اسنپ"))),
        ).single()
        assertNull(linked.merchant)
    }

    @Test
    fun `مرز ۲٪`() {
        assertTrue(PurchaseLinker.isTransferFee(10_011_000, 10_000_000))   // ۰٫۱۱٪
        assertTrue(PurchaseLinker.isTransferFee(10_190_000, 10_000_000))   // ۱٫۹٪
        assertTrue(!PurchaseLinker.isTransferFee(10_200_000, 10_000_000))  // ۲٪ (زیر ۲٪ نیست)
        assertTrue(!PurchaseLinker.isTransferFee(10_000_000, 10_000_000))  // برابر = خرید، کارمزد ندارد
        assertTrue(!PurchaseLinker.isTransferFee(9_990_000, 10_000_000))   // برداشت کمتر از رمز
    }

    @Test
    fun `رمز بعد از ۳ دقیقه وصل نمی‌شود`() {
        val linked = PurchaseLinker.link(
            listOf(TxRecord(2, 3 * 60_000 + 1, 12, ParsedTransaction(FlowType.WITHDRAWAL, 10_011_000, null))),
            listOf(OtpRecord(1, 0, 12, PurchaseOtp(10_000_000, "کارت/حساب …2805", isTransfer = true))),
        ).single()
        assertNull(linked.merchant)
    }

    @Test
    fun `انتقال به حساب خودم با کارمزد هم پیشنهاد می‌شود`() {
        val pairs = TransferMatcher.findPairs(
            listOf(
                TransferCandidate(1, true, 10_011_000, 0),
                TransferCandidate(2, false, 10_000_000, 60_000),
            )
        )
        assertEquals(listOf(TransferPair(1, 2)), pairs)
    }
}
