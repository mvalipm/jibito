package ir.jibito.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class JalaliTest {

    @Test
    fun `نوروزهای شناخته‌شده`() {
        assertEquals(Triple(2023, 3, 21), Jalali.toGregorian(1402, 1, 1))
        assertEquals(Triple(2024, 3, 20), Jalali.toGregorian(1403, 1, 1))
        assertEquals(Triple(2025, 3, 21), Jalali.toGregorian(1404, 1, 1))
        assertEquals(Triple(1403, 1, 1), Jalali.fromGregorian(2024, 3, 20))
    }

    @Test
    fun `رفت و برگشت برای همه‌ی روزهای چند سال`() {
        for (jy in 1399..1410) for (jm in 1..12) {
            val days = if (jm <= 6) 31 else if (jm <= 11) 30 else 29
            for (jd in 1..days) {
                val (gy, gm, gd) = Jalali.toGregorian(jy, jm, jd)
                assertEquals("$jy/$jm/$jd", Triple(jy, jm, jd), Jalali.fromGregorian(gy, gm, gd))
            }
        }
    }

    @Test
    fun `ماه قبل و بعد`() {
        assertEquals(JalaliMonth(1405, 1), JalaliMonth(1404, 12).plus(1))
        assertEquals(JalaliMonth(1404, 12), JalaliMonth(1405, 1).plus(-1))
        assertEquals(140507, JalaliMonth(1405, 7).key)
    }

    @Test
    fun `مرز ماه‌ها پشت سر هم است`() {
        val m = JalaliMonth(1405, 6)
        assertEquals(m.endMillis(), m.plus(1).startMillis())
        assertEquals(m, JalaliMonth.of(m.startMillis()))
        assertEquals(m, JalaliMonth.of(m.endMillis() - 1))
    }
}
