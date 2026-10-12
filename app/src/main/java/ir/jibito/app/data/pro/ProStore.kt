package ir.jibito.app.data.pro

import androidx.activity.result.ActivityResultRegistry
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** نتیجه‌ی صفحه‌ی خرید فروشگاه */
enum class PurchaseResult {
    /** خرید شد (یا قبلاً خریده بود) و امضایش درست است */
    BOUGHT,

    /** کاربر صفحه‌ی خرید را بست */
    CANCELED,

    /** کافه‌بازار روی گوشی نیست (یا نسخه‌اش پرداخت درون‌برنامه‌ای ندارد) */
    NO_STORE,

    /** هر خطای دیگر: اینترنت، پرداخت ناموفق، … */
    FAILED,
}

/** فروشگاهی که «جیبیتوی کامل» را می‌فروشد (کافه‌بازار: [BazaarStore]؛ در تست‌ها نمونه‌ی ساختگی) */
interface ProStore {
    /** خرید ممکن است: کلید فروشگاه در اپ هست و درست است */
    val available: Boolean

    /** خرید قبلی را از فروشگاه می‌پرسد. null یعنی نشد بپرسیم (فروشگاه نیست، وصل نشد) */
    suspend fun owned(): Boolean?

    /** قیمت فعلی، همان‌طور که فروشگاه می‌نویسد؛ null اگر نشد بپرسیم */
    suspend fun price(): String?

    /** صفحه‌ی خرید فروشگاه (از رشته‌ی اصلی صدا زده شود)؛ نتیجه روی رشته‌ی اصلی */
    fun purchase(registry: ActivityResultRegistry, onResult: (PurchaseResult) -> Unit)
}

/** تنظیمات خرید در کافه‌بازار */
object BazaarConfig {
    /** شناسه‌ی محصول در پیشخان کافه‌بازار (پرداخت درون‌برنامه‌ای ← محصولات)؛ مصرف‌نشدنی، یک بار برای همیشه */
    const val PRODUCT_ID = "jibito_pro"

    /**
     * کلید عمومی RSA از پیشخان کافه‌بازار (پرداخت درون‌برنامه‌ای). عمومی است، پس جای پنهان لازم ندارد؛
     * با آن امضای هر خرید روی همین گوشی بررسی می‌شود. تا خالی است، خرید غیرفعال است و برگه‌ی خرید
     * می‌گوید «هنوز آماده نیست» (BazaarConfigTest کلید خراب را پیش از ساخت می‌گیرد).
     */
    const val RSA_PUBLIC_KEY = ""

    /**
     * کلید، یک کلید عمومی RSA درست است؟ Poolakey با کلید خراب موقع پرسیدن خریدها در رشته‌ی پس‌زمینه
     * خطای گرفته‌نشده می‌دهد و اپ بسته می‌شود؛ پس با کلید نادرست اصلاً سراغ بازار نمی‌رویم.
     */
    fun isValidKey(key: String): Boolean {
        val compact = compact(key)
        if (compact.isEmpty() || !compact.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "+/=" }) return false
        return runCatching {
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(compact)))
        }.isSuccess
    }

    /** کلید بدون فاصله و خط تازه (کپی از پیشخان گاهی چندخطی است) */
    fun compact(key: String): String = key.filterNot(Char::isWhitespace)
}
