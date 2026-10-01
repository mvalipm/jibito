package ir.jibito.app.data.category

import ir.jibito.app.data.parser.OtpParser
import ir.jibito.app.data.parser.SmsTextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** تست استخراج مقصد از پیامک رمز دوم + پیشنهاد دسته. (نمونه‌ها ساختگی‌اند.) */
class OtpAndCategoryTest {

    private fun otp(sms: String) = OtpParser.parse(SmsTextNormalizer.normalize(sms))

    @Test
    fun `مقصد و مبلغ از رمز دوم با کلید پذیرنده`() {
        val o = otp("رمز پویا\nپذیرنده: اسنپ\nمبلغ: 1,250,000 ریال\nرمز: 482915\nاعتبار تا 14:22:10")!!
        assertEquals("اسنپ", o.merchant)
        assertEquals(1_250_000L, o.amountRial)
    }

    @Test
    fun `مقصد با خرید از و مبلغ تومانی`() {
        val o = otp("خرید از دیجی کالا\nمبلغ 350,000 تومان\nرمز دوم: 77123\n")!!
        assertEquals("دیجی کالا", o.merchant)
        assertEquals(3_500_000L, o.amountRial)
    }

    @Test
    fun `انتقال به کارت - مقصد کوتاه می‌شود`() {
        val o = otp("پاسارگاد\nانتقال به\n6037991234565678\nمبلغ:20,000,000\nرمز: 41926\n08:07:46")!!
        assertEquals("کارت/حساب …5678", o.merchant)
        assertEquals(20_000_000L, o.amountRial)
    }

    @Test
    fun `کد فعال‌سازی رمز خرید نیست`() {
        assertNull(otp("کد فعالسازی شما در دیما\ncode: 884716"))
    }

    @Test
    fun `پیامک معمولی رمز نیست`() {
        assertNull(otp("بانک ملت\nبرداشت از 1234\nمبلغ:700,000\nمانده:1,300,000"))
    }

    @Test
    fun `پیشنهاد دسته`() {
        assertEquals("رفت‌وآمد", CategorySuggester.suggest("اسنپ"))
        assertEquals("غذا", CategorySuggester.suggest("اسنپ فود"))
        assertEquals("سفر", CategorySuggester.suggest("اسنپ تریپ"))
        assertEquals("خرید", CategorySuggester.suggest("فروشگاه اینترنتی دیجی کالا"))
        assertEquals("قبض و شارژ", CategorySuggester.suggest("ایرانسل"))
        assertNull(CategorySuggester.suggest("کارت/حساب …5678"))
        assertNull(CategorySuggester.suggest(null))
    }
}
