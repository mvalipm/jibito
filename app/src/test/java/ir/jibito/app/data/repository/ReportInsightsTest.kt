package ir.jibito.app.data.repository

import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.DatedAmount
import ir.jibito.app.util.JalaliMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReportInsightsTest {

    private val mehr = JalaliMonth(1405, 7)
    private val shahrivar = JalaliMonth(1405, 6)
    private val day = 24L * 60 * 60 * 1000
    private val noon = 12L * 60 * 60 * 1000
    private lateinit var savedZone: TimeZone

    private val food = CategoryEntity(id = 1, name = "رستوران", icon = "🍽")
    private val taxi = CategoryEntity(id = 2, name = "حمل‌ونقل", icon = "🚕")
    private val snapp = CategoryEntity(id = 3, name = "اسنپ", parentId = 2)
    private val savings = CategoryEntity(id = 4, name = "پس‌انداز", countsAsSpend = false)
    private val categories = listOf(food, taxi, snapp, savings)

    @Before
    fun fixZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"))
    }

    @After
    fun restoreZone() = TimeZone.setDefault(savedZone)

    private fun on(month: JalaliMonth, dayOfMonth: Int, rial: Long, category: Long?) =
        DatedAmount(category, rial, month.startMillis() + (dayOfMonth - 1) * day + noon)

    @Test
    fun `بیشترین افزایش و کاهش دسته‌ها نسبت به همین موقع ماه قبل`() {
        val now = mehr.startMillis() + 9 * day + noon // ۱۰ مهر
        val rows = listOf(
            on(shahrivar, 3, 10_000_000L, 1),
            on(shahrivar, 25, 50_000_000L, 1), // بعد از روز ۱۰: در مقایسه نیست
            on(shahrivar, 4, 20_000_000L, 3), // اسنپ ← حمل‌ونقل
            on(mehr, 2, 16_000_000L, 1),
            on(mehr, 5, 12_000_000L, 2),
            on(mehr, 6, 90_000_000L, 4), // پس‌انداز خرج نیست
        )
        val changes = ReportInsights.categoryChanges(rows, categories.associateBy { it.id }, mehr, now)
        assertEquals(2, changes.size)
        val up = changes[0]
        assertEquals("رستوران", up.name)
        assertEquals(6_000_000L, up.deltaRial)
        assertEquals(60, up.percent)
        val down = changes[1]
        assertEquals(2L, down.categoryId)
        assertEquals(-8_000_000L, down.deltaRial)
        assertEquals(-40, down.percent)

        val all = ReportInsights.compute(rows, categories, mehr, now)
        assertTrue(all.none { it is Insight.CategoryChange && it.categoryId == 4L })
    }

    @Test
    fun `تغییر کوچک یا پایه‌ی کوچک نکته نیست`() {
        val now = mehr.startMillis() + 9 * day + noon
        val rows = listOf(
            on(shahrivar, 3, 10_000_000L, 1),
            on(mehr, 3, 11_000_000L, 1), // ۱۰٪
            on(shahrivar, 3, 500_000L, 2), // پایه کمتر از ۱۰۰ هزار تومان
            on(mehr, 3, 9_000_000L, 2),
        )
        assertTrue(ReportInsights.categoryChanges(rows, categories.associateBy { it.id }, mehr, now).isEmpty())
    }

    @Test
    fun `پرخرج‌ترین روز ماه`() {
        val rows = listOf(
            on(mehr, 2, 3_000_000L, null),
            on(mehr, 6, 4_000_000L, null),
            on(mehr, 6, 5_000_000L, 1),
        )
        val busiest = ReportInsights.busiestDay(rows, mehr)!!
        assertEquals(6, busiest.day)
        assertEquals(9_000_000L, busiest.amountRial)
        assertEquals(2, busiest.count)
        assertNull(ReportInsights.busiestDay(rows.take(1), mehr))
    }

    @Test
    fun `جمعه‌ها پرخرج‌تر`() {
        val now = mehr.startMillis() + 9 * day + noon
        val cal = Calendar.getInstance()
        // ۸ هفته: هر روز ۱ میلیون، جمعه‌ها ۳ میلیون
        val rows = (1..56).map { back ->
            val time = now - back * day
            cal.timeInMillis = time
            DatedAmount(null, if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) 3_000_000L else 1_000_000L, time)
        }
        val peak = ReportInsights.weekdayPeak(rows, now)!!
        assertEquals(Calendar.FRIDAY, peak.weekday)
        assertEquals(3.0, peak.times, 0.01)

        // کمتر از ۴ هفته سابقه: الگو نیست
        assertNull(ReportInsights.weekdayPeak(rows.take(20), now))
    }
}
