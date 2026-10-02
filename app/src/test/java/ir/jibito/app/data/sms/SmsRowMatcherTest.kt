package ir.jibito.app.data.sms

import ir.jibito.app.data.sms.SmsRowMatcher.Row
import ir.jibito.app.data.sms.SmsRowMatcher.Scanned
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsRowMatcherTest {

    private fun contentOf(vararg rows: Pair<Row, String>): suspend () -> Map<Pair<Long, String>, Long> =
        { rows.associate { (row, body) -> (row.dateEpoch to body) to row.rowId } }

    @Test
    fun sameIdAndDateMatches() = runBlocking {
        val r = SmsRowMatcher.match(
            scanned = listOf(Scanned(10, 1_000, "a")),
            rows = listOf(Row(1, 10, 1_000)),
            contentLookup = null,
        )
        assertEquals(mapOf(10L to 1L), r.matches)
        assertTrue(r.detach.isEmpty())
    }

    @Test
    fun newSmsHasNoMatchAndNoLookupInIncrementalScan() = runBlocking {
        val r = SmsRowMatcher.match(
            scanned = listOf(Scanned(11, 2_000, "b")),
            rows = listOf(Row(1, 10, 1_000)),
            contentLookup = null,
        )
        assertTrue(r.matches.isEmpty())
        assertTrue(r.detach.isEmpty())
    }

    @Test
    fun sameIdDifferentDateIsAnotherMessageAndOldRowIsDetached() = runBlocking {
        // گوشی تازه: شناسه‌ی ۱۰ حالا مال پیامک دیگری است
        val r = SmsRowMatcher.match(
            scanned = listOf(Scanned(10, 5_000, "new message")),
            rows = listOf(Row(1, 10, 1_000)),
            contentLookup = null,
        )
        assertTrue(r.matches.isEmpty())
        assertEquals(listOf(1L), r.detach)
    }

    @Test
    fun phoneChangeRemapsByDateAndBody() = runBlocking {
        // گوشی قدیم: پیامک‌ها شناسه‌ی ۱۰ و ۱۱ داشتند؛ گوشی تازه همان پیامک‌ها را با شناسه‌ی ۱۱ و ۳ دارد (جابه‌جا)
        val rowA = Row(1, 10, 1_000)
        val rowB = Row(2, 11, 2_000)
        val r = SmsRowMatcher.match(
            scanned = listOf(Scanned(11, 1_000, "A"), Scanned(3, 2_000, "B")),
            rows = listOf(rowA, rowB),
            contentLookup = contentOf(rowA to "A", rowB to "B"),
        )
        assertEquals(mapOf(11L to 1L, 3L to 2L), r.matches)
        // ردیف B شناسه‌ی ۱۱ را دارد که حالا مال A است ← اول آزاد می‌شود
        assertEquals(listOf(2L), r.detach)
    }

    @Test
    fun exactMatchWinsOverContentMatch() = runBlocking {
        // دو پیامک کاملاً یکسان (زمان و متن): ردیفی که شناسه‌اش درست است، مال خودش می‌ماند
        val row1 = Row(1, 10, 1_000)
        val row2 = Row(2, 20, 1_000)
        val r = SmsRowMatcher.match(
            scanned = listOf(Scanned(30, 1_000, "same"), Scanned(10, 1_000, "same")),
            rows = listOf(row1, row2),
            contentLookup = { mapOf((1_000L to "same") to 1L) },
        )
        assertEquals(1L, r.matches[10L])
        assertFalse("row 1 must not be claimed twice", r.matches[30L] == 1L)
    }

    @Test
    fun contentLookupIsNotLoadedWhenEverythingMatchesById() = runBlocking {
        var loaded = false
        SmsRowMatcher.match(
            scanned = listOf(Scanned(10, 1_000, "a")),
            rows = listOf(Row(1, 10, 1_000)),
            contentLookup = { loaded = true; emptyMap() },
        )
        assertFalse(loaded)
    }

    @Test
    fun detachedRowWithoutSmsIdCanStillBeFoundByContent() = runBlocking {
        val row = Row(7, null, 4_000)
        val r = SmsRowMatcher.match(
            scanned = listOf(Scanned(99, 4_000, "x")),
            rows = listOf(row),
            contentLookup = contentOf(row to "x"),
        )
        assertEquals(mapOf(99L to 7L), r.matches)
        assertTrue(r.detach.isEmpty())
    }
}
