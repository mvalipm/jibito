package ir.jibito.app.ui.sleep

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTextTest {

    private val minute = 60_000L

    @Test
    fun `تأخیر به دقیقه، ساعت یا روز`() {
        assertEquals(LagUnit.MINUTES to 45, lagUnit(45 * minute))
        assertEquals(LagUnit.HOURS to 2, lagUnit(2 * 60 * minute + 40 * minute))
        assertEquals(LagUnit.DAYS to 3, lagUnit(3 * 24 * 60 * minute + minute))
    }

    @Test
    fun `برند از سازنده‌ی گوشی`() {
        assertEquals(PhoneBrand.XIAOMI, PhoneBrand.of("Xiaomi"))
        assertEquals(PhoneBrand.XIAOMI, PhoneBrand.of("POCO"))
        assertEquals(PhoneBrand.SAMSUNG, PhoneBrand.of("samsung"))
        assertEquals(PhoneBrand.HUAWEI, PhoneBrand.of("HONOR"))
        assertEquals(PhoneBrand.OTHER, PhoneBrand.of("Google"))
        assertEquals(PhoneBrand.OTHER, PhoneBrand.of(null))
    }
}
