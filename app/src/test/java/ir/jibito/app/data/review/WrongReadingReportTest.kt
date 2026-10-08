package ir.jibito.app.data.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.domain.Transaction
import org.junit.Test

class WrongReadingReportTest {

    @Test
    fun `مبلغ و مانده برچسب می‌خورند و بقیه‌ی رقم‌ها پوشانده می‌شوند`() {
        val body = "بانک ملت\nبرداشت: 1,250,000\nمانده: 41,230,000\nکارت 6104\n1404/07/10-12:30"
        val out = WrongReadingReport.labelAndMask(body, amountRial = 1_250_000, balanceRial = 41_230_000)
        assertEquals("بانک ملت\nبرداشت: [مبلغ]\nمانده: [مانده]\nکارت ####\n####/##/##-##:##", out)
    }

    @Test
    fun `پیامک به تومان و رقم فارسی`() {
        val out = WrongReadingReport.labelAndMask("خرید ۱۲۵٬۰۰۰ تومان", amountRial = 1_250_000, balanceRial = null)
        assertEquals("خرید [مبلغ] تومان", out)
    }

    @Test
    fun `هیچ رقمی بیرون نمی‌رود وقتی چیزی جور نیست`() {
        val out = WrongReadingReport.labelAndMask("واریز 999 مانده 12.5", amountRial = 5, balanceRial = 7)
        assertFalse(out.any { it.isDigit() })
    }

    private val tx = Transaction(
        id = 1, bank = null, body = "برداشت مبلغ 3,008,000 ریال", dateMillis = 0,
        transaction = ParsedTransaction(FlowType.WITHDRAWAL, 3_008_000), merchant = null, suggestedCategory = null,
        isFailedPurchase = false, categoryId = null, categoryName = null, categoryIcon = null, isAutoCategorized = false,
    )

    @Test
    fun `علت، سرشماره و نسخه در گزارش`() {
        val out = WrongReadingReport.text(tx, WrongReadingReport.Reason.SHOULD_BE_TRANSFER, "MofidCard", "0.46.4")
        assertTrue(out.contains("مشکل: باید انتقال باشه"))
        assertTrue(out.contains("سرشماره: MofidCard"))
        assertTrue(out.contains("نسخه‌ی اپ: 0.46.4"))
        assertTrue(out.contains("برداشت مبلغ [مبلغ] ریال"))
    }

    @Test
    fun `اسم فروشگاه رو نخوند - متن رمز دوم هم با رقم‌های پوشیده می‌رود`() {
        val purchase = tx.copy(
            body = "777.888.16305454.1\n-12,321,000\n07/16_09:57\nمانده: 13,451,930",
            transaction = ParsedTransaction(FlowType.WITHDRAWAL, 12_321_000, 13_451_930),
            otpBody = "پاسارگاد\nخرید\nاسنپ مارکت\nمبلغ:12,321,000\nرمز: 04720\n09:57:36",
        )
        val out = WrongReadingReport.text(purchase, WrongReadingReport.Reason.MERCHANT_MISSING)
        assertTrue(out.contains("مشکل: اسم فروشگاه رو نخوند"))
        assertTrue(out.contains("——— پیامک رمز دوم\nپاسارگاد\nخرید\nاسنپ مارکت\nمبلغ:[مبلغ]\nرمز: #####\n##:##:##"))
        assertFalse("no digit leaves the phone", out.any { it.isDigit() })
    }

    @Test
    fun `سرشماره‌ی شبه‌موبایل پوشانده می‌شود`() {
        assertEquals("###########", WrongReadingReport.maskSender("09121234567"))
        assertEquals("200033", WrongReadingReport.maskSender("200033"))
    }
}
