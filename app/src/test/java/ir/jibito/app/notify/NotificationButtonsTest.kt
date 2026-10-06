package ir.jibito.app.notify

import ir.jibito.app.data.local.entity.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** دکمه‌های دسته‌ی نوتیفیکیشن: پیشنهاد اپ و پرکاربردها، در عمقی که کاربر در تنظیمات گذاشته. */
class NotificationButtonsTest {

    private val transport = CategoryEntity(id = 1, name = "حمل‌ونقل")
    private val car = CategoryEntity(id = 2, name = "خودرو شخصی", parentId = 1)
    private val fuel = CategoryEntity(id = 3, name = "سوخت", parentId = 2)
    private val taxi = CategoryEntity(id = 4, name = "تاکسی اینترنتی", parentId = 1)
    private val dining = CategoryEntity(id = 5, name = "رستوران و کافه")
    private val cafe = CategoryEntity(id = 6, name = "کافه", parentId = 5)
    private val food = CategoryEntity(id = 7, name = "خوراک")

    /** ترتیب «بیشترین استفاده»: کافه، تاکسی، خوراک، … */
    private val byUsage = listOf(cafe, taxi, food, transport, car, fuel, dining)

    private fun names(suggested: String?, depth: Int, count: Int = 3, hidden: Set<Long> = emptySet()) =
        NotificationButtons.choose(suggested, byUsage, count, depth, hidden).map { it.name }

    @Test
    fun `همه‌ی لایه‌ها ← همان دسته‌ی دقیق`() {
        assertEquals(listOf("سوخت", "کافه", "تاکسی اینترنتی"), names("سوخت", depth = 3))
    }

    @Test
    fun `فقط دسته‌ی اصلی ← پیشنهاد و پرکاربردها بالا می‌روند و تکراری حذف می‌شود`() {
        // سوخت ← حمل‌ونقل، کافه ← رستوران و کافه، تاکسی ← حمل‌ونقل (تکراری)، خوراک
        assertEquals(listOf("حمل‌ونقل", "رستوران و کافه", "خوراک"), names("سوخت", depth = 1))
    }

    @Test
    fun `تا زیردسته ← جزئیات به زیردسته می‌رسد`() {
        assertEquals(listOf("خودرو شخصی", "کافه", "تاکسی اینترنتی"), names("سوخت", depth = 2))
    }

    @Test
    fun `بی‌پیشنهاد یا پیشنهاد ناشناخته`() {
        assertEquals(listOf("رستوران و کافه", "حمل‌ونقل", "خوراک"), names(null, depth = 1))
        assertEquals(listOf("کافه", "تاکسی اینترنتی"), names("چیزی که نیست", depth = 3, count = 2))
    }

    @Test
    fun `«سایر» دکمه نمی‌شود، مگر پیشنهاد خود اپ باشد`() {
        val other = CategoryEntity(id = 9, name = "سایر", flowType = 1)
        val salary = CategoryEntity(id = 8, name = "حقوق", flowType = 1)
        assertEquals(listOf(salary), NotificationButtons.choose(null, listOf(other, salary), 3, 1, emptySet()))
        assertEquals(listOf(other, salary), NotificationButtons.choose("سایر", listOf(other, salary), 3, 1, emptySet()))
    }

    @Test
    fun `دسته‌ی اصلی پنهان دکمه نمی‌شود (نه پیشنهاد، نه زیردسته‌هایش)`() {
        // «حمل‌ونقل» پنهان ← سوخت و تاکسی هم نمی‌آیند
        assertEquals(listOf("کافه", "خوراک", "رستوران و کافه"), names("سوخت", depth = 3, hidden = setOf(1L)))
        assertEquals(listOf("رستوران و کافه", "خوراک"), names("سوخت", depth = 1, hidden = setOf(1L)))
    }
}
