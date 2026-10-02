package ir.jibito.app.notify

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/** ساعت آرام: نوتیفیکیشن‌ها بی‌صدا می‌آیند (بدون اندروید، قابل تست). */
object QuietHours {
    const val START_HOUR = 23
    const val END_HOUR = 7

    fun isQuiet(hour: Int): Boolean = hour >= START_HOUR || hour < END_HOUR
}

/** تنظیمات کاربر برای همه‌ی نوتیفیکیشن‌ها (تنظیمات ← نوتیفیکیشن‌ها) */
class NotificationSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _hideOnLockScreen = MutableStateFlow(prefs.getBoolean(KEY_HIDE_LOCK, true))
    /** مبلغ‌ها روی صفحه‌ی قفل گوشی دیده نشوند */
    val hideOnLockScreen: StateFlow<Boolean> = _hideOnLockScreen.asStateFlow()

    private val _quietHours = MutableStateFlow(prefs.getBoolean(KEY_QUIET, true))
    /** شب‌ها (۲۳ تا ۷) بی‌صدا */
    val quietHours: StateFlow<Boolean> = _quietHours.asStateFlow()

    fun setHideOnLockScreen(value: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_LOCK, value).apply()
        _hideOnLockScreen.value = value
    }

    fun setQuietHours(value: Boolean) {
        prefs.edit().putBoolean(KEY_QUIET, value).apply()
        _quietHours.value = value
    }

    companion object {
        private const val PREFS = "notification_settings"
        private const val KEY_HIDE_LOCK = "hide_on_lock_screen"
        private const val KEY_QUIET = "quiet_hours"

        /** خواندن مستقیم (بیرون از AppContainer، مثلاً در گیرنده‌ها) */
        fun hideOnLockScreen(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIDE_LOCK, true)

        fun quietHours(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_QUIET, true)
    }
}

/**
 * فرستادن همه‌ی نوتیفیکیشن‌های اپ از یک جا، با قانون‌های مشترک:
 * - حریم خصوصی: روی صفحه‌ی قفل فقط نسخه‌ی بی‌مبلغ دیده می‌شود. اگر گوشی موقع رسیدن خبر قفل است،
 *   خود خبر هم بی‌مبلغ فرستاده می‌شود و با باز شدن قفل، نسخه‌ی کامل (بی‌صدا) جایش را می‌گیرد.
 * - ساعت آرام: شب‌ها بی‌صدا.
 */
object Notify {

    /** نسخه‌ی کامل خبرهایی که بی‌مبلغ فرستاده شدند؛ با باز شدن قفل گوشی دوباره فرستاده می‌شوند */
    private val pendingFull = ConcurrentHashMap<Int, (redacted: Boolean) -> NotificationCompat.Builder>()

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * @param build خبر را می‌سازد؛ redacted=true یعنی بدون مبلغ و جزئیات (برای صفحه‌ی قفل)
     * @return false اگر اجازه‌ی نوتیفیکیشن نیست
     */
    fun post(
        context: Context,
        id: Int,
        now: Long = System.currentTimeMillis(),
        build: (redacted: Boolean) -> NotificationCompat.Builder,
    ): Boolean {
        // بررسی صریح همین‌جا (نه فقط در canPost) تا Lint هم ببیند
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val hide = NotificationSettings.hideOnLockScreen(context)
        val redacted = hide && isLocked(context)
        val builder = withPublicVersion(build(redacted), build)
        if (NotificationSettings.quietHours(context) && QuietHours.isQuiet(hourOf(now))) builder.setSilent(true)
        NotificationManagerCompat.from(context).notify(id, builder.build())
        if (redacted) pendingFull[id] = build else pendingFull.remove(id)
        return true
    }

    fun cancel(context: Context, id: Int) {
        pendingFull.remove(id)
        NotificationManagerCompat.from(context).cancel(id)
    }

    /** قفل گوشی باز شد: خبرهای بی‌مبلغی که هنوز دیده می‌شوند، کامل شوند (بی‌صدا و بی‌لرزش) */
    fun revealAfterUnlock(context: Context) {
        if (pendingFull.isEmpty() || isLocked(context)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val active = context.getSystemService(NotificationManager::class.java)
            .activeNotifications.map { it.id }.toSet()
        for ((id, build) in pendingFull.entries.toList()) {
            pendingFull.remove(id)
            if (id !in active) continue
            val builder = withPublicVersion(build(false), build)
                .setOnlyAlertOnce(true)
                .setSilent(true)
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }
    }

    /** یک بار در شروع اپ: گوش دادن به باز شدن قفل گوشی (تا اپ زنده است) */
    fun watchUnlock(context: Context) {
        val app = context.applicationContext
        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) = revealAfterUnlock(app)
            },
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** روی صفحه‌ی قفل همیشه نسخه‌ی بی‌مبلغ و بی‌دکمه دیده شود (وقتی تنظیم «جزئیات حساس» گوشی پنهان است) */
    private fun withPublicVersion(
        builder: NotificationCompat.Builder,
        build: (redacted: Boolean) -> NotificationCompat.Builder,
    ): NotificationCompat.Builder = builder
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(build(true).clearActions().setStyle(null).build())

    private fun isLocked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun hourOf(now: Long): Int = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
}
