package ir.jibito.app.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferMatcherTest {

    private val min = 60_000L
    private val hour = 60 * min

    private fun w(id: Long, amount: Long, t: Long) = TransferCandidate(id, true, amount, t)
    private fun d(id: Long, amount: Long, t: Long) = TransferCandidate(id, false, amount, t)

    @Test
    fun `برداشت و واریز هم‌مبلغ چند دقیقه بعد ← یک جفت`() {
        val pairs = TransferMatcher.findPairs(listOf(w(1, 5_000_000, 0), d(2, 5_000_000, 2 * min)))
        assertEquals(listOf(TransferPair(1, 2)), pairs)
    }

    @Test
    fun `مبلغ متفاوت یا واریزِ قبل از برداشت ← جفت نیست`() {
        assertEquals(emptyList<TransferPair>(), TransferMatcher.findPairs(listOf(w(1, 5_000_000, 0), d(2, 5_000_010, min))))
        assertEquals(emptyList<TransferPair>(), TransferMatcher.findPairs(listOf(w(1, 5_000_000, hour), d(2, 5_000_000, 0))))
    }

    @Test
    fun `پایا تا ۲۴ ساعت قبول، بیشتر نه`() {
        assertEquals(1, TransferMatcher.findPairs(listOf(w(1, 9_000_000, 0), d(2, 9_000_000, 23 * hour))).size)
        assertEquals(0, TransferMatcher.findPairs(listOf(w(1, 9_000_000, 0), d(2, 9_000_000, 25 * hour))).size)
    }

    @Test
    fun `هر واریز فقط یک بار جفت می‌شود و نزدیک‌ترین گرفته می‌شود`() {
        val pairs = TransferMatcher.findPairs(
            listOf(
                w(1, 1_000_000, 0),
                w(2, 1_000_000, 10 * min),
                d(3, 1_000_000, 5 * min),
                d(4, 1_000_000, 15 * min),
            )
        )
        assertEquals(listOf(TransferPair(1, 3), TransferPair(2, 4)), pairs)
    }
}
