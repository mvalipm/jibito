package ir.jibito.app.data.category

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantWordModelTest {

    private val cafe = 1L
    private val pharmacy = 2L
    private val market = 3L

    private val trained = MerchantWordModel(
        listOf(
            "کافه آرتا" to cafe,
            "کافه لمیز" to cafe,
            "داروخانه دکتر رضایی" to pharmacy,
            "داروخانه شبانه روزی" to pharmacy,
        )
    )

    @Test
    fun `فروشگاه تازه با کلمه‌ی مشترک دسته‌ی فروشگاه‌های قبلی را می‌گیرد`() {
        assertEquals(cafe, trained.predict("کافه نادری"))
        assertEquals(pharmacy, trained.predict("داروخانه سلامت"))
    }

    @Test
    fun `اسم بدون کلمه‌ی آشنا ← هیچ`() {
        assertNull(trained.predict("زنبیل دانا"))
        assertNull(trained.predict(""))
    }

    @Test
    fun `فقط یک فروشگاه با کلمه‌ی مشترک کافی نیست`() {
        val model = MerchantWordModel(listOf("کافه آرتا" to cafe, "داروخانه الف" to pharmacy))
        assertNull(model.predict("کافه نادری"))
    }

    @Test
    fun `یک فروشگاه که چند بار تکرار شده یک نمونه حساب می‌شود`() {
        val model = MerchantWordModel(listOf("کافه آرتا" to cafe, "کافه آرتا" to cafe, "داروخانه الف" to pharmacy))
        assertNull(model.predict("کافه نادری"))
    }

    @Test
    fun `کلمه‌ی عمومی که بین چند دسته پخش است ← هیچ`() {
        val model = MerchantWordModel(
            listOf(
                "فروشگاه آرمان" to market,
                "فروشگاه بهار" to market,
                "فروشگاه جام" to cafe,
                "فروشگاه دنا" to cafe,
            )
        )
        assertNull(model.predict("فروشگاه پارس"))
    }

    @Test
    fun `کلمه‌ی مشترک و کلمه‌ی ناآشنا با هم جواب می‌دهند`() {
        // «نادری» و «آرتا» هیچ‌کدام مهم نیستند؛ کلمه‌ی «کافه» تصمیم می‌گیرد
        assertEquals(cafe, trained.predict("کافه‌ی تازه نادری"))
    }

    @Test
    fun `انتقال به کارت یک شخص یاد گرفته نمی‌شود`() {
        val model = MerchantWordModel(listOf("کارت/حساب …1234" to cafe, "کارت/حساب …5678" to cafe))
        assertNull(model.predict("کارت/حساب …9999"))
    }

    @Test
    fun `بدون هیچ نمونه‌ای ← هیچ`() {
        assertNull(MerchantWordModel(emptyList()).predict("کافه نادری"))
    }
}
