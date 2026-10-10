package ir.jibito.app.ui.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerDotsTest {

    @Test
    fun `تا هفت مورد، نقطه‌ی فعال همان صفحه است`() {
        for (count in 2..MAX_PAGER_DOTS) {
            for (page in 0 until count) assertEquals(page, pagerDotIndex(page, count))
        }
    }

    @Test
    fun `بیشتر از هفت مورد، اولی اول و آخری آخر و وسط‌ها به ترتیب`() {
        assertEquals(0, pagerDotIndex(0, 30))
        assertEquals(MAX_PAGER_DOTS - 1, pagerDotIndex(29, 30))
        val indices = (0 until 30).map { pagerDotIndex(it, 30) }
        assertTrue(indices.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(indices.all { it in 0 until MAX_PAGER_DOTS })
    }

    @Test
    fun `صفحه‌ی بیرون از محدوده یا یک مورد، خراب نمی‌شود`() {
        assertEquals(0, pagerDotIndex(0, 1))
        assertEquals(0, pagerDotIndex(5, 0))
        assertEquals(MAX_PAGER_DOTS - 1, pagerDotIndex(100, 30))
    }
}
