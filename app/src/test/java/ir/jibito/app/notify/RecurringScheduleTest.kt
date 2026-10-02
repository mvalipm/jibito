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

    @Test
    fun `ماه بعد که علامت خورده، یعنی این ماه هم انجام شده`() {
        // «پرداخت کردم» روی «فردا موعد…» در روز آخر ماه، ماه بعد را علامت می‌زند
        assertFalse(RecurringSchedule.isDue(1, 140508, 1405, 7, 30, 20))
    }

    @Test
    fun `یادآوری عصر روز قبل از موعد`() {
        // موعد روز ۵؛ فردا ۵ مهر است
        assertTrue(RecurringSchedule.isDueTomorrow(5, null, 1405, 7, 5, 18))
        assertFalse("not before evening", RecurringSchedule.isDueTomorrow(5, null, 1405, 7, 5, 17))
        assertFalse("tomorrow is not the due day", RecurringSchedule.isDueTomorrow(5, null, 1405, 7, 6, 20))
        assertFalse("already done for that month", RecurringSchedule.isDueTomorrow(5, 140507, 1405, 7, 5, 20))
        // موعد روز ۱: فردا اول ماه بعد است
        assertTrue(RecurringSchedule.isDueTomorrow(1, 140507, 1405, 8, 1, 19))
        // موعد ۳۱ در ماه ۳۰ روزه ← فردا روز ۳۰
        assertTrue(RecurringSchedule.isDueTomorrow(31, null, 1405, 7, 30, 19))
    }

    @Test
    fun `بعداً یادم بنداز: صبح و ظهر ← امشب ساعت ۲۰، عصر ← فردا ساعت ۹`() {
        val morning = java.util.Calendar.getInstance().apply { clear(); set(2026, 9, 3, 10, 30) }.timeInMillis
        val tonight = java.util.Calendar.getInstance().apply { clear(); set(2026, 9, 3, 20, 0) }.timeInMillis
        assertTrue(RecurringSchedule.snoozesTonight(morning))
        org.junit.Assert.assertEquals(tonight, RecurringSchedule.snoozeUntil(morning))

        val evening = java.util.Calendar.getInstance().apply { clear(); set(2026, 9, 3, 18, 0) }.timeInMillis
        val tomorrow = java.util.Calendar.getInstance().apply { clear(); set(2026, 9, 4, 9, 0) }.timeInMillis
        assertFalse(RecurringSchedule.snoozesTonight(evening))
        org.junit.Assert.assertEquals(tomorrow, RecurringSchedule.snoozeUntil(evening))
    }
}
