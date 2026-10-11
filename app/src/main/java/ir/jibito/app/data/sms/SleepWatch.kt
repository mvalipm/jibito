package ir.jibito.app.data.sms

import android.content.Context
import androidx.core.content.edit
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.notify.TransactionNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * خرجی که دیر پیدا شد: پیامکش [lagMillis] پیش‌تر رسیده بود و گیرنده‌ی پیامک (یا کار پس‌زمینه) به موقع اجرا نشد.
 * @param label اسم فروشگاه، وگرنه اسم بانک؛ برای مثالِ متن کارت
 */
data class LateFind(val foundAt: Long, val lagMillis: Long, val label: String?)

/** «گوشی منو خوابونده؟»: چند خرج در این هفته دیر پیدا شد و یک نمونه برای متن کارت */
data class SleepAlert(val count: Int, val example: LateFind)

/**
 * می‌پاید که گوشی اپ را برای صرفه‌جویی باتری می‌خواباند یا نه.
 * نشانه‌اش ساده و دقیق است: خرجی که در همگام‌سازی افزایشی بیش از [TransactionNotifier.RECENT_WINDOW_MILLIS]
 * بعد از رسیدن پیامکش پیدا شود، همان خرجی است که سؤال «مال چی بود؟» برایش نیامد.
 * زمان پیامک همان لحظه‌ی رسیدن به گوشی است، پس خاموش بودن گوشی یا آنتن نداشتن این‌جا شمرده نمی‌شود.
 * همه‌چیز در SharedPreferences و فقط روی همین گوشی است.
 */
class SleepWatch(context: Context) {

    private val prefs = context.getSharedPreferences("sleep_watch", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)

    /** با هر تغییر (خرج دیر، «فعلاً نه»، «انجام دادم») یکی زیاد می‌شود تا صفحه‌ها دوباره [alert] را بخوانند */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun alert(now: Long = System.currentTimeMillis()): SleepAlert? =
        alertOf(decode(prefs.getString(KEY_FINDS, null)), now, prefs.getLong(KEY_RESET_AT, 0L), prefs.getLong(KEY_SNOOZED_UNTIL, 0L))

    /** خرج‌های دیرپیداشده‌ی یک همگام‌سازی؛ قدیمی‌تر از دو هفته‌ها دور ریخته می‌شوند */
    fun record(finds: List<LateFind>, now: Long = System.currentTimeMillis()) {
        if (finds.isEmpty()) return
        val kept = (decode(prefs.getString(KEY_FINDS, null)) + finds)
            .filter { now - it.foundAt < KEEP_MILLIS }
            .takeLast(MAX_KEPT)
        prefs.edit { putString(KEY_FINDS, encode(kept)) }
        _version.value++
    }

    /** «فعلاً نه»: دو هفته ساکت */
    fun snooze(now: Long = System.currentTimeMillis()) {
        prefs.edit { putLong(KEY_SNOOZED_UNTIL, now + SNOOZE_MILLIS) }
        _version.value++
    }

    /** «انجام دادم»: شمارش از همین حالا از صفر؛ اگر باز دو خرج دیر پیدا شد، کارت برمی‌گردد */
    fun markFixed(now: Long = System.currentTimeMillis()) {
        prefs.edit { putLong(KEY_RESET_AT, now) }
        _version.value++
    }

    companion object {
        private const val KEY_FINDS = "finds"
        private const val KEY_RESET_AT = "reset_at"
        private const val KEY_SNOOZED_UNTIL = "snoozed_until"
        private const val DAY = 24 * 60 * 60 * 1000L
        const val WINDOW_MILLIS = 7 * DAY
        const val SNOOZE_MILLIS = 14 * DAY
        private const val KEEP_MILLIS = 14 * DAY
        private const val MAX_KEPT = 20
        /** چند خرجِ دیر در هفته تا کارت بیاید؛ یکی ممکن است اتفاقی باشد */
        const val MIN_FINDS = 2

        /**
         * از ردیف‌های تازه‌ی یک همگام‌سازی افزایشی، خرج‌هایی که دیر پیدا شدند: برداشت واقعی (نه خرید ناموفق یا انتقال به خود)
         * که پیامکش بیش از پنجره‌ی نوتیفیکیشن پیش‌تر رسیده بود.
         */
        fun lateFinds(inserted: List<TransactionFlowEntity>, now: Long): List<LateFind> =
            inserted
                .filter {
                    it.flowType == FlowType.WITHDRAWAL.code && !it.isFailedPurchase &&
                        it.transferState != TransactionFlowEntity.TRANSFER_SELF &&
                        now - it.dateEpoch > TransactionNotifier.RECENT_WINDOW_MILLIS
                }
                .map { LateFind(now, now - it.dateEpoch, it.merchant ?: BankDirectory.byId(it.bankId)?.name) }

        /**
         * کارت لازم است؟ دست‌کم [MIN_FINDS] خرج دیر در ۷ روز اخیر (و بعد از آخرین «انجام دادم»)، و «فعلاً نه» تمام شده باشد.
         * نمونه: تازه‌ترین خرجی که اسم دارد، وگرنه تازه‌ترین.
         */
        fun alertOf(finds: List<LateFind>, now: Long, resetAt: Long, snoozedUntil: Long): SleepAlert? {
            if (now < snoozedUntil) return null
            val since = maxOf(now - WINDOW_MILLIS, resetAt)
            val recent = finds.filter { it.foundAt >= since && it.foundAt <= now }
            if (recent.size < MIN_FINDS) return null
            val example = recent.filter { it.label != null }.maxByOrNull { it.foundAt } ?: recent.maxBy { it.foundAt }
            return SleepAlert(recent.size, example)
        }

        // هر خرج یک خط: «زمان پیدا شدن|تأخیر|اسم» (اسم ممکن است خالی باشد؛ خط جدید و | در اسم برداشته می‌شود)
        internal fun encode(finds: List<LateFind>): String =
            finds.joinToString("\n") { f ->
                listOf(f.foundAt.toString(), f.lagMillis.toString(), f.label.orEmpty().replace('\n', ' ').replace('|', ' '))
                    .joinToString("|")
            }

        internal fun decode(text: String?): List<LateFind> =
            text.orEmpty().lines().mapNotNull { line ->
                val parts = line.split('|', limit = 3)
                if (parts.size < 3) return@mapNotNull null
                val at = parts[0].toLongOrNull() ?: return@mapNotNull null
                val lag = parts[1].toLongOrNull() ?: return@mapNotNull null
                LateFind(at, lag, parts[2].ifBlank { null })
            }
    }
}
