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
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar
import ir.jibito.app.data.parser.FlowType

/** قانون یادآوری پرداخت تکراری (بدون اندروید، قابل تست). */
object RecurringSchedule {

    /** یادآوری‌ها از این ساعت به بعد (نه نیمه‌شب) */
    const val REMIND_FROM_HOUR = 9

    /** چند روز دارد این ماه شمسی؟ */
    fun monthLength(year: Int, month: Int): Int {
        val (gy, gm, gd) = Jalali.toGregorian(year, month, 1)
        val next = JalaliMonth(year, month).plus(1)
        val (ny, nm, nd) = Jalali.toGregorian(next.year, next.month, 1)
        return ChronoUnit.DAYS.between(LocalDate.of(gy, gm, gd), LocalDate.of(ny, nm, nd)).toInt()
    }

    /** روز واقعی موعد در این ماه (مثلاً «روز ۳۱» در ماه ۳۰ روزه ← روز ۳۰) */
    fun dueDay(dayOfMonth: Int, year: Int, month: Int): Int = dayOfMonth.coerceIn(1, monthLength(year, month))

    /** امروز (یا روزهای بعدش، اگر گوشی خاموش بود) وقت یادآوری است و این ماه هنوز یادآوری نشده؟ */
    fun isDue(dayOfMonth: Int, lastRemindedMonthKey: Int?, year: Int, month: Int, day: Int, hour: Int): Boolean {
        val monthKey = JalaliMonth(year, month).key
        if (lastRemindedMonthKey == monthKey) return false
        val due = dueDay(dayOfMonth, year, month)
        return day > due || (day == due && hour >= REMIND_FROM_HOUR)
    }

    /** یادآوری با تأخیر می‌آید (مثلاً گوشی روز موعد خاموش بود)؟ آن‌وقت متن نباید «امروز» بگوید */
    fun isLate(dayOfMonth: Int, year: Int, month: Int, day: Int): Boolean = day > dueDay(dayOfMonth, year, month)

    /** ساعت ۰۰:۰۰ روز موعد این ماه (به وقت گوشی) */
    fun dueDayStart(dayOfMonth: Int, year: Int, month: Int): Long {
        val (gy, gm, gd) = Jalali.toGregorian(year, month, dueDay(dayOfMonth, year, month))
        return Calendar.getInstance().apply {
            clear()
            set(gy, gm - 1, gd, 0, 0, 0)
        }.timeInMillis
    }

    /**
     * پرداختی که امروز ساخته می‌شود و موعد این ماهش گذشته، برای همین ماه یادآوری نشود
     * (کاربر خودش همین الان به آن فکر کرده). مقدار برای ستون lastRemindedMonthKey.
     */
    fun initialRemindedKey(dayOfMonth: Int, year: Int, month: Int, day: Int): Int? =
        if (day >= dueDay(dayOfMonth, year, month)) JalaliMonth(year, month).key else null
}

/** هر بار که کار پس‌زمینه اجرا می‌شود (حداکثر هر ۱۵ دقیقه): اگر موعد پرداختی رسیده، خبر می‌دهد. */
class RecurringReminder(private val context: Context, private val db: AppDatabase) {

    suspend fun check(now: Long = System.currentTimeMillis()) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val (y, m, d) = Jalali.fromGregorian(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dao = db.recurringDao()
        val monthKey = JalaliMonth(y, m).key
        for (p in dao.all()) {
            if (!RecurringSchedule.isDue(p.dayOfMonth, p.lastRemindedMonthKey, y, m, d, hour)) continue
            // همان مبلغ از چند روز قبل از موعد تا الان از پیامک بانک برداشت شده ← پرداخت شده؛ یادآوری لازم نیست
            val dueStart = RecurringSchedule.dueDayStart(p.dayOfMonth, y, m)
            val withdrawals = db.summaryDao()
                .amounts(FlowType.WITHDRAWAL.code, RecurringPaidCheck.windowStart(dueStart), now + 1)
                .map { it.amount }
            if (RecurringPaidCheck.alreadyPaid(withdrawals, p.amountRial)) {
                dao.markReminded(p.id, monthKey)
                continue
            }
            val dueDay = RecurringSchedule.dueDay(p.dayOfMonth, y, m)
            val late = RecurringSchedule.isLate(p.dayOfMonth, y, m, d)
            if (show(p.id, p.title, p.amountRial, if (late) dueDay else null)) dao.markReminded(p.id, monthKey)
        }
    }

    /** true اگر نشان داده شد (بدون اجازه‌ی نوتیفیکیشن، دفعه‌ی بعد دوباره امتحان می‌شود) */
    /** @param lateDueDay اگر یادآوری دیر رسیده، روز موعدی که گذشت (متن: «موعد … روز ۱۱ بود») */
    private fun show(id: Long, title: String, amountRial: Long, lateDueDay: Int?): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        ensureChannel()
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jibito)
            .setContentTitle(
                if (lateDueDay == null) context.getString(R.string.recurring_notif_title, title)
                else Jalali.toPersianDigits(context.getString(R.string.recurring_notif_title_late, title, lateDueDay))
            )
            .setContentText(context.getString(R.string.recurring_notif_body, Money.toman(amountRial)))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.jibito_primary))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE + id.toInt(), notification)
        return true
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.recurring_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.recurring_channel_desc) }
        )
    }

    companion object {
        const val CHANNEL_ID = "recurring"
        private const val NOTIFICATION_BASE = 500_000_000
    }
}
