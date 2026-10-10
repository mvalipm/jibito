package ir.jibito.app.ui.smslist

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.domain.Transaction
import org.junit.Assert.assertEquals
import org.junit.Test

class UncategorizedBadgeTest {

    private fun tx(id: Long, date: Long, categoryId: Long? = null, type: FlowType = FlowType.WITHDRAWAL) =
        Transaction(
            id = id, bank = null, body = "", dateMillis = date, transaction = ParsedTransaction(type, 100_000),
            merchant = null, suggestedCategory = null, isFailedPurchase = false, categoryId = categoryId,
            categoryName = null, categoryIcon = null, isAutoCategorized = false,
        )

    private val all = listOf(tx(1, 1_000), tx(2, 2_000), tx(3, 3_000), tx(4, 4_000, categoryId = 9), tx(5, 5_000, type = FlowType.DEPOSIT))

    @Test
    fun `بدون بستن همه‌ی خرج‌های بی‌دسته شمرده می‌شوند`() {
        assertEquals(3, uncategorizedBadgeCount(all, enabled = true, seenUntil = 0L))
    }

    @Test
    fun `خاموش یعنی صفر`() {
        assertEquals(0, uncategorizedBadgeCount(all, enabled = false, seenUntil = 0L))
    }

    @Test
    fun `بعد از فعلاً نه فقط خرج‌های تازه‌تر عدد می‌سازند`() {
        assertEquals(0, uncategorizedBadgeCount(all, enabled = true, seenUntil = 3_000L))
        assertEquals(1, uncategorizedBadgeCount(all + tx(6, 6_000), enabled = true, seenUntil = 3_000L))
    }
}
