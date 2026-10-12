package ir.jibito.app.data.pro

import android.content.Context
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «جیبیتوی کامل»: ماه رایگان روی گوشی، خرید، «قبلاً خریدم» و پیدا شدن خرید قبلی بعد از نصب دوباره */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProAccessTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val start = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    /** فروشگاه ساختگی: جواب‌ها از پیش تعیین شده‌اند */
    private class FakeStore(
        var owned: Boolean? = false,
        var purchaseResult: PurchaseResult = PurchaseResult.BOUGHT,
        override val available: Boolean = true,
    ) : ProStore {
        var ownedCalls = 0
        override suspend fun owned(): Boolean? = owned.also { ownedCalls++ }
        override suspend fun price(): String? = "۹۹٬۰۰۰ تومان"
        override fun purchase(registry: ActivityResultRegistry, onResult: (PurchaseResult) -> Unit) = onResult(purchaseResult)
    }

    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) = Unit
    }

    private fun access(store: ProStore, now: Long = start) =
        ProAccess(ProSettings(context, now), store, CoroutineScope(Dispatchers.Unconfined))

    /** هر تست روی نصب تازه‌ای که ماه رایگانش در [start] شروع شده */
    @Before
    fun freshInstall() {
        context.getSharedPreferences(ProSettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        ProSettings(context, start)
    }

    @Test
    fun `ماه رایگان از اولین باز شدن شمرده می‌شود و با دوباره ساختن از نو شروع نمی‌شود`() {
        val later = ProSettings(context, start + 10 * day)
        assertEquals(ProStatus.Trial(20), later.status.value)
        later.refresh(start + 31 * day)
        assertEquals(ProStatus.Free, later.status.value)
    }

    @Test
    fun `«ماه رایگانت تموم شد» فقط یک بار و فقط بعد از تمام شدنش`() {
        val settings = ProSettings(context, start)
        assertFalse(settings.trialEndNoticeDue)
        settings.refresh(start + 30 * day)
        assertTrue(settings.trialEndNoticeDue)
        settings.markTrialEndNoticeSeen()
        assertFalse(settings.trialEndNoticeDue)
        assertFalse(ProSettings(context, start + 40 * day).trialEndNoticeDue)
    }

    @Test
    fun `خرید موفق برای همیشه باز می‌کند و بعد از دوباره باز شدن اپ می‌ماند`() = runBlocking {
        val pro = access(FakeStore(), start + 40 * day)
        assertEquals(ProStatus.Free, pro.status.value)
        assertEquals(PurchaseResult.BOUGHT, pro.purchase(registry))
        assertEquals(ProStatus.Owned, pro.status.value)
        assertEquals(ProStatus.Owned, ProSettings(context, start + 400 * day).status.value)
        assertFalse(ProSettings(context, start + 400 * day).trialEndNoticeDue)
    }

    @Test
    fun `خرید ناموفقی که فقط یعنی «قبلاً خریده‌ای» باز می‌کند`() = runBlocking {
        val pro = access(FakeStore(owned = true, purchaseResult = PurchaseResult.FAILED), start + 40 * day)
        assertEquals(PurchaseResult.BOUGHT, pro.purchase(registry))
        assertEquals(ProStatus.Owned, pro.status.value)
    }

    @Test
    fun `خرید ناموفق یا لغوشده چیزی را باز نمی‌کند`() = runBlocking {
        val failed = access(FakeStore(owned = false, purchaseResult = PurchaseResult.FAILED), start + 40 * day)
        assertEquals(PurchaseResult.FAILED, failed.purchase(registry))
        assertEquals(ProStatus.Free, failed.status.value)
        val canceled = access(FakeStore(purchaseResult = PurchaseResult.CANCELED), start + 40 * day)
        assertEquals(PurchaseResult.CANCELED, canceled.purchase(registry))
        assertEquals(ProStatus.Free, canceled.status.value)
    }

    @Test
    fun `«قبلاً خریدم» سه جواب جدا دارد`() = runBlocking {
        assertEquals(RestoreResult.NOT_FOUND, access(FakeStore(owned = false)).restore())
        assertEquals(RestoreResult.UNAVAILABLE, access(FakeStore(owned = null)).restore())
        assertEquals(RestoreResult.UNAVAILABLE, access(FakeStore(owned = true, available = false)).restore())
        val pro = access(FakeStore(owned = true), start + 40 * day)
        assertEquals(RestoreResult.FOUND, pro.restore())
        assertEquals(ProStatus.Owned, pro.status.value)
    }

    @Test
    fun `خرید قبلی بعد از نصب دوباره خودش پیدا می‌شود، حداکثر روزی یک بار`() {
        val store = FakeStore(owned = false)
        val pro = access(store)
        pro.onAppVisible(start)
        pro.onAppVisible(start + day / 2)
        assertEquals(1, store.ownedCalls)
        store.owned = true
        pro.onAppVisible(start + day)
        assertEquals(2, store.ownedCalls)
        assertEquals(ProStatus.Owned, pro.status.value)
        // خریده: دیگر سراغ بازار نمی‌رود
        pro.onAppVisible(start + 5 * day)
        assertEquals(2, store.ownedCalls)
    }

    @Test
    fun `بازار یک بار خالی جواب داد؛ خرید پس گرفته نمی‌شود`() {
        val store = FakeStore(owned = true)
        val pro = access(store)
        pro.onAppVisible(start)
        assertEquals(ProStatus.Owned, pro.status.value)
        store.owned = false
        runBlocking { assertEquals(RestoreResult.NOT_FOUND, pro.restore()) }
        assertEquals(ProStatus.Owned, ProSettings(context, start + 400 * day).status.value)
    }

    @Test
    fun `بدون کلید فروشگاه سراغ بازار نمی‌رود`() {
        val store = FakeStore(owned = true, available = false)
        access(store).onAppVisible(start)
        assertEquals(0, store.ownedCalls)
    }
}
