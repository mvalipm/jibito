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
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.category.SpendRollup
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
import java.util.Calendar

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
 * سرعت خرج نسبت به روزهای گذشته‌ی ماه (بدون اندروید، قابل تست).
 * ۸۰٪ بودجه در روز ۲۸ طبیعی است، در روز ۱۰ نه؛ این‌جا همین فرق حساب می‌شود.
 * @param day روز امروز در ماه شمسی (از ۱)، [monthLength] تعداد روزهای ماه
 */
object BudgetPace {
    /** هشدار «با این سرعت تموم می‌شه» (پیش از ۸۰٪) */
    const val LEVEL = 50

    /** چند روز اول ماه، سرعت خرج هنوز معنی ندارد (مثلاً اجاره‌ی اول ماه) */
    const val MIN_DAY = 5

    /** خرج تا آخر ماه، اگر به همین سرعت ادامه پیدا کند */
    fun projected(spentRial: Long, day: Int, monthLength: Int): Long = spentRial * monthLength / day.coerceAtLeast(1)

    /**
     * دست‌کم نصف بودجه رفته و با این سرعت، بیش از ۱۰٪ از بودجه رد می‌شود (قبل از تمام شدن ماه).
     */
    fun onPaceToOverrun(spentRial: Long, budgetRial: Long, day: Int, monthLength: Int): Boolean =
        budgetRial > 0 && day >= MIN_DAY && day < monthLength &&
            spentRial < budgetRial && spentRial * 2 >= budgetRial &&
            projected(spentRial, day, monthLength) > budgetRial + budgetRial / 10

    /** روزی از ماه که با این سرعت بودجه تمام می‌شود؛ null اگر تا آخر ماه تمام نمی‌شود (یا همین حالا تمام شده) */
    fun runOutDay(spentRial: Long, budgetRial: Long, day: Int, monthLength: Int): Int? {
        if (spentRial <= 0 || spentRial >= budgetRial) return null
        val d = Math.ceil(budgetRial.toDouble() * day / spentRial).toInt()
        return if (d <= monthLength) d.coerceAtLeast(day) else null
    }

    /** باقی‌مانده‌ی بودجه تقسیم بر روزهای مانده (با امروز) */
    fun dailyAllowance(spentRial: Long, budgetRial: Long, day: Int, monthLength: Int): Long {
        val remaining = budgetRial - spentRial
        if (remaining <= 0) return 0
        return remaining / (monthLength - day + 1).coerceAtLeast(1)
    }

    /** سطحی که باید خبر داده شود: ۱۰۰، ۸۰، ۵۰ (سرعت) یا ۰ */
    fun alertLevel(spentRial: Long, budgetRial: Long, day: Int, monthLength: Int): Int {
        val level = BudgetLevel.of(spentRial, budgetRial)
        if (level > 0) return level
        return if (onPaceToOverrun(spentRial, budgetRial, day, monthLength)) LEVEL else 0
    }
}

/**
 * بعد از هر تراکنش تازه یا هر تغییر دسته صدا زده می‌شود.
 * اگر خرج یک دسته در این ماه با سرعتی است که از بودجه رد می‌شود، یا از ۸۰٪ یا ۱۰۰٪ بودجه‌اش رد شد،
 * یک بار (در هر ماه، برای هر سطح) خبر می‌دهد — همراه با «روزی چقدر جا داری».
 */
class BudgetAlerter(
    private val context: Context,
    private val db: AppDatabase,
) {
    private val dao = db.summaryDao()

    suspend fun check(now: Long = System.currentTimeMillis()) {
        val month = JalaliMonth.of(now)
        val day = jalaliDay(now)
        val monthLength = RecurringSchedule.monthLength(month.year, month.month)
        val today = Today(month, day, monthLength)
        val categories = db.categoryDao().all().filter { it.flowType == FlowType.WITHDRAWAL.code }
        // خرج‌ها روی دسته‌ی اصلی جمع می‌شوند (بودجه فقط روی دسته‌ی اصلی است)
        val spend = SpendRollup.rollup(categories, dao.sums(FlowType.WITHDRAWAL.code, month.startMillis(), month.endMillis()))
        checkOverall(today, spend.total)

        val byId = categories.associateBy { it.id }
        for (budget in dao.budgets()) {
            val category = byId[budget.categoryId] ?: continue
            val spent = spend.byRoot[budget.categoryId] ?: 0L
            val level = BudgetPace.alertLevel(spent, budget.monthlyLimitRial, day, monthLength)
            val alreadyAlerted = if (budget.alertedMonthKey == month.key) budget.alertedLevel else 0
            if (level > alreadyAlerted) {
                val label = listOfNotNull(category.icon, category.name).joinToString(" ")
                // یک نوتیفیکیشن ثابت برای هر دسته؛ هشدار بالاتر جای قبلی را می‌گیرد
                if (show(NOTIFICATION_BASE + category.id.toInt(), label, level, spent, budget.monthlyLimitRial, today)) {
                    dao.markAlerted(category.id, month.key, level)
                }
            }
        }
    }

    /** بودجه‌ی کل ماه: همان قانون‌ها، یک بار در ماه برای هر سطح */
    private suspend fun checkOverall(today: Today, spent: Long) {
        val budget = dao.overallBudget() ?: return
        val level = BudgetPace.alertLevel(spent, budget.monthlyLimitRial, today.day, today.monthLength)
        val alreadyAlerted = if (budget.alertedMonthKey == today.month.key) budget.alertedLevel else 0
        if (level > alreadyAlerted) {
            val label = context.getString(R.string.budget_overall_label)
            if (show(OVERALL_NOTIFICATION_ID, label, level, spent, budget.monthlyLimitRial, today)) {
                dao.markOverallAlerted(today.month.key, level)
            }
        }
    }

    private class Today(val month: JalaliMonth, val day: Int, val monthLength: Int)

    /** @return false اگر اجازه‌ی نوتیفیکیشن نیست (دفعه‌ی بعد دوباره امتحان می‌شود) */
    private fun show(id: Int, label: String, level: Int, spent: Long, limit: Long, today: Today): Boolean {
        val over = level >= 100
        val title = when {
            over -> context.getString(R.string.budget_alert_over, label)
            level >= BudgetLevel.WARNING_PERCENT -> context.getString(R.string.budget_alert_warning, label)
            else -> {
                val runOut = BudgetPace.runOutDay(spent, limit, today.day, today.monthLength) ?: today.monthLength
                val date = Jalali.toPersianDigits("$runOut ${Jalali.MONTH_NAMES[today.month.month - 1]}")
                context.getString(R.string.budget_alert_pace, label, date)
            }
        }
        val text = if (over) {
            val excess = spent - limit
            if (excess > 0) context.getString(R.string.budget_alert_body_over, Money.compact(excess), Money.toman(spent), Money.toman(limit))
            else context.getString(R.string.budget_alert_body, Money.toman(spent), Money.toman(limit))
        } else {
            val allowance = BudgetPace.dailyAllowance(spent, limit, today.day, today.monthLength)
            context.getString(R.string.budget_alert_body_allowance, Money.toman(spent), Money.toman(limit), Money.compact(allowance))
        }
        val channel = if (over) CHANNEL_ID else SOFT_CHANNEL_ID
        ensureChannels()

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notify.post(context, id) { redacted ->
            NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_jibito)
                .setContentTitle(title)
                // روی صفحه‌ی قفل: بدون مبلغ‌ها
                .apply {
                    if (!redacted) {
                        setContentText(text)
                        setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    }
                }
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(context, R.color.jibito_primary))
                .setPriority(if (over) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        }
    }

    private fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.budget_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = context.getString(R.string.budget_channel_desc) }
            )
        }
        // هشدارهای پیش از تمام شدن: بی‌مزاحمت‌تر (بدون بالا آمدن روی صفحه)
        if (manager.getNotificationChannel(SOFT_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    SOFT_CHANNEL_ID,
                    context.getString(R.string.budget_soft_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.budget_soft_channel_desc) }
            )
        }
    }

    private fun jalaliDay(now: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return Jalali.fromGregorian(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)).third
    }

    companion object {
        const val CHANNEL_ID = "budget"
        const val SOFT_CHANNEL_ID = "budget_soft"
        private const val NOTIFICATION_BASE = 1_000_000_000
        private const val OVERALL_NOTIFICATION_ID = 999_999_999
    }
}
