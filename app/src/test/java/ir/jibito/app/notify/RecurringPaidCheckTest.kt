package ir.jibito.app.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringPaidCheckTest {

    @Test
    fun `همان مبلغ یا با کارمزد کوچک یعنی پرداخت شده`() {
        assertTrue(RecurringPaidCheck.alreadyPaid(listOf(1_200_000, 5_000_000), 5_000_000))
        // کارت‌به‌کارت با کارمزد ۲٬۵۰۰ تومان
        assertTrue(RecurringPaidCheck.alreadyPaid(listOf(5_025_000), 5_000_000))
    }

    @Test
    fun `مبلغ دیگر یا هیچ برداشتی یعنی هنوز نه`() {
        assertFalse(RecurringPaidCheck.alreadyPaid(listOf(4_000_000, 6_000_000), 5_000_000))
        assertFalse(RecurringPaidCheck.alreadyPaid(emptyList(), 5_000_000))
        assertFalse(RecurringPaidCheck.alreadyPaid(listOf(0), 0))
    }

    @Test
    fun `پنجره از ۱۰ روز قبل از موعد`() {
        val due = 1_000_000_000_000L
        assertEquals(due - 10 * 24L * 60 * 60 * 1000, RecurringPaidCheck.windowStart(due))
    }
}
