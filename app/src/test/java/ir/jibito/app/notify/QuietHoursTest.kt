package ir.jibito.app.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {

    @Test
    fun `از ۲۳ تا ۷ صبح بی‌صدا`() {
        assertTrue(QuietHours.isQuiet(23))
        assertTrue(QuietHours.isQuiet(0))
        assertTrue(QuietHours.isQuiet(2))
        assertTrue(QuietHours.isQuiet(6))
        assertFalse(QuietHours.isQuiet(7))
        assertFalse(QuietHours.isQuiet(12))
        assertFalse(QuietHours.isQuiet(22))
    }
}
