package ir.jibito.app.data.category

import ir.jibito.app.data.category.ReplyCategoryMatcher.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** نوشته‌ی جعبه‌ی «بنویس» نوتیفیکیشن ← دسته، فقط وقتی مطمئنیم. */
class ReplyCategoryMatcherTest {

    // بخشی از درخت دسته‌های خرج (مثل Taxonomy)
    private val food = Candidate(1, "خوراک", null)
    private val market = Candidate(2, "سوپرمارکت", 1)
    private val bread = Candidate(3, "نان", 1)
    private val dining = Candidate(10, "رستوران و کافه", null)
    private val restaurant = Candidate(11, "رستوران و فست‌فود", 10)
    private val cafe = Candidate(12, "کافه", 10)
    private val transport = Candidate(20, "حمل‌ونقل", null)
    private val taxi = Candidate(21, "تاکسی اینترنتی", 20)
    private val clothing = Candidate(30, "پوشاک", null)
    private val clothes = Candidate(31, "لباس", 30)
    private val otherA = Candidate(40, "سایر", 1)
    private val otherB = Candidate(41, "سایر", 30)

    private val all = listOf(food, market, bread, dining, restaurant, cafe, transport, taxi, clothing, clothes, otherA, otherB)

    private fun match(text: String) = ReplyCategoryMatcher.match(text, all)

    @Test
    fun exactName() {
        assertEquals(cafe.id, match("کافه"))
        assertEquals(taxi.id, match("تاکسی اینترنتی"))
        // نیم‌فاصله، فاصله‌ی اضافه، ی/ک عربی
        assertEquals(taxi.id, match("  تاكسي   اینترنتی "))
        assertEquals(transport.id, match("حمل و نقل"))
    }

    @Test
    fun partOfName() {
        assertEquals(restaurant.id, match("فست فود"))
        assertEquals(restaurant.id, match("فستفود"))
    }

    @Test
    fun partOnSameBranchPicksTheMostSpecific() {
        // «رستوران» هم در «رستوران و کافه» هست هم در زیردسته‌اش «رستوران و فست‌فود» ← زیردسته
        assertEquals(restaurant.id, match("رستوران"))
    }

    @Test
    fun oneTypo() {
        assertEquals(market.id, match("سوپرمارکِت"))
        assertEquals(market.id, match("سوپرمرکت"))
        // متن کوتاه: غلط تایپی پذیرفته نمی‌شود
        assertNull(match("نام"))
    }

    @Test
    fun categoryNameInsideText() {
        assertEquals(clothes.id, match("خرید لباس بچه"))
        assertEquals(bread.id, match("نان و پنیر"))
    }

    @Test
    fun keywordDictionary() {
        assertEquals(taxi.id, match("اسنپ تا فرودگاه"))
        assertEquals(cafe.id, match("قهوه با دوستان"))
    }

    @Test
    fun noMatchStaysANote() {
        assertNull(match("قرض به علی"))
        assertNull(match("شام تولد مامان"))
        assertNull(match(""))
        assertNull(match("   "))
        assertNull(match("ا"))
    }

    @Test
    fun ambiguousUnrelatedCategoriesPickNothing() {
        assertNull(match("سایر"))
    }

    @Test
    fun keywordWhoseCategoryIsMissingPicksNothing() {
        // فرهنگ «اسنپ» را تاکسی اینترنتی می‌داند ولی این کاربر چنین دسته‌ای ندارد
        assertNull(ReplyCategoryMatcher.match("اسنپ", listOf(food, cafe)))
    }

    @Test
    fun persianDigitsAndPunctuation() {
        assertEquals("قسط 2 وام", ReplyCategoryMatcher.normalize("قسط ۲، وام!"))
    }

    @Test
    fun editDistance() {
        assertTrue(ReplyCategoryMatcher.withinOneEdit("abcd", "abxd"))
        assertTrue(ReplyCategoryMatcher.withinOneEdit("abcd", "abd"))
        assertTrue(ReplyCategoryMatcher.withinOneEdit("abd", "abcd"))
        assertTrue(ReplyCategoryMatcher.withinOneEdit("abcd", "abcde"))
        assertFalse(ReplyCategoryMatcher.withinOneEdit("abcd", "axxd"))
        assertFalse(ReplyCategoryMatcher.withinOneEdit("abcd", "ab"))
    }
}
