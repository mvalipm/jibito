package ir.jibito.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.jibito.app.MainActivity
import ir.jibito.app.R
import ir.jibito.app.data.category.SpendRollup
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * خلاصه‌ی هفتگی: جمعه‌ها عصر، «این هفته چقدر خرج کردی، نسبت به هفته‌ی قبل، و بیشترِ خرج کجا رفت».
 * مثل یادآوری پرداخت‌ها، در همان کار پس‌زمینه‌ی ۱۵ دقیقه‌ای بررسی می‌شود؛ زمان‌بندی جدا ندارد.
 * از تنظیمات خاموش می‌شود (یا از کانال نوتیفیکیشنش در تنظیمات گوشی).
 */
class WeeklyDigest(private val context: Context, private val db: AppDatabase) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    suspend fun check(now: Long = System.currentTimeMillis()) {
        if (!_enabled.value) return
        val lastSent = prefs.getLong(KEY_LAST_SLOT, 0L)
        if (!WeeklyDigestRule.isDue(now, lastSent)) return

        val categories = db.categoryDao().all()
        val byId = categories.associateBy { it.id }
        val rows = db.summaryDao().amounts(FlowType.WITHDRAWAL.code, now - 14 * WeeklyDigestRule.DAY_MS, now)
        val spends = SpendRollup.spendsOnly(rows, categories).map { row ->
            Triple(row.dateEpoch, row.amount, row.categoryId?.let { byId[it] }?.let { SpendRollup.rootOf(it, byId).id })
        }
        val digest = WeeklyDigestRule.summarize(spends, now)
        // هفته‌ی بی‌خرج: چیزی برای گفتن نیست (و نوتیفیکیشن بی‌فایده نمی‌فرستیم)
        if (digest.thisWeekRial == 0L || show(digest, digest.topRootId?.let { byId[it]?.name })) {
            prefs.edit().putLong(KEY_LAST_SLOT, WeeklyDigestRule.lastSlot(now)).apply()
        }
    }

    /** true اگر نشان داده شد (بدون اجازه‌ی نوتیفیکیشن، دفعه‌ی بعد دوباره امتحان می‌شود) */
    private fun show(d: WeeklyDigestRule.Digest, topName: String?): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        ensureChannel()
        val change = d.changePercent?.let { p ->
            when {
                kotlin.math.abs(p) < 3 -> context.getString(R.string.digest_same)
                p > 0 -> context.getString(R.string.digest_more, p)
                else -> context.getString(R.string.digest_less, -p)
            }
        }
        val body = Jalali.toPersianDigits(
            listOfNotNull(change, topName?.let { context.getString(R.string.digest_top, it) }).joinToString(" · ")
        )
        val openApp = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jibito)
            .setContentTitle(context.getString(R.string.digest_title, Money.compact(d.thisWeekRial)))
            .apply { if (body.isNotBlank()) setContentText(body) }
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.jibito_primary))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.digest_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.digest_channel_desc) }
        )
    }

    companion object {
        const val CHANNEL_ID = "weekly_digest"
        private const val NOTIFICATION_ID = 700_000_001
        private const val PREFS = "weekly_digest"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_SLOT = "last_slot"
    }
}
