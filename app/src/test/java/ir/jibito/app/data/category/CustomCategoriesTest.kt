package ir.jibito.app.data.category

import ir.jibito.app.data.category.CreateCategoryResult.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomCategoriesTest {

    private val existing = listOf("سوپرمارکت", "اجاره و مسکن", "سوخت")

    @Test
    fun `اسم درست`() {
        assertNull(CustomCategories.validate("  قسط  ماشین ", existing))
        assertEquals("قسط ماشین", CustomCategories.clean("  قسط  ماشین "))
    }

    @Test
    fun `خالی یا خیلی بلند`() {
        assertEquals(Reason.EMPTY, CustomCategories.validate("   ", existing))
        assertEquals(Reason.TOO_LONG, CustomCategories.validate("ا".repeat(31), existing))
    }

    @Test
    fun `تکراری حتی با ی عربی یا نیم‌فاصله‌ی متفاوت`() {
        assertEquals(Reason.DUPLICATE, CustomCategories.validate("سوپرماركت", existing))
        assertEquals(Reason.DUPLICATE, CustomCategories.validate("اجاره و‌مسکن", existing))
    }
}
