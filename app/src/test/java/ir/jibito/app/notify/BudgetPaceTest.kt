package ir.jibito.app.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetPaceTest {

    private val budget = 10_000_000L

    @Test
    fun `نصف بودجه در روز ۱۰ یعنی با این سرعت رد می‌شود`() {
        assertTrue(BudgetPace.onPaceToOverrun(spentRial = 5_000_000, budgetRial = budget, day = 10, monthLength = 30))
        assertEquals(BudgetPace.LEVEL, BudgetPace.alertLevel(5_000_000, budget, 10, 30))
    }

    @Test
    fun `نصف بودجه در روز ۱۶ طبیعی است`() {
        assertFalse(BudgetPace.onPaceToOverrun(5_000_000, budget, 16, 30))
        assertEquals(0, BudgetPace.alertLevel(5_000_000, budget, 16, 30))
    }

    @Test
    fun `چند روز اول ماه سرعت معنی ندارد (مثلاً اجاره)`() {
        assertFalse(BudgetPace.onPaceToOverrun(6_000_000, budget, 3, 30))
    }

    @Test
    fun `کمتر از نصف بودجه هشدار سرعت نمی‌دهد`() {
        assertFalse(BudgetPace.onPaceToOverrun(4_900_000, budget, 5, 30))
    }

    @Test
    fun `سطح‌های ۸۰ و ۱۰۰ جای هشدار سرعت را می‌گیرند`() {
        assertEquals(80, BudgetPace.alertLevel(8_000_000, budget, 10, 30))
        assertEquals(100, BudgetPace.alertLevel(10_000_000, budget, 29, 30))
    }

    @Test
    fun `روز تمام شدن بودجه با همین سرعت`() {
        // ۵ میلیون در ۱۰ روز ← ۱۰ میلیون در روز ۲۰
        assertEquals(20, BudgetPace.runOutDay(5_000_000, budget, 10, 30))
        // تا آخر ماه تمام نمی‌شود
        assertNull(BudgetPace.runOutDay(3_000_000, budget, 15, 30))
        // همین حالا تمام شده
        assertNull(BudgetPace.runOutDay(10_000_000, budget, 15, 30))
    }

    @Test
    fun `روزی چقدر جا داری`() {
        // ۶ میلیون مانده، روز ۲۱ از ۳۰ ← ۱۰ روز با امروز
        assertEquals(600_000, BudgetPace.dailyAllowance(4_000_000, budget, 21, 30))
        assertEquals(0, BudgetPace.dailyAllowance(12_000_000, budget, 21, 30))
        // روز آخر: کل مانده برای امروز
        assertEquals(1_000_000, BudgetPace.dailyAllowance(9_000_000, budget, 30, 30))
    }
}
