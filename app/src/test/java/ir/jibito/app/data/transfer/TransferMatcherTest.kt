package ir.jibito.app.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `انتقال عادی تا ۳۰ دقیقه قبول، بیشتر نه`() {
        assertEquals(1, TransferMatcher.findPairs(listOf(w(1, 9_000_000, 0), d(2, 9_000_000, 30 * min))).size)
        assertEquals(0, TransferMatcher.findPairs(listOf(w(1, 9_000_000, 0), d(2, 9_000_000, 31 * min))).size)
        assertEquals(0, TransferMatcher.findPairs(listOf(w(1, 9_000_000, 0), d(2, 9_000_000, 23 * hour))).size)
    }

    @Test
    fun `پایا یا ساتنا در هر کدام از دو پیامک ← تا ۷۲ ساعت قبول، بیشتر نه`() {
        val pw = TransferCandidate(1, true, 9_000_000, 0, isInterbank = true)
        assertEquals(1, TransferMatcher.findPairs(listOf(pw, d(2, 9_000_000, 71 * hour))).size)
        assertEquals(0, TransferMatcher.findPairs(listOf(pw, d(2, 9_000_000, 73 * hour))).size)
        val pd = TransferCandidate(2, false, 9_000_000, 48 * hour, isInterbank = true)
        assertEquals(listOf(TransferPair(1, 2)), TransferMatcher.findPairs(listOf(w(1, 9_000_000, 0), pd)))
    }

    @Test
    fun `تشخیص پایا و ساتنا و حواله از متن پیامک`() {
        assertTrue(TransferMatcher.isInterbankText("بانک ملی\nواریز پایا\n۱۰۰,۰۰۰"))
        assertTrue(TransferMatcher.isInterbankText("انتقال ساتنا ۵۰۰,۰۰۰,۰۰۰"))
        assertTrue(TransferMatcher.isInterbankText("حواله ۱۲۳"))
        assertFalse(TransferMatcher.isInterbankText("کارت به کارت ۱۰۰,۰۰۰"))
    }

    @Test
    fun `کارمزد بین ۵۰۰ تا ۵۰ هزار تومان قبول، بیرون از بازه نه`() {
        // ۵۰۰ تومان روی انتقال ۲۰ هزار تومانی (قانون ۲٪ قبلی ردش می‌کرد)
        assertTrue(TransferMatcher.isTransferFee(205_000, 200_000))
        assertTrue(TransferMatcher.isTransferFee(1_000_500_000, 1_000_000_000))
        // کمتر از ۵۰۰ تومان
        assertFalse(TransferMatcher.isTransferFee(1_004_999, 1_000_000))
        // بیشتر از ۵۰ هزار تومان، حتی روی مبلغ خیلی بزرگ (قانون ۲٪ قبلی قبولش می‌کرد)
        assertFalse(TransferMatcher.isTransferFee(1_000_500_010, 1_000_000_000))
        assertFalse(TransferMatcher.isTransferFee(1_020_000_000, 1_000_000_000))
        // کارمزد بیشتر از خود مبلغ
        assertFalse(TransferMatcher.isTransferFee(10_000, 4_000))
        // برابر یا کمتر ← کارمزد نیست
        assertFalse(TransferMatcher.isTransferFee(1_000_000, 1_000_000))
        assertFalse(TransferMatcher.isTransferFee(990_000, 1_000_000))
    }

    @Test
    fun `برداشت با کارمزد ← جفت با واریز خالص`() {
        val pairs = TransferMatcher.findPairs(listOf(w(1, 10_011_000, 0), d(2, 10_000_000, 2 * min)))
        assertEquals(listOf(TransferPair(1, 2)), pairs)
        assertEquals(0, TransferMatcher.findPairs(listOf(w(1, 10_600_000, 0), d(2, 10_000_000, 2 * min))).size)
    }

    @Test
    fun `واریز روی همان حساب ← پول برگشته، جفت نیست`() {
        // برداشت ۱۰۰ هزار از ملی، مانده ۹۰۰ هزار؛ ۲ دقیقه بعد همان ۱۰۰ هزار برگشت، مانده ۱ میلیون
        val wd = TransferCandidate(1, true, 1_000_000, 0, bankId = 1, balanceRial = 9_000_000)
        val back = TransferCandidate(2, false, 1_000_000, 2 * min, bankId = 1, balanceRial = 10_000_000)
        assertEquals(0, TransferMatcher.findPairs(listOf(wd, back)).size)
    }

    @Test
    fun `هم‌بانک ولی حساب دیگر ← جفت هست`() {
        val wd = TransferCandidate(1, true, 1_000_000, 0, bankId = 1, balanceRial = 9_000_000)
        // مانده‌ی حساب مقصد ربطی به مانده‌ی حساب مبدأ ندارد
        val other = TransferCandidate(2, false, 1_000_000, 2 * min, bankId = 1, balanceRial = 3_000_000)
        assertEquals(listOf(TransferPair(1, 2)), TransferMatcher.findPairs(listOf(wd, other)))
        // بانک دیگر، حتی با مانده‌های پشت سر هم
        val otherBank = TransferCandidate(2, false, 1_000_000, 2 * min, bankId = 2, balanceRial = 10_000_000)
        assertEquals(1, TransferMatcher.findPairs(listOf(wd, otherBank)).size)
        // مانده نامعلوم ← نمی‌شود گفت همان حساب است
        val noBalance = TransferCandidate(2, false, 1_000_000, 2 * min, bankId = 1)
        assertEquals(1, TransferMatcher.findPairs(listOf(wd, noBalance)).size)
    }

    @Test
    fun `برگشتیِ همان حساب رد می‌شود و واریزِ حساب دیگر جفت می‌شود`() {
        val wd = TransferCandidate(1, true, 1_000_000, 0, bankId = 1, balanceRial = 9_000_000)
        val back = TransferCandidate(2, false, 1_000_000, 1 * min, bankId = 1, balanceRial = 10_000_000)
        val other = TransferCandidate(3, false, 1_000_000, 3 * min, bankId = 5, balanceRial = 4_000_000)
        assertEquals(listOf(TransferPair(1, 3)), TransferMatcher.findPairs(listOf(wd, back, other)))
    }

    @Test
    fun `اصلاحیه هیچ‌وقت انتقال نیست، حتی وقتی برداشتِ دیگری وسطش آمده`() {
        // ردیف‌های قدیمی: دو برداشت ۱ میلیونی و یک اصلاحیه که هنوز به برداشتِ دوم وصل نشده
        val items = listOf(
            TransferCandidate(1, true, 1_000_000, 0, bankId = 1, balanceRial = 21_814_555),
            TransferCandidate(2, true, 1_000_000, min, bankId = 1, balanceRial = 20_814_555),
            TransferCandidate(3, false, 1_000_000, 2 * min, bankId = 1, balanceRial = 21_814_555, isCorrection = true),
        )
        assertEquals(emptyList<TransferPair>(), TransferMatcher.findPairs(items))
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
        val deposits = items.filter { !it.isWithdrawal && !it.isCorrection }.sortedWith(compareBy({ it.dateMillis }, { it.id }))
        val byAmount = deposits.groupBy { it.amountRial }
        val used = HashSet<Long>()
        val pairs = mutableListOf<TransferPair>()
        for (w in withdrawals) {
            fun ok(d: TransferCandidate) =
                d.id !in used && d.dateMillis >= w.dateMillis &&
                    d.dateMillis - w.dateMillis <= TransferMatcher.windowFor(w, d) && !TransferMatcher.isSameAccount(w, d)
            val match = byAmount[w.amountRial]?.firstOrNull(::ok)
                ?: deposits.filter { ok(it) && TransferMatcher.isTransferFee(w.amountRial, it.amountRial) }
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
        val amounts = listOf(1_000_000L, 1_011_000L, 5_000_000L, 5_050_000L, 20_000_000L, 20_100_000L, 1_600_000L, 333_000L)
        repeat(200) { round ->
            val items = (1..60L).map { id ->
                TransferCandidate(
                    id = id,
                    isWithdrawal = random.nextBoolean(),
                    amountRial = amounts[random.nextInt(amounts.size)],
                    // چند روز، با زمان‌های تکراری هم
                    dateMillis = random.nextInt(4 * 24 * 6).toLong() * 10 * min,
                    bankId = random.nextInt(3),
                    balanceRial = listOf(null, 0L, 1_000_000L, 2_000_000L)[random.nextInt(4)],
                    isInterbank = random.nextInt(4) == 0,
                    isCorrection = random.nextInt(6) == 0,
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
