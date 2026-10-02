package ir.jibito.app.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecurringFundsTest {

    @Test
    fun `مانده‌ی همه‌ی بانک‌ها با هم حساب می‌شود`() {
        assertEquals(2_000_000L, RecurringFunds.shortfall(listOf(3_000_000, 3_000_000), 8_000_000))
        assertNull(RecurringFunds.shortfall(listOf(5_000_000, 3_000_000), 8_000_000))
    }

    @Test
    fun `بدون مانده‌ی معلوم چیزی نمی‌گوید`() {
        assertNull(RecurringFunds.shortfall(emptyList(), 8_000_000))
    }
}
