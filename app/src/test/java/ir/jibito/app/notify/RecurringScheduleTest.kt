package ir.jibito.app.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringScheduleTest {

    @Test
    fun monthLengths() {
        assertEquals(31, RecurringSchedule.monthLength(1404, 1))
        assertEquals(31, RecurringSchedule.monthLength(1404, 6))
        assertEquals(30, RecurringSchedule.monthLength(1404, 7))
        assertEquals(30, RecurringSchedule.monthLength(1404, 11))
        // اسفند ۱۴۰۳ کبیسه (۳۰ روز)، اسفند ۱۴۰۴ ۲۹ روز
        assertEquals(30, RecurringSchedule.monthLength(1403, 12))
        assertEquals(29, RecurringSchedule.monthLength(1404, 12))
    }

    @Test
    fun dueOnTheDayFromNineAndOnlyOncePerMonth() {
        assertFalse(RecurringSchedule.isDue(5, null, 1405, 7, 4, 12))
        assertFalse("not at midnight", RecurringSchedule.isDue(5, null, 1405, 7, 5, 0))
        assertTrue(RecurringSchedule.isDue(5, null, 1405, 7, 5, 9))
        assertFalse("already reminded this month", RecurringSchedule.isDue(5, 140507, 1405, 7, 5, 10))
        assertTrue("next month again", RecurringSchedule.isDue(5, 140507, 1405, 8, 5, 10))
    }

    @Test
    fun catchesUpIfThePhoneWasOff() {
        assertTrue(RecurringSchedule.isDue(5, 140506, 1405, 7, 9, 1))
    }

    @Test
    fun day31InShortMonthIsTheLastDay() {
        // مهر ۳۰ روزه است
        assertTrue(RecurringSchedule.isDue(31, null, 1405, 7, 30, 10))
        assertEquals(29, RecurringSchedule.dueDay(31, 1404, 12))
    }

    @Test
    fun lateOnlyAfterTheDueDay() {
        assertFalse(RecurringSchedule.isLate(11, 1405, 7, 11))
        assertTrue(RecurringSchedule.isLate(11, 1405, 7, 12))
        // روز ۳۱ در مهرِ ۳۰ روزه: روز ۳۰ دیر نیست
        assertFalse(RecurringSchedule.isLate(31, 1405, 7, 30))
    }

    @Test
    fun newPaymentWhoseDayPassedWaitsForNextMonth() {
        assertEquals(140507, RecurringSchedule.initialRemindedKey(5, 1405, 7, 20))
        assertNull(RecurringSchedule.initialRemindedKey(25, 1405, 7, 20))
    }
}
