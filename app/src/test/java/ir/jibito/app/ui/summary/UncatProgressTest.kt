package ir.jibito.app.ui.summary

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.JalaliMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/** نوار پیشرفت «خرج بی‌دسته»: فقط خرج‌های همین ماه، و «فوری» فقط با مورد تازه */
class UncatProgressTest {

    private val month = JalaliMonth(1405, 7)
    private val day = 86_400_000L
    private val inMonth = month.startMillis() + 3 * day

    private fun tx(
        id: Long,
        date: Long = inMonth,
        categoryId: Long? = null,
        type: FlowType = FlowType.WITHDRAWAL,
        selfTransfer: Boolean = false,
        failed: Boolean = false,
    ) = Transaction(
        id = id, bank = null, body = "", dateMillis = date, transaction = ParsedTransaction(type, 100_000),
        merchant = null, suggestedCategory = null, isFailedPurchase = failed, categoryId = categoryId,
        categoryName = null, categoryIcon = null, isAutoCategorized = false, isSelfTransfer = selfTransfer,
    )

    @Test
    fun `مخرج همه‌ی خرج‌های همین ماه است`() {
        val all = listOf(tx(1), tx(2), tx(3, categoryId = 9), tx(4, categoryId = 9), tx(5, categoryId = 9))
        val p = uncatProgress(all, month, badgeEnabled = true, seenUntil = 0L)
        assertEquals(2, p.open)
        assertEquals(5, p.total)
        assertEquals(3, p.done)
        assertEquals(0.6f, p.fraction, 0.0001f)
    }

    @Test
    fun `ماه‌های دیگر، واریز، انتقال به خودم و خرید ناموفق نه در عدد هستند نه در مخرج`() {
        val all = listOf(
            tx(1),
            tx(2, date = month.startMillis() - day),
            tx(3, date = month.endMillis() + day),
            tx(4, type = FlowType.DEPOSIT),
            tx(5, selfTransfer = true),
            tx(6, failed = true),
        )
        val p = uncatProgress(all, month, badgeEnabled = true, seenUntil = 0L)
        assertEquals(1, p.open)
        assertEquals(1, p.total)
    }

    @Test
    fun `ماه بدون خرج نوار خالی دارد، نه تقسیم بر صفر`() {
        val p = uncatProgress(emptyList(), month, badgeEnabled = true, seenUntil = 0L)
        assertEquals(0, p.total)
        assertEquals(0f, p.fraction, 0f)
    }

    @Test
    fun `مورد تازه یعنی بی‌دسته‌ای تازه‌تر از آخرین دیده‌شده`() {
        val all = listOf(tx(1, date = inMonth), tx(2, date = inMonth + day))
        assertEquals(2, uncatProgress(all, month, badgeEnabled = true, seenUntil = 0L).unseen)
        assertEquals(0, uncatProgress(all, month, badgeEnabled = true, seenUntil = inMonth + day).unseen)
        assertEquals(1, uncatProgress(all + tx(3, date = inMonth + 2 * day), month, badgeEnabled = true, seenUntil = inMonth + day).unseen)
    }

    @Test
    fun `با عدد خاموش، مورد تازه‌ای شمرده نمی‌شود ولی پیشرفت می‌ماند`() {
        val p = uncatProgress(listOf(tx(1), tx(2, categoryId = 3)), month, badgeEnabled = false, seenUntil = 0L)
        assertEquals(0, p.unseen)
        assertEquals(1, p.open)
        assertEquals(2, p.total)
    }
}
