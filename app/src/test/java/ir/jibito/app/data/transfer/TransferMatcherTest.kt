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

    /** همان الگوریتم قبلی (همه‌ی واریزها برای هر برداشت)؛ نسخه‌ی سریع باید دقیقاً همین جواب را بدهد */
    private fun naive(items: List<TransferCandidate>): List<TransferPair> {
        val withdrawals = items.filter { it.isWithdrawal }.sortedWith(compareBy({ it.dateMillis }, { it.id }))
        val deposits = items.filter { !it.isWithdrawal }.sortedWith(compareBy({ it.dateMillis }, { it.id }))
        val byAmount = deposits.groupBy { it.amountRial }
        val used = HashSet<Long>()
        val pairs = mutableListOf<TransferPair>()
        for (w in withdrawals) {
            fun ok(d: TransferCandidate) =
                d.id !in used && d.dateMillis >= w.dateMillis && d.dateMillis - w.dateMillis <= TransferMatcher.WINDOW_MILLIS
            val match = byAmount[w.amountRial]?.firstOrNull(::ok)
                ?: deposits.filter { ok(it) && ir.jibito.app.data.linking.PurchaseLinker.isTransferFee(w.amountRial, it.amountRial) }
                    .minByOrNull { it.dateMillis }
                ?: continue
            used += match.id
            pairs += TransferPair(w.id, match.id)
        }
        return pairs
    }

    @Test
    fun `نسخه‌ی سریع همان جواب نسخه‌ی کامل را می‌دهد`() {
        val random = java.util.Random(42)
        val amounts = listOf(1_000_000L, 1_011_000L, 5_000_000L, 5_050_000L, 20_000_000L, 20_100_000L, 333_000L)
        repeat(200) { round ->
            val items = (1..60L).map { id ->
                TransferCandidate(
                    id = id,
                    isWithdrawal = random.nextBoolean(),
                    amountRial = amounts[random.nextInt(amounts.size)],
                    // چند روز، با زمان‌های تکراری هم
                    dateMillis = random.nextInt(4 * 24 * 6).toLong() * 10 * min,
                )
            }
            assertEquals("round $round", naive(items), TransferMatcher.findPairs(items))
        }
    }

    @Test
    fun `چند ده هزار تراکنش در کسری از ثانیه`() {
        val items = (1..40_000L).map { id ->
            TransferCandidate(id, id % 3 != 0L, 1_000_000L + (id % 97) * 1_000, id * 20 * min)
        }
        val start = System.nanoTime()
        TransferMatcher.findPairs(items)
        val millis = (System.nanoTime() - start) / 1_000_000
        assert(millis < 3_000) { "took $millis ms" }
    }
}
