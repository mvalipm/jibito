package ir.jibito.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ir.jibito.app.MainActivity
import ir.jibito.app.R
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.RecurringPaymentEntity
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

    /** «فردا موعد …» از این ساعتِ روز قبل (عصر، وقتی هنوز فرصت هست) */
    const val PRE_REMIND_FROM_HOUR = 18

    /** «بعداً یادم بنداز» تا این ساعت ← همان شب؛ بعدش ← فردا صبح */
    const val SNOOZE_TONIGHT_UNTIL_HOUR = 17
    const val SNOOZE_TONIGHT_HOUR = 20

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
        // بزرگ‌تر هم یعنی انجام‌شده: «پرداخت کردم» روی «فردا موعد…» در روز آخر ماه، ماه بعد را علامت می‌زند
        if (lastRemindedMonthKey != null && lastRemindedMonthKey >= monthKey) return false
        val due = dueDay(dayOfMonth, year, month)
        return day > due || (day == due && hour >= REMIND_FROM_HOUR)
    }

    /**
     * عصرِ روز قبل از موعد است؟ (فردا = [tomorrowYear]/[tomorrowMonth]/[tomorrowDay] شمسی)
     * ماهِ موعد هنوز یادآوری نشده باشد.
     */
    fun isDueTomorrow(
        dayOfMonth: Int, lastRemindedMonthKey: Int?,
        tomorrowYear: Int, tomorrowMonth: Int, tomorrowDay: Int, hour: Int,
    ): Boolean =
        hour >= PRE_REMIND_FROM_HOUR &&
            lastRemindedMonthKey != JalaliMonth(tomorrowYear, tomorrowMonth).key &&
            tomorrowDay == dueDay(dayOfMonth, tomorrowYear, tomorrowMonth)

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

    /** «بعداً یادم بنداز» تا کی؟ تا ساعت ۱۷ ← امشب ساعت ۲۰؛ بعدش ← فردا ساعت ۹ */
    fun snoozeUntil(now: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val tonight = cal.get(Calendar.HOUR_OF_DAY) < SNOOZE_TONIGHT_UNTIL_HOUR
        if (!tonight) cal.add(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, if (tonight) SNOOZE_TONIGHT_HOUR else REMIND_FROM_HOUR)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun snoozesTonight(now: Long): Boolean =
        Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY) < SNOOZE_TONIGHT_UNTIL_HOUR
}

/** «مانده برای این پرداخت کافی است؟» (بدون اندروید، قابل تست) */
object RecurringFunds {

    /** مانده‌ای قدیمی‌تر از این، دیگر قابل اعتماد نیست */
    const val FRESH_DAYS = 30

    /** چقدر کم است؛ null اگر کافی است یا هیچ مانده‌ی تازه‌ای نمی‌دانیم */
    fun shortfall(balancesRial: List<Long>, amountRial: Long): Long? {
        if (balancesRial.isEmpty()) return null
        return (amountRial - balancesRial.sum()).takeIf { it > 0 }
    }
}

/** «بعداً یادم بنداز» و «فردا موعد…» که نشان داده شد (در هر ماه یک بار) */
class RecurringReminderState(context: Context) {
    private val prefs = context.getSharedPreferences("recurring_reminders", Context.MODE_PRIVATE)

    fun snooze(id: Long, until: Long, monthKey: Int) {
        prefs.edit().putLong("snooze_until_$id", until).putInt("snooze_month_$id", monthKey).apply()
    }

    /** (تا کی، ماه موعد) یا null */
    fun snoozed(id: Long): Pair<Long, Int>? {
        val until = prefs.getLong("snooze_until_$id", 0L)
        return if (until > 0) until to prefs.getInt("snooze_month_$id", 0) else null
    }

    fun clearSnooze(id: Long) {
        prefs.edit().remove("snooze_until_$id").remove("snooze_month_$id").apply()
    }

    fun preRemindedMonth(id: Long): Int = prefs.getInt("pre_$id", 0)

    fun markPreReminded(id: Long, monthKey: Int) {
        prefs.edit().putInt("pre_$id", monthKey).apply()
    }
}

/**
 * هر بار که کار پس‌زمینه اجرا می‌شود (حداکثر هر ۱۵ دقیقه): اگر موعد پرداختی فرداست یا رسیده، خبر می‌دهد.
 * با دکمه‌های «✓ پرداخت کردم» و «بعداً یادم بنداز»؛ اگر مانده‌ی حساب‌ها (از پیامک بانک) کم است، همان‌جا می‌گوید.
 */
class RecurringReminder(private val context: Context, private val db: AppDatabase) {

    private val state = RecurringReminderState(context)

    suspend fun check(now: Long = System.currentTimeMillis()) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val (y, m, d) = Jalali.fromGregorian(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val tomorrow = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_MONTH, 1) }
        val (ty, tm, td) = Jalali.fromGregorian(tomorrow.get(Calendar.YEAR), tomorrow.get(Calendar.MONTH) + 1, tomorrow.get(Calendar.DAY_OF_MONTH))
        val dao = db.recurringDao()
        val monthKey = JalaliMonth(y, m).key
        for (p in dao.all()) {
            // «بعداً یادم بنداز»: تا وقتش نرسیده، هیچ
            val snooze = state.snoozed(p.id)
            if (snooze != null) {
                if (now < snooze.first) continue
                state.clearSnooze(p.id)
                // یادآوری ماه قبل به ماه تازه افتاد (مثلاً موعد روز آخر ماه بود) ← همان یادآوری دیرکرده
                if (snooze.second != monthKey && snooze.second != 0) {
                    val due = JalaliMonth(snooze.second / 100, snooze.second % 100)
                    remindDue(p, due.year, due.month, lateDueDay = RecurringSchedule.dueDay(p.dayOfMonth, due.year, due.month), now = now)
                    continue
                }
            }
            if (RecurringSchedule.isDue(p.dayOfMonth, p.lastRemindedMonthKey, y, m, d, hour)) {
                val late = RecurringSchedule.isLate(p.dayOfMonth, y, m, d)
                remindDue(p, y, m, lateDueDay = if (late) RecurringSchedule.dueDay(p.dayOfMonth, y, m) else null, now = now)
            } else if (RecurringSchedule.isDueTomorrow(p.dayOfMonth, p.lastRemindedMonthKey, ty, tm, td, hour)) {
                val dueKey = JalaliMonth(ty, tm).key
                if (state.preRemindedMonth(p.id) == dueKey) continue
                if (paid(p, ty, tm, now)) {
                    dao.markReminded(p.id, dueKey)
                    continue
                }
                if (show(p, dueKey, ReminderKind.TOMORROW, lateDueDay = null, now = now)) state.markPreReminded(p.id, dueKey)
            }
        }
    }

    /** یادآوری روز موعد (یا دیرکرده) برای ماه [year]/[month] */
    private suspend fun remindDue(p: RecurringPaymentEntity, year: Int, month: Int, lateDueDay: Int?, now: Long) {
        val dueKey = JalaliMonth(year, month).key
        // همان مبلغ از چند روز قبل از موعد تا الان از پیامک بانک برداشت شده ← پرداخت شده؛ یادآوری لازم نیست
        if (paid(p, year, month, now)) {
            db.recurringDao().markReminded(p.id, dueKey)
            return
        }
        if (show(p, dueKey, ReminderKind.DUE, lateDueDay, now)) db.recurringDao().markReminded(p.id, dueKey)
    }

    private suspend fun paid(p: RecurringPaymentEntity, year: Int, month: Int, now: Long): Boolean {
        val dueStart = RecurringSchedule.dueDayStart(p.dayOfMonth, year, month)
        val withdrawals = db.summaryDao()
            .amounts(FlowType.WITHDRAWAL.code, RecurringPaidCheck.windowStart(dueStart), now + 1)
            .map { it.amount }
        return RecurringPaidCheck.alreadyPaid(withdrawals, p.amountRial)
    }

    private enum class ReminderKind { TOMORROW, DUE }

    /**
     * true اگر نشان داده شد (بدون اجازه‌ی نوتیفیکیشن، دفعه‌ی بعد دوباره امتحان می‌شود)
     * @param lateDueDay اگر یادآوری دیر رسیده، روز موعدی که گذشت (متن: «موعد … روز ۱۱ بود»)
     */
    private suspend fun show(p: RecurringPaymentEntity, dueKey: Int, kind: ReminderKind, lateDueDay: Int?, now: Long): Boolean {
        if (!Notify.canPost(context)) return false
        ensureChannel()
        val title = when {
            kind == ReminderKind.TOMORROW -> context.getString(R.string.recurring_notif_title_tomorrow, p.title)
            lateDueDay == null -> context.getString(R.string.recurring_notif_title, p.title)
            else -> Jalali.toPersianDigits(context.getString(R.string.recurring_notif_title_late, p.title, lateDueDay))
        }
        val body = listOfNotNull(
            context.getString(R.string.recurring_notif_body, Money.toman(p.amountRial)),
            shortfallLine(p.amountRial, now),
        ).joinToString("\n")

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeLabel = context.getString(
            if (RecurringSchedule.snoozesTonight(now)) R.string.recurring_action_tonight else R.string.recurring_action_tomorrow
        )
        return Notify.post(context, notificationId(p.id), now) { redacted ->
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_jibito)
                .setContentTitle(title)
                // روی صفحه‌ی قفل: بدون مبلغ و مانده
                .apply {
                    if (!redacted) {
                        setContentText(body)
                        setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    }
                }
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(context, R.color.jibito_primary))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(0, context.getString(R.string.recurring_action_paid), RecurringActionReceiver.paidIntent(context, p.id, dueKey))
                .apply {
                    // «فردا موعد…» خودش فردا دوباره یادآوری می‌شود؛ «بعداً» فقط برای روز موعد
                    if (kind == ReminderKind.DUE) {
                        addAction(0, snoozeLabel, RecurringActionReceiver.snoozeIntent(context, p.id, dueKey))
                    }
                }
        }
    }

    /** «مانده‌ی ملت: ۳٬۰۰۰٬۰۰۰ تومان · ۵٬۰۰۰٬۰۰۰ تومان کم داری» — فقط اگر کم است */
    private suspend fun shortfallLine(amountRial: Long, now: Long): String? {
        val fresh = db.transactionFlowDao().bankBalances()
            .distinctBy { it.bankId }
            .filter { now - it.dateEpoch < RecurringFunds.FRESH_DAYS * 24L * 60 * 60 * 1000 }
        val missing = RecurringFunds.shortfall(fresh.map { it.remainAfter }, amountRial) ?: return null
        val label = fresh.singleOrNull()?.let { BankDirectory.byId(it.bankId)?.name }
            ?.let { context.getString(R.string.recurring_balance_of, it) }
            ?: context.getString(R.string.recurring_balance_all)
        return context.getString(R.string.recurring_shortfall, label, Money.toman(fresh.sumOf { it.remainAfter }), Money.toman(missing))
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

        fun notificationId(paymentId: Long): Int = NOTIFICATION_BASE + paymentId.toInt()
    }
}
