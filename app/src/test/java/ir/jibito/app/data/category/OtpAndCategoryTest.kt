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
        assertEquals("تاکسی اینترنتی", CategorySuggester.suggest("اسنپ"))
        assertEquals("رستوران و فست‌فود", CategorySuggester.suggest("اسنپ فود"))
        assertEquals("اقامت", CategorySuggester.suggest("اسنپ تریپ"))
        assertNull(CategorySuggester.suggest("فروشگاه اینترنتی دیجی کالا"))
        assertEquals("شارژ موبایل", CategorySuggester.suggest("ایرانسل"))
        assertEquals("بسته اینترنت موبایل", CategorySuggester.suggest("ایرانسل - بسته اینترنت"))
        assertEquals("اینترنت خانگی", CategorySuggester.suggest("شاتل"))
        assertNull(CategorySuggester.suggest("کارت/حساب …5678"))
        assertNull(CategorySuggester.suggest(null))
    }

    // ---------- طرف حساب از متن خود پیامک برداشت (برای یادگیری) ----------

    private fun merchant(sms: String) =
        ir.jibito.app.data.parser.MerchantExtractor.find(SmsTextNormalizer.normalize(sms))

    @Test
    fun `اسم فروشگاه بدون تاریخ و ساعت`() {
        assertEquals("رفاه", merchant("خرید از فروشگاه رفاه 1405/07/09 12:30\nمبلغ:350,000\nمانده:1,000,000"))
    }

    @Test
    fun `انتقال به حساب - همیشه چهار رقم آخر`() {
        assertEquals("کارت/حساب …0123", merchant("برداشت\nانتقال به حساب 1234567890123\nمبلغ:5,000,000\nمانده:1,000,000"))
    }

    @Test
    fun `برداشت از حساب خودم طرف حساب نیست`() {
        assertNull(merchant("بانک ملت\nبرداشت از 1234567890\nمبلغ:700,000\nمانده:1,300,000"))
    }
}
