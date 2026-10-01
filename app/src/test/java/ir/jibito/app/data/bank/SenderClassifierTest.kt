package ir.jibito.app.data.bank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderClassifierTest {

    private fun bankNameOf(sender: String): String? =
        (SenderClassifier.classify(sender) as? SenderType.BankSender)?.bank?.name

    @Test
    fun `شکل‌های مختلف یک سرشماره به یک بانک می‌رسند`() {
        assertEquals("بانک پاسارگاد", bankNameOf("+9820009000"))
        assertEquals("بانک پاسارگاد", bankNameOf("9820009000"))
        assertEquals("بانک پاسارگاد", bankNameOf("20009000"))
        assertEquals("بانک پاسارگاد", bankNameOf("B.Pasargad"))
    }

    @Test
    fun `باگ اپ قدیمی - 20003304 مال بانک ملت است نه ملی`() {
        assertEquals("بانک ملت", bankNameOf("20003304"))
        assertEquals("بانک ملت", bankNameOf("+9820003304"))
    }

    @Test
    fun `سرشماره‌های انگلیسی`() {
        assertEquals("بانک تجارت", bankNameOf("TejaratBank"))
        assertEquals("بانک ملت", bankNameOf("Bank Mellat"))
        assertEquals("بانک سامان", bankNameOf("Saman Bank"))
    }

    @Test
    fun `بلوبانک با اینکه شبیه موبایل است شخصی حساب نمی‌شود`() {
        assertEquals("بلوبانک", bankNameOf("0999987641"))
        assertEquals("بلوبانک", bankNameOf("+98999987641"))
    }

    @Test
    fun `شماره‌های موبایل شخصی`() {
        assertEquals(SenderType.Personal, SenderClassifier.classify("09121234567"))
        assertEquals(SenderType.Personal, SenderClassifier.classify("+989351234567"))
        assertEquals(SenderType.Personal, SenderClassifier.classify("00989011234567"))
        assertTrue(SenderClassifier.isPersonalMobileNumber("0912 123 4567"))
        assertFalse(SenderClassifier.isPersonalMobileNumber("98200033"))
    }

    @Test
    fun `اپراتور و سرشماره‌ی ناشناس`() {
        assertEquals(SenderType.Unknown, SenderClassifier.classify("HAMRAH_AVAL"))
        assertEquals(SenderType.Unknown, SenderClassifier.classify("+98500030075204"))
        assertEquals(SenderType.Unknown, SenderClassifier.classify(""))
    }

    @Test
    fun `هیچ سرشماره‌ای بین دو بانک مشترک نیست`() {
        val all = BankDirectory.banks.flatMap { it.senders }
        assertEquals(all.size, all.toSet().size)
    }
}
