package ir.jibito.app.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ErrorLogTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() = ErrorLog.clear(context)

    @Test
    fun recordsAndReportsNewestFirst() {
        ErrorLog.record(context, "first", IllegalStateException("boom one"))
        ErrorLog.record(context, "second", IllegalArgumentException("boom two"))
        assertEquals(2, ErrorLog.count(context))
        val report = ErrorLog.report(context)
        assertTrue(report.startsWith("Jibito"))
        assertTrue(report.indexOf("boom two") < report.indexOf("boom one"))
        assertTrue(ErrorLog.lastAt(context) != null)
    }

    @Test
    fun keepsOnlyTheLatestEntries() {
        repeat(45) { ErrorLog.record(context, "n$it", RuntimeException("e$it")) }
        assertEquals(30, ErrorLog.count(context))
        val report = ErrorLog.report(context)
        assertTrue(report.contains("e44"))
        assertTrue(!report.contains("· n0\n"))
    }

    @Test
    fun clearRemovesEverything() {
        ErrorLog.record(context, "x", RuntimeException("x"))
        ErrorLog.clear(context)
        assertEquals(0, ErrorLog.count(context))
        assertEquals(null, ErrorLog.lastAt(context))
    }
}
