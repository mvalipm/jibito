package ir.jibito.app.data.wallet

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «برگردون» بعد از «آره، حقوقمه» و «نه»: جواب‌های قبلی (حقوق تأییدشده و ردشده‌ها) دقیقاً برمی‌گردند */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SalaryUndoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var settings: SalarySettings

    @Before
    fun setUp() {
        context.getSharedPreferences(SalarySettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        settings = SalarySettings(context)
    }

    private val old = Salary("ملت|اداره", bankId = 1, amountRial = 50_000_000, dayOfMonth = 5)
    private val fresh = Salary("سامان|شرکت", bankId = 2, amountRial = 90_000_000, dayOfMonth = 12)

    @Test
    fun undoYesRemovesTheConfirmedSalary() {
        val before = settings.choice.value
        settings.confirm(fresh)
        settings.restore(before)
        assertNull(settings.choice.value.confirmed)
        assertEquals(before, settings.choice.value)
    }

    @Test
    fun undoYesPutsThePreviousSalaryBack() {
        settings.confirm(old)
        val before = settings.choice.value
        settings.confirm(fresh)
        settings.restore(before)
        assertEquals(old.key, settings.choice.value.confirmed?.key)
        assertEquals(old.amountRial, settings.choice.value.confirmed?.amountRial)
        assertEquals(old.dayOfMonth, settings.choice.value.confirmed?.dayOfMonth)
    }

    @Test
    fun undoNoSuggestsItAgainAndKeepsEarlierNos() {
        settings.dismiss("اول")
        val before = settings.choice.value
        settings.dismiss(fresh.key)
        assertEquals(setOf("اول", fresh.key), settings.choice.value.dismissed)
        settings.restore(before)
        assertEquals(setOf("اول"), settings.choice.value.dismissed)
    }

    @Test
    fun undoNoOnAConfirmedSalaryConfirmsItAgain() {
        settings.confirm(old)
        val before = settings.choice.value
        settings.dismiss(old.key)
        assertNull(settings.choice.value.confirmed)
        settings.restore(before)
        assertEquals(old.key, settings.choice.value.confirmed?.key)
        assertEquals(emptySet<String>(), settings.choice.value.dismissed)
    }
}
