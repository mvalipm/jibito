package ir.jibito.app.data.pro

import androidx.activity.result.ActivityResultRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** جواب «قبلاً خریدم» */
enum class RestoreResult {
    /** خرید پیدا شد؛ جیبیتوی کامل باز شد */
    FOUND,

    /** فروشگاه جواب داد ولی با این حساب خریدی نیست */
    NOT_FOUND,

    /** نشد از فروشگاه بپرسیم (نصب نیست، وصل نشد، خرید هنوز فعال نشده) */
    UNAVAILABLE,
}

/**
 * «جیبیتوی کامل»: وضعیت ([ProSettings]) + فروشگاه ([ProStore]).
 * خرید قبلی خودش پیدا می‌شود (نصب دوباره یا گوشی تازه، حداکثر روزی یک بار) یا با «قبلاً خریدم».
 * خرید هیچ‌وقت خودکار پس گرفته نمی‌شود: اگر بازار یک بار خالی جواب داد (مثلاً کاربر از حسابش بیرون آمده)،
 * کسی که پول داده نباید امکاناتش را از دست بدهد.
 */
class ProAccess(private val settings: ProSettings, private val store: ProStore, private val scope: CoroutineScope) {

    val status: StateFlow<ProStatus> = settings.status

    /** خرید ممکن است (کلید فروشگاه در اپ هست) */
    val storeAvailable: Boolean get() = store.available

    /** اپ دیده شد (باز شد یا از پس‌زمینه برگشت): روزهای ماه رایگان، و اگر وقتش است، پرسیدن خرید قبلی */
    fun onAppVisible(now: Long = System.currentTimeMillis()) {
        settings.refresh(now)
        if (store.available && settings.checkDue(now)) {
            settings.markChecked(now)
            scope.launch { if (store.owned() == true) settings.markOwned() }
        }
    }

    /** «قبلاً خریدم» */
    suspend fun restore(): RestoreResult {
        if (!store.available) return RestoreResult.UNAVAILABLE
        return when (store.owned()) {
            true -> {
                settings.markOwned()
                RestoreResult.FOUND
            }
            false -> RestoreResult.NOT_FOUND
            null -> RestoreResult.UNAVAILABLE
        }
    }

    suspend fun price(): String? = store.price()

    /**
     * صفحه‌ی خرید. خرید ناموفق ممکن است فقط یعنی «قبلاً خریده‌ای» (روی گوشی دیگر)؛ پس یک بار هم می‌پرسیم.
     * خرید موفق همیشه ثبت می‌شود، حتی اگر صفحه‌ای که منتظر جواب بود دیگر نباشد.
     */
    suspend fun purchase(registry: ActivityResultRegistry): PurchaseResult {
        val result = suspendCancellableCoroutine<PurchaseResult> { cont ->
            store.purchase(registry) { r ->
                if (r == PurchaseResult.BOUGHT) settings.markOwned()
                if (cont.isActive) cont.resume(r)
            }
        }
        if (result != PurchaseResult.FAILED) return result
        return if (store.owned() == true) {
            settings.markOwned()
            PurchaseResult.BOUGHT
        } else {
            result
        }
    }

    /** «ماه رایگانت تموم شد» هنوز نشان داده نشده */
    val trialEndNoticeDue: Boolean get() = settings.trialEndNoticeDue

    fun markTrialEndNoticeSeen() = settings.markTrialEndNoticeSeen()
}
