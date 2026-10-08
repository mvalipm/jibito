package ir.jibito.app.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «اسم فروشگاه کدومه؟»: یاد گرفتن شکل پیامک و خواندن اسم از همان خط در پیامک‌های هم‌شکل */
class MerchantRulesTest {

    // نمونه‌ی واقعی (پوشیده) پاسارگاد
    private val snapp = "پاسارگاد\nخرید\nاسنپ مارکت\nمبلغ:12,321,000\nرمز: 04720\n09:57:36"
    private val digikala = "پاسارگاد\nخرید\nدیجی کالا\nمبلغ:850,000\nرمز: 91833\n21:04:10"

    @Test
    fun `خط‌های عددی انتخاب‌شدنی نیستند`() {
        val lines = MerchantRules.lines(snapp)
        assertEquals(6, lines.size)
        assertTrue(MerchantRules.isPickable(lines[2]))
        assertFalse(MerchantRules.isPickable(lines[3])) // مبلغ
        assertFalse(MerchantRules.isPickable(lines[4])) // رمز
        assertFalse(MerchantRules.isPickable(lines[5])) // ساعت
        assertNull(MerchantRules.skeleton(snapp, 3))
        assertNull(MerchantRules.skeleton(snapp, 99))
    }

    @Test
    fun `شکل یادگرفته روی فروشگاه دیگر با همان شکل کار می‌کند`() {
        val skeleton = MerchantRules.skeleton(snapp, 2)
        assertNotNull(skeleton)
        assertEquals("اسنپ مارکت", MerchantRules.match(snapp, skeleton!!))
        assertEquals("دیجی کالا", MerchantRules.match(digikala, skeleton))
    }

    @Test
    fun `پیامک با شکل دیگر جور نیست`() {
        val skeleton = MerchantRules.skeleton(snapp, 2)!!
        // رمز انتقال: خط «خرید» نیست
        assertNull(MerchantRules.match("پاسارگاد\nانتقال به 610433*2805\nمبلغ:10,000,000\nرمز: 44410\n19:50:19", skeleton))
        // یک خط بیشتر
        assertNull(MerchantRules.match("$snapp\nلغو11", skeleton))
        // خط اسم این بار عدد دارد
        assertNull(MerchantRules.match("پاسارگاد\nخرید\n6037991234565678\nمبلغ:1,000\nرمز: 11111\n10:00:00", skeleton))
    }

    @Test
    fun `اول تازه‌ترین شکل`() {
        val atStore = MerchantRules.skeleton(snapp, 2)!!
        val atBank = MerchantRules.skeleton(snapp, 0)!!
        assertEquals("پاسارگاد", MerchantRules.find(snapp, listOf(atBank, atStore)))
        assertEquals("اسنپ مارکت", MerchantRules.find(snapp, listOf(atStore, atBank)))
        assertNull(MerchantRules.find(null, listOf(atStore)))
    }
}
