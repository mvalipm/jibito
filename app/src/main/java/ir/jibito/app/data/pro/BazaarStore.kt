package ir.jibito.app.data.pro

import android.content.Context
import androidx.activity.result.ActivityResultRegistry
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.entity.PurchaseInfo
import ir.cafebazaar.poolakey.entity.PurchaseState
import ir.cafebazaar.poolakey.exception.BazaarNotFoundException
import ir.cafebazaar.poolakey.exception.BazaarNotSupportedException
import ir.cafebazaar.poolakey.exception.IAPNotSupportedException
import ir.cafebazaar.poolakey.request.PurchaseRequest
import ir.jibito.app.util.ErrorLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * خرید «جیبیتوی کامل» از کافه‌بازار با Poolakey (کتابخانه‌ی رسمی بازار).
 * همه‌ی کار با خود اپ بازار است و جیبیتو اجازه‌ی اینترنت لازم ندارد: بازار خرید را انجام می‌دهد و نتیجه را
 * با امضای خودش برمی‌گرداند؛ امضا با کلید عمومی [BazaarConfig.RSA_PUBLIC_KEY] روی همین گوشی بررسی می‌شود.
 * برای هر کار یک اتصال تازه باز و بعدش بسته می‌شود (اتصال باز، بازار را بی‌دلیل بیدار نگه می‌دارد).
 */
class BazaarStore(context: Context) : ProStore {

    private val appContext = context.applicationContext
    private val key = BazaarConfig.compact(BazaarConfig.RSA_PUBLIC_KEY)

    override val available: Boolean = BazaarConfig.isValidKey(key)

    private fun payment() = Payment(
        appContext,
        // فقط محصول یک‌باره داریم؛ با true، بازاری که اشتراک ندارد اتصال را رد می‌کند
        PaymentConfiguration(localSecurityCheck = SecurityCheck.Enable(key), shouldSupportSubscription = false),
    )

    override suspend fun owned(): Boolean? = connected<Boolean> { payment, done ->
        payment.getPurchasedProducts {
            querySucceed { purchases -> done(purchases.any(::isPro)) }
            queryFailed { e ->
                ErrorLog.record(appContext, "bazaar query", e)
                done(null)
            }
        }
    }

    override suspend fun price(): String? = connected<String> { payment, done ->
        payment.getInAppSkuDetails(listOf(BazaarConfig.PRODUCT_ID)) {
            getSkuDetailsSucceed { list -> done(list.firstOrNull { it.sku == BazaarConfig.PRODUCT_ID }?.price?.takeIf { it.isNotBlank() }) }
            getSkuDetailsFailed { done(null) }
        }
    }

    override fun purchase(registry: ActivityResultRegistry, onResult: (PurchaseResult) -> Unit) {
        if (!available) {
            onResult(PurchaseResult.FAILED)
            return
        }
        val payment = payment()
        var connection: Connection? = null
        var finished = false
        var closed = false
        // قطع اتصال، خودش «disconnected» را خبر می‌دهد؛ دو بار قطع کردن در Poolakey خطا می‌دهد
        fun close() {
            if (closed) return
            closed = true
            connection?.disconnect()
        }
        fun finish(result: PurchaseResult) {
            if (finished) return
            finished = true
            onResult(result)
            close()
        }
        connection = payment.connect {
            connectionSucceed {
                payment.purchaseProduct(registry, PurchaseRequest(BazaarConfig.PRODUCT_ID)) {
                    purchaseSucceed { finish(if (isPro(it)) PurchaseResult.BOUGHT else PurchaseResult.FAILED) }
                    purchaseCanceled { finish(PurchaseResult.CANCELED) }
                    purchaseFailed { e ->
                        ErrorLog.record(appContext, "bazaar purchase", e)
                        finish(PurchaseResult.FAILED)
                    }
                    failedToBeginFlow { e ->
                        ErrorLog.record(appContext, "bazaar purchase start", e)
                        finish(PurchaseResult.FAILED)
                    }
                }
            }
            connectionFailed { e -> finish(if (e.isNoStore()) PurchaseResult.NO_STORE else PurchaseResult.FAILED) }
            disconnected {
                closed = true
                finish(PurchaseResult.FAILED)
            }
        }
    }

    /**
     * یک اتصال برای یک پرسش: وصل می‌شود، [ask] را صدا می‌زند و با اولین جواب قطع می‌کند.
     * Poolakey گاهی یک خطا را دو بار خبر می‌دهد (مثلاً «بازار نیست» از هر دو راه اتصال)؛ فقط اولی حساب است.
     * بازاری که جواب ندهد، کار را بیشتر از [TIMEOUT_MILLIS] معطل نمی‌کند.
     */
    private suspend fun <T> connected(ask: (Payment, (T?) -> Unit) -> Unit): T? {
        if (!available) return null
        return withContext(Dispatchers.Main) {
            withTimeoutOrNull(TIMEOUT_MILLIS) {
                suspendCancellableCoroutine { cont ->
                    val payment = payment()
                    var connection: Connection? = null
                    var answered = false
                    var closed = false
                    // قطع اتصال، خودش «disconnected» را خبر می‌دهد؛ دو بار قطع کردن در Poolakey خطا می‌دهد
                    fun close() {
                        if (closed) return
                        closed = true
                        connection?.disconnect()
                    }
                    fun done(value: T?) {
                        if (answered) return
                        answered = true
                        if (cont.isActive) cont.resume(value)
                        close()
                    }
                    connection = payment.connect {
                        connectionSucceed { ask(payment, ::done) }
                        connectionFailed { done(null) }
                        disconnected {
                            closed = true
                            done(null)
                        }
                    }
                    cont.invokeOnCancellation { close() }
                }
            }
        }
    }

    private fun isPro(purchase: PurchaseInfo) =
        purchase.productId == BazaarConfig.PRODUCT_ID && purchase.purchaseState == PurchaseState.PURCHASED

    private fun Throwable.isNoStore() =
        this is BazaarNotFoundException || this is BazaarNotSupportedException || this is IAPNotSupportedException

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
    }
}
