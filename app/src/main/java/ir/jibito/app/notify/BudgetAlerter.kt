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
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.category.SpendRollup
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money

/** سطح هشدار بودجه: ۰ = عادی، ۸۰ = نزدیک سقف، ۱۰۰ = تمام شده. */
object BudgetLevel {
    const val WARNING_PERCENT = 80

    fun of(spentRial: Long, budgetRial: Long): Int = when {
        budgetRial <= 0 -> 0
        spentRial >= budgetRial -> 100
        spentRial * 100 >= budgetRial * WARNING_PERCENT -> 80
        else -> 0
    }
}

/**
 * بعد از هر تراکنش تازه یا هر تغییر دسته صدا زده می‌شود.
 * اگر خرج یک دسته در این ماه از ۸۰٪ یا ۱۰۰٪ بودجه‌اش رد شد، یک بار (در هر ماه، برای هر سطح) خبر می‌دهد.
 */
class BudgetAlerter(
    private val context: Context,
    private val db: AppDatabase,
) {
    private val dao = db.summaryDao()

    suspend fun check() {
        val month = JalaliMonth.current()
        val categories = db.categoryDao().all().filter { it.flowType == FlowType.WITHDRAWAL.code }
        // خرج‌ها روی دسته‌ی اصلی جمع می‌شوند (بودجه فقط روی دسته‌ی اصلی است)
        val spend = SpendRollup.rollup(categories, dao.sums(FlowType.WITHDRAWAL.code, month.startMillis(), month.endMillis()))
        checkOverall(month, spend.total)

        val byId = categories.associateBy { it.id }
        for (budget in dao.budgets()) {
            val category = byId[budget.categoryId] ?: continue
            val spent = spend.byRoot[budget.categoryId] ?: 0L
            val level = BudgetLevel.of(spent, budget.monthlyLimitRial)
            val alreadyAlerted = if (budget.alertedMonthKey == month.key) budget.alertedLevel else 0
            if (level > alreadyAlerted) {
                show(category.id, category.name, category.icon, level, spent, budget.monthlyLimitRial)
                dao.markAlerted(category.id, month.key, level)
            }
        }
    }

    /** بودجه‌ی کل ماه: همان قانون ۸۰٪ و ۱۰۰٪، یک بار در ماه برای هر سطح */
    private suspend fun checkOverall(month: JalaliMonth, spent: Long) {
        val budget = dao.overallBudget() ?: return
        val level = BudgetLevel.of(spent, budget.monthlyLimitRial)
        val alreadyAlerted = if (budget.alertedMonthKey == month.key) budget.alertedLevel else 0
        if (level > alreadyAlerted) {
            val title = context.getString(
                if (level >= 100) R.string.budget_alert_overall_over else R.string.budget_alert_overall_warning
            )
            notify(OVERALL_NOTIFICATION_ID, title, spent, budget.monthlyLimitRial, month, categoryId = null)
            dao.markOverallAlerted(month.key, level)
        }
    }

    private fun show(categoryId: Long, name: String, icon: String?, level: Int, spent: Long, limit: Long) {
        val label = listOfNotNull(icon, name).joinToString(" ")
        val title = if (level >= 100) {
            context.getString(R.string.budget_alert_over, label)
        } else {
            context.getString(R.string.budget_alert_warning, label)
        }
        // یک نوتیفیکیشن ثابت برای هر دسته؛ هشدار ۱۰۰٪ جای هشدار ۸۰٪ را می‌گیرد
        notify(notificationId(categoryId), title, spent, limit, JalaliMonth.current(), categoryId)
    }

    /**
     * نوار پیشرفت (چقدر از سقف رفته)، «۲٫۲ میلیون از سقف ۲ میلیون · ۱۲ روز تا آخر ماه»
     * و دو دکمه: «ببینم کجاها» و «تا آخر ماه ساکت» (برای همین دسته، فقط همین ماه).
     * @param categoryId null یعنی بودجه‌ی کل ماه
     */
    private fun notify(id: Int, title: String, spent: Long, limit: Long, month: JalaliMonth, categoryId: Long?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel()
        val body = context.getString(R.string.budget_alert_body, Money.compact(spent), Money.compact(limit))
        val daysLeft = ((month.endMillis() - System.currentTimeMillis()) / DAY_MS + 1).toInt()
        val text = if (daysLeft > 0) {
            "$body · " + Jalali.toPersianDigits(context.getString(R.string.budget_alert_days_left, daysLeft))
        } else {
            body
        }
        val percent = if (limit > 0) (spent * 100 / limit).toInt().coerceIn(0, 100) else 100

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jibito)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setProgress(100, percent, false)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.jibito_primary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, context.getString(R.string.budget_alert_open), openApp)
            .addAction(0, context.getString(R.string.budget_alert_mute), BudgetMuteReceiver.intent(context, id, categoryId, month.key))
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.budget_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.budget_channel_desc) }
        )
    }

    companion object {
        const val CHANNEL_ID = "budget"
        private const val NOTIFICATION_BASE = 1_000_000_000
        private const val OVERALL_NOTIFICATION_ID = 999_999_999
        private const val DAY_MS = 24L * 60 * 60 * 1000

        fun notificationId(categoryId: Long): Int = NOTIFICATION_BASE + categoryId.toInt()
    }
}
