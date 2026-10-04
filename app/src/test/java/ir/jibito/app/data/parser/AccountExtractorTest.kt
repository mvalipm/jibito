package ir.jibito.app.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountExtractorTest {

    private fun find(body: String) = AccountExtractor.find(SmsTextNormalizer.normalize(body))

    @Test
    fun `حساب خود پیامک در قالب‌های مختلف`() {
        assertEquals("41007", find("بانك ملي ايران\nاصـلاحيه:1,000,000+\nحساب:41007\nمانده:21,814,555\n0524-19:02"))
        assertEquals("0101XXXX", find("بانک صادرات\nبرداشت:۲۵۰,۰۰۰-\nحساب:۰۱۰۱XXXX\nمانده:۱,۲۳۴,۵۶۷"))
        assertEquals("1234567890", find("حساب1234567890\nبرداشت150,000\nمانده2,345,000"))
        assertEquals("123456789", find("بانک ملت\nبرداشت\nمبلغ:1,000,000\nمانده حساب 123456789: 2,000,000"))
        assertEquals("123", find("بانک سامان\nبرداشت از حساب 123\nمبلغ 350,000"))
        assertEquals("1111222233", find("بانک ایکس\nکسر از حساب 1111222233\n350,000 ریال"))
        assertEquals("123", find("حکمت\nواریز به حساب 123\nمبلغ:400,000"))
        assertEquals("6037***1234", find("کارت 6037***1234\n1,500,000-"))
        assertEquals("6104", find("بانک ملت\nبرداشت: 1,250,000\nمانده: 41,230,000\nکارت 6104"))
    }

    @Test
    fun `حساب مقصد انتقال، حساب خود پیامک نیست`() {
        assertEquals(null, find("برداشت\nانتقال به حساب 1234567890123\nمبلغ:5,000,000\nمانده:1,000,000"))
    }

    @Test
    fun `پیامک بدون شماره‌حساب`() {
        assertEquals(null, find("انصار\nبرداشت از حساب\n300,000 ریال\nمانده:700,000"))
    }
}
