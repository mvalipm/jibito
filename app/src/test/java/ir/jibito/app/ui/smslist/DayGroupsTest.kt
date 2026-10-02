package ir.jibito.app.ui.smslist

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.Jalali
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DayGroupsTest {

    private lateinit var savedZone: TimeZone

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"))
    }

    @After
    fun tearDown() = TimeZone.setDefault(savedZone)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    private var nextId = 1L

    private fun tx(
        date: Long,
        amount: Long,
        type: FlowType = FlowType.WITHDRAWAL,
        categoryId: Long? = null,
        failed: Boolean = false,
        selfTransfer: Boolean = false,
    ) = Transaction(
        id = nextId++, bank = null, body = "", dateMillis = date,
        transaction = ParsedTransaction(type, amount), merchant = null, suggestedCategory = null,
        isFailedPurchase = failed, categoryId = categoryId, categoryName = null, categoryIcon = null,
        isAutoCategorized = false, isSelfTransfer = selfTransfer,
    )

    @Test
    fun `تراکنش‌های هر روز کنار هم و به همان ترتیب`() {
        val list = listOf(
            tx(at(2025, 10, 2, 23, 50), 10),
            tx(at(2025, 10, 2, 0, 5), 20),
            tx(at(2025, 10, 1, 23, 59), 30),
            tx(at(2025, 9, 27, 9), 40),
        )
        val groups = groupByDay(list)
        assertEquals(3, groups.size)
        assertEquals(listOf(10L, 20L), groups[0].items.map { it.transaction.amountRial })
        assertEquals(listOf(30L), groups[1].items.map { it.transaction.amountRial })
        assertEquals(at(2025, 10, 2, 0), groups[0].dayStartMillis)
    }

    @Test
    fun `جمع خرج روز فقط خرج واقعی را می‌شمارد`() {
        val day = at(2025, 10, 2, 12)
        val savings = 99L
        val groups = groupByDay(
            listOf(
                tx(day, 100),
                tx(day, 1_000, type = FlowType.DEPOSIT),
                tx(day, 2_000, failed = true),
                tx(day, 3_000, selfTransfer = true),
                tx(day, 4_000, categoryId = savings),
                tx(day, 50, categoryId = 5),
            ),
            nonSpendCategoryIds = setOf(savings),
        )
        assertEquals(1, groups.size)
        assertEquals(150L, groups[0].spendRial)
    }

    @Test
    fun `فهرست خالی`() {
        assertEquals(emptyList<DayGroup>(), groupByDay(emptyList()))
    }

    @Test
    fun `عنوان روزها`() {
        val now = at(2025, 10, 2, 15) // پنجشنبه ۱۰ مهر ۱۴۰۴
        assertEquals("امروز", Jalali.dayTitle(at(2025, 10, 2, 0, 1), now))
        assertEquals("دیروز", Jalali.dayTitle(at(2025, 10, 1, 23, 59), now))
        assertEquals("شنبه ۵ مهر", Jalali.dayTitle(at(2025, 9, 27, 10), now))
        // سال شمسی دیگر ← سال هم نوشته می‌شود
        assertEquals("چهارشنبه ۲۹ اسفند ۱۴۰۳", Jalali.dayTitle(at(2025, 3, 19, 10), now))
        assertEquals("۰۹:۰۵", Jalali.time(at(2025, 10, 2, 9, 5)))
    }
}
