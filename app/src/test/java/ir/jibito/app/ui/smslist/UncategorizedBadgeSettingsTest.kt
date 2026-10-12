package ir.jibito.app.ui.smslist

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.domain.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** کلید «عدد خرج‌های بی‌دسته» در تنظیمات و «فعلاً نه»: هر دو یک کلیدند و خاموش و روشن کردنش باید دیده شود */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UncategorizedBadgeSettingsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var settings: UncategorizedBadgeSettings

    @Before
    fun setUp() {
        context.getSharedPreferences(UncategorizedBadgeSettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        settings = UncategorizedBadgeSettings(context)
    }

    private fun tx(id: Long, date: Long) = Transaction(
        id = id, bank = null, body = "", dateMillis = date, transaction = ParsedTransaction(FlowType.WITHDRAWAL, 100_000),
        merchant = null, suggestedCategory = null, isFailedPurchase = false, categoryId = null,
        categoryName = null, categoryIcon = null, isAutoCategorized = false,
    )

    private val all = listOf(tx(1, 1_000), tx(2, 2_000), tx(3, 3_000))

    private fun count() = uncategorizedBadgeCount(all, settings.enabled.value, settings.seenUntil.value)

    @Test
    fun `بعد از دیده‌شدن، خاموش و روشن کردن عدد را برمی‌گرداند`() {
        settings.markSeen(all)
        assertEquals(0, count())

        settings.setEnabled(false)
        assertEquals(0, count())

        settings.setEnabled(true)
        assertEquals(3, count())
        assertEquals(0L, settings.seenUntil.value)
    }

    @Test
    fun `روشن کردن دوباره در نسخه‌ی تازه‌ی تنظیمات هم می‌ماند`() {
        settings.markSeen(all)
        settings.setEnabled(false)
        settings.setEnabled(true)

        val reopened = UncategorizedBadgeSettings(context)
        assertTrue(reopened.enabled.value)
        assertEquals(0L, reopened.seenUntil.value)
    }

    @Test
    fun `خاموش کردن دیده‌شده‌ها را پاک نمی‌کند و عدد صفر می‌ماند`() {
        settings.markSeen(all)
        settings.setEnabled(false)
        assertFalse(settings.enabled.value)
        assertEquals(3_000L, settings.seenUntil.value)
        assertEquals(0, count())
    }

    @Test
    fun `روشن بودنِ از قبل، دیده‌شده‌ها را پاک نمی‌کند`() {
        settings.markSeen(all)
        settings.setEnabled(true)
        assertEquals(3_000L, settings.seenUntil.value)
        assertEquals(0, count())
    }

    @Test
    fun `فعلاً نه کلید تنظیمات را خاموش می‌کند و یک بار روشن کردن عدد را برمی‌گرداند`() {
        settings.turnOff()
        assertFalse(settings.enabled.value)
        assertFalse(UncategorizedBadgeSettings(context).enabled.value)
        assertEquals(0, count())

        settings.setEnabled(true)
        assertEquals(3, count())
    }

    @Test
    fun `برگردون بعد از فعلاً نه همان حالت قبل را برمی‌گرداند`() {
        settings.markSeen(listOf(tx(1, 1_000)))
        val undo = settings.turnOff()
        undo()
        assertTrue(settings.enabled.value)
        assertEquals(1_000L, settings.seenUntil.value)
        assertEquals(2, count())

        val reopened = UncategorizedBadgeSettings(context)
        assertTrue(reopened.enabled.value)
        assertEquals(1_000L, reopened.seenUntil.value)
    }
}
