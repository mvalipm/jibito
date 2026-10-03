package ir.jibito.app.data.linking

import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.MerchantExtractor
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.data.parser.SmsTextNormalizer
import ir.jibito.app.data.parser.TransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * گزارش کاربر (کارگزاری مفید، «انتقال بین بانکی پل»): طرف حساب «بانک اقتصاد نوین» خوانده می‌شد،
 * چون «مقصد» پیش از «به نام» بررسی می‌شد و خط مقصد فقط شبا و اسم بانک است.
 * (اسم‌ها و شماره‌ها ساختگی‌اند.)
 */
class MofidTransferTest {

    private val otpSms = "حساب مفید (محرمانه)\nانتقال بین بانکی پل\nمقصد:\nIR720*74001 بانک اقتصاد نوین\n" +
        "به نام: سيما احمدی \nمبلغ: 3,000,000 ريال\nرمز: 878386\nاعتبار: 14:07:24\n@my.emofid.com #878386"

    @Test
    fun `رمز پل مفید - اسم گیرنده، نه اسم بانک`() {
        val otp = TransactionParser.parseOtp(otpSms)!!
        assertEquals("سیما احمدی", otp.merchant)
        assertEquals(3_000_000L, otp.amountRial)
        assertTrue(otp.isTransfer)
    }

    @Test
    fun `برداشت مفید با کارمزد به رمز وصل می‌شود`() {
        val otp = TransactionParser.parseOtp(otpSms)!!
        val linked = PurchaseLinker.link(
            listOf(TxRecord(2, 60_000, 1001, ParsedTransaction(FlowType.WITHDRAWAL, 3_008_000, 2_248_134))),
            listOf(OtpRecord(1, 0, 1001, otp)),
        ).single()
        assertEquals("سیما احمدی", linked.merchant)
        assertEquals(8_000L, linked.feeRial)
    }

    @Test
    fun `مقصد بی‌اسم گیرنده - شماره‌ی حساب، نه اسم بانک`() {
        val text = SmsTextNormalizer.normalize("انتقال بین بانکی پل\nمقصد:\nIR210*22501 بانک سامان\nمبلغ: 4,000,000 ريال")
        assertEquals("کارت/حساب …2501", MerchantExtractor.find(text))
    }

    @Test
    fun `سرشماره‌ی MofidCard شناخته می‌شود و برداشتش با کارمزد درست خوانده می‌شود`() {
        val bank = BankDirectory.findBySender("MofidCard")!!
        val sms = "کارگزاری مفید\nبرداشت مبلغ 3,008,000 ریال\nکارمزد بانک به مبلغ 8,000 ریال \nاز حساب حامی\n" +
            "مانده: 2,248,134 ریال\n1405/06/28\n14:05"
        val tx = TransactionParser.parse(bank, sms)!!
        assertEquals(FlowType.WITHDRAWAL, tx.type)
        assertEquals(3_008_000L, tx.amountRial)
        assertEquals(2_248_134L, tx.balanceRial)
        // رمز پل هم از همین سرشماره می‌آید و تراکنش حساب نمی‌شود
        assertEquals(null, TransactionParser.parse(bank, otpSms))
    }
}
