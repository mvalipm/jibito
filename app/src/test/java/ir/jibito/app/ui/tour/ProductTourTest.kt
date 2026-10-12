package ir.jibito.app.ui.tour

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductTourTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        context.getSharedPreferences(ProductTour.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** باز شدن اپ از نو: یک نمونه‌ی تازه روی همان تنظیمات */
    private fun launch() = ProductTour(context)

    @Test
    fun `نصب تازه یک بار راهنما را نشان می‌دهد و بعد از تمام شدن دیگر نه`() {
        val tour = launch()
        assertFalse(tour.active.value)
        tour.start(freshInstall = true)
        assertTrue(tour.active.value)
        tour.finish()
        assertFalse(tour.active.value)

        val next = launch()
        next.start(freshInstall = true)
        assertFalse(next.active.value)
    }

    @Test
    fun `رد کردن هم یعنی دیده شد`() {
        launch().apply { start(freshInstall = true) }.finish()
        val next = launch()
        next.start(freshInstall = true)
        assertFalse(next.active.value)
    }

    @Test
    fun `اگر راهنما نیمه‌کاره ماند، دفعه‌ی بعد دوباره می‌آید`() {
        launch().start(freshInstall = true)
        val next = launch()
        next.start(freshInstall = true)
        assertTrue(next.active.value)
    }

    @Test
    fun `کسی که اپ را به‌روز کرده خودکار نمی‌بیند، و بعدها هم این تصمیم عوض نمی‌شود`() {
        val tour = launch()
        tour.start(freshInstall = false)
        assertFalse(tour.active.value)
        // حتی اگر بعداً «نصب تازه» به نظر برسد (مثلاً زمان نصب و به‌روزرسانی یکی شود)
        val next = launch()
        next.start(freshInstall = true)
        assertFalse(next.active.value)
    }

    @Test
    fun `دوباره نشان بده کار می‌کند و باز شدن اپ خاموشش نمی‌کند`() {
        val tour = launch()
        tour.start(freshInstall = false)
        tour.restart()
        assertTrue(tour.active.value)
        // چرخاندن گوشی: دوباره start صدا زده می‌شود
        tour.start(freshInstall = false)
        assertTrue(tour.active.value)
        tour.finish()
        assertFalse(tour.active.value)
    }

    @Test
    fun `فقط مرحله‌هایی که جایشان روی صفحه است، به ترتیب`() {
        assertEquals(
            listOf(TourStep.HERO, TourStep.TRANSACTIONS, TourStep.TODO, TourStep.SETTINGS),
            tourSteps(setOf(TourStep.SETTINGS, TourStep.TODO, TourStep.HERO, TourStep.TRANSACTIONS)),
        )
        assertEquals(emptyList<TourStep>(), tourSteps(emptySet()))
    }

    @Test
    fun `اگر مرحله‌ی جاری از صفحه رفت، به بعدی می‌رود و جای کاربر نمی‌پرد`() {
        val steps = tourSteps(setOf(TourStep.HERO, TourStep.REPORTS, TourStep.SETTINGS))
        assertEquals(TourStep.HERO, steps.current(TourStep.HERO.ordinal))
        // «کجا رفت؟» نیست ← مرحله‌ی بعدی که هست
        assertEquals(TourStep.REPORTS, steps.current(TourStep.WHERE.ordinal))
        // از آخرین مرحله هم گذشته ← همان آخری
        assertEquals(TourStep.SETTINGS, steps.current(TourStep.SETTINGS.ordinal + 1))
        assertNull(emptyList<TourStep>().current(0))
    }
}
