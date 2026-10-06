package ir.jibito.app.domain

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.util.JalaliMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class OneOffDetectorTest {

    private val mehr = JalaliMonth(1405, 7)
    private val day = 24L * 60 * 60 * 1000
    private val noon = 12L * 60 * 60 * 1000
    private val now = mehr.startMillis() + 9 * day + noon // ۱۰ مهر
    private lateinit var savedZone: TimeZone

    @Before
    fun fixZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"))
    }

    @After
    fun restoreZone() = TimeZone.setDefault(savedZone)

    private var nextId = 1L

    private fun tx(
        month: JalaliMonth,
        dayOfMonth: Int,
        rial: Long,
        type: FlowType = FlowType.WITHDRAWAL,
        categoryId: Long? = null,
        oneOff: Boolean = false,
        rejected: Boolean = false,
        selfTransfer: Boolean = false,
    ) = Transaction(
        id = nextId++, bank = null, body = "", dateMillis = month.startMillis() + (dayOfMonth - 1) * day + noon,
        transaction = ParsedTransaction(type, rial), merchant = null, suggestedCategory = null,
        isFailedPurchase = false, categoryId = categoryId, categoryName = null, categoryIcon = null,
        isAutoCategorized = false, isSelfTransfer = selfTransfer, isOneOff = oneOff, isOneOffRejected = rejected,
    )

    /** سه ماه قبل، هر ماه ۴۰۰ میلیون تومان خرج عادی (۴ میلیارد ریال)، در ۴۰ خرید ۱۰ میلیون تومانی */
    private fun history() = (1..3).flatMap { m -> (1..40).map { i -> tx(mehr.plus(-m), 1 + i % 28, 100_000_000L) } }

    @Test
    fun `خرید خیلی بزرگ‌تر از یک ماه معمولی پیشنهاد می‌شود`() {
        val house = tx(mehr, 5, 100_000_000_000L)
        val car = tx(mehr, 7, 30_000_000_000L)
        val found = OneOffDetector.find(history() + house + car, emptySet(), now)
        assertEquals(listOf(house.id, car.id), found.map { it.id })
    }

    @Test
    fun `کمتر از نصف یک ماه معمولی پیشنهاد نمی‌شود`() {
        // ۱۵۰ میلیون تومان در برابر ماه معمولی ۴۰۰ میلیون
        val found = OneOffDetector.find(history() + tx(mehr, 5, 1_500_000_000L), emptySet(), now)
        assertTrue(found.isEmpty())
    }

    @Test
    fun `بدون سابقه فقط قانون مبلغ ثابت`() {
        val big = tx(mehr, 5, 600_000_000L) // ۶۰ میلیون تومان
        val small = tx(mehr, 6, 400_000_000L) // ۴۰ میلیون تومان: کمتر از حداقل
        assertEquals(listOf(big.id), OneOffDetector.find(listOf(big, small), emptySet(), now).map { it.id })
    }

    @Test
    fun `علامت‌خورده، «نه» گفته، قدیمی، واریز، انتقال به خودم و پس‌انداز پیشنهاد نمی‌شوند`() {
        val big = 100_000_000_000L
        val rows = history() + listOf(
            tx(mehr, 5, big, oneOff = true),
            tx(mehr, 5, big, rejected = true),
            tx(mehr.plus(-2), 1, big), // بیشتر از ۴۵ روز پیش
            tx(mehr, 5, big, type = FlowType.DEPOSIT),
            tx(mehr, 5, big, selfTransfer = true),
            tx(mehr, 5, big, categoryId = 9),
        )
        assertTrue(OneOffDetector.find(rows, setOf(9L), now).isEmpty())
    }

    @Test
    fun `خرج یک‌باره‌ی قبلی پایه‌ی ماه معمولی نیست`() {
        // شهریور یک خرید خانه داشت؛ نباید «ماه معمولی» را بزرگ کند
        val rows = history() + tx(mehr.plus(-1), 10, 1_000_000_000_000L, oneOff = true) + tx(mehr, 5, 3_000_000_000L)
        assertEquals(1, OneOffDetector.find(rows, emptySet(), now).size)
    }
}
