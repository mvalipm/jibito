package ir.jibito.app.data.bank

import ir.jibito.app.data.parser.SmsTextNormalizer
import ir.jibito.app.data.review.ReviewDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluSenderTest {

    @Test
    fun `سرشماره‌ی شبه‌موبایل بلو، بانک شناخته می‌شود نه شخصی`() {
        val t = SenderClassifier.classify("09999987641")
        assertTrue(t is SenderType.BankSender)
        assertEquals(40, (t as SenderType.BankSender).bank.id)
        assertTrue(SenderClassifier.classify("+989999987641") is SenderType.BankSender)
    }

    @Test
    fun `موبایل واقعی همچنان شخصی است`() {
        assertEquals(SenderType.Personal, SenderClassifier.classify("09121234567"))
    }

    @Test
    fun `از شماره‌ی شبه‌شخصی فقط پیامک قطعاً بانکی به بررسی می‌رود`() {
        val bank = SmsTextNormalizer.normalize(
            "بلو واریز پول محمد عزیز، 1,000,000 ریال به حساب شما نشست. موجودی: 1,000,000 ریال ۲۱:۴۸ ۱۴۰۵.۰۷.۰۹"
        )
        assertTrue(ReviewDetector.isBankLikeFromPersonal(bank))
        // پیامک دوستانه: «موجودی» ندارد
        assertFalse(ReviewDetector.isBankLikeFromPersonal(SmsTextNormalizer.normalize("سلام، 1,000,000 ریال واریز کردم به حسابت")))
        assertFalse(ReviewDetector.isBankLikeFromPersonal(SmsTextNormalizer.normalize("موجودی یخچال تموم شده، نون هم بگیر")))
    }
}
