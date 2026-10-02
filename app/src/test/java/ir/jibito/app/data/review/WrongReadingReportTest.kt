package ir.jibito.app.data.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
