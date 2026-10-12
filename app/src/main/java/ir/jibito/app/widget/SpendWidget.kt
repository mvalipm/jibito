package ir.jibito.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import ir.jibito.app.JibitoApplication
import ir.jibito.app.data.category.SpendRollup
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.util.ErrorLog
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ویجت «خرج امروز»: خرج امروز در برابر یک روز معمولی، نمودار هفت روز، و خرج یا بودجه‌ی ماه.
 * هر اندازه چیدمان خودش را دارد (WidgetSize، ظاهر: SpendViews). زمینه همراه حالت روشن/تیره‌ی گوشی است
 * و حال بودجه (آرام / نزدیک سقف / رد شده) فقط برچسب و رنگ نوار ماه را عوض می‌کند.
 * بعد از هر همگام‌سازی و هر تغییر دسته به‌روز می‌شود (و هر ۳۰ دقیقه، توسط خود اندروید).
 * اگر قفل اپ روشن باشد، مبلغ‌ها روی صفحه‌ی اصلی نشان داده نمی‌شوند.
 */
class SpendWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                render(context, manager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    /** پیش از اندروید ۱۲ چیدمان با اندازه عوض نمی‌شود، مگر خودمان دوباره بسازیم */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        onUpdate(context, manager, intArrayOf(appWidgetId))
    }

    /** اعداد ویجت (به ریال) */
    data class Numbers(
        val todayRial: Long,
        val monthRial: Long,
        val overallBudgetRial: Long?,
        /** چه مقدار از monthRial خرج یک‌باره است (از بودجه کم نمی‌شود) */
        val monthOneOffRial: Long = 0,
        /** خرج هر روز از شش روز پیش تا امروز (قدیمی‌ترین اول)؛ خالی یعنی نمودار نه */
        val weekRial: List<Long> = emptyList(),
        /** شروع همان روزها (برای حرف اول اسم روز زیر هر ستون) */
        val weekStarts: List<Long> = emptyList(),
        /** «یک روز معمولی»؛ null یعنی هنوز سابقه‌ی کافی نیست */
        val typicalRial: Long? = null,
        /** آخرین خرج‌ها (تازه‌ترین اول) برای اندازه‌ی بزرگ */
        val recent: List<Recent> = emptyList(),
    ) {
        /** خرجی که از بودجه‌ی کل کم می‌شود */
        val budgetSpentRial: Long get() = monthRial - monthOneOffRial
    }

    /** یک ردیف «آخرین خرج‌ها»: طرف‌حساب یا اسم دسته (null ← «خرج») و رنگ دسته‌ی اصلی */
    data class Recent(val id: Long, val label: String?, val amountRial: Long, val dateMillis: Long, val colorHex: String?)

    companion object {

        /** همه‌ی ویجت‌های روی صفحه را به‌روز می‌کند؛ اگر ویجتی نیست، کاری نمی‌کند. */
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, SpendWidget::class.java))
            if (ids.isEmpty()) return
            render(context, manager, ids)
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            try {
                val container = (context.applicationContext as JibitoApplication).container
                val locked = container.appLockSettings.enabled.value
                val now = System.currentTimeMillis()
                val n = if (locked) null else numbers(container.database, now)
                WidgetKit.deliver(context, manager, ids) { size -> SpendViews.build(context, n, now, size) }
            } catch (e: Exception) {
                ErrorLog.record(context, "widget", e)
            }
        }

        /** حال جیب، مثل بالای صفحه‌ی خلاصه */
        internal enum class Mood { NONE, CALM, WARN, OVER }

        /** کمتر از این فاصله بین «خرج» و «زمان» یعنی «طبق برنامه» (مثل صفحه‌ی خلاصه) */
        private const val ON_TRACK_MARGIN = 0.05

        /** حال فقط از بودجه، مثل سرصفحه‌ی خلاصه (موجودی حساب اثری ندارد) */
        internal fun moodOf(n: Numbers, now: Long): Mood {
            val budget = n.overallBudgetRial?.takeIf { it > 0 } ?: return Mood.NONE
            val month = JalaliMonth.of(now)
            val time = (now - month.startMillis()).toDouble() / (month.endMillis() - month.startMillis())
            val spent = n.budgetSpentRial.toDouble() / budget
            return when {
                n.budgetSpentRial >= budget -> Mood.OVER
                spent >= 0.8 || spent - time >= ON_TRACK_MARGIN -> Mood.WARN
                else -> Mood.CALM
            }
        }

        /** چند ردیف «آخرین خرج‌ها» */
        private const val RECENT = 3

        /** خرج امروز و این ماه با همان قانون‌های صفحه‌ی خلاصه (انتقال به خودم و دسته‌های «خرج نیست» حساب نمی‌شوند) */
        suspend fun numbers(db: AppDatabase, now: Long): Numbers {
            val dao = db.summaryDao()
            val withdrawal = FlowType.WITHDRAWAL.code
            val categories = db.categoryDao().all().filter { it.flowType == withdrawal }
            val todayStart = Jalali.startOfDay(now)
            val todayEnd = SpendDays.nextDay(todayStart)
            val month = JalaliMonth.of(now)
            val today = SpendRollup.rollup(categories, dao.sums(withdrawal, todayStart, todayEnd)).total
            val monthSpend = SpendRollup.rollup(categories, dao.sums(withdrawal, month.startMillis(), month.endMillis()))

            // سی روز پیش از امروز (برای «روز معمولی») و خود امروز
            val days = SpendDays.dayStarts(todayStart, SpendDays.TYPICAL_DAYS + 1)
            val rows = SpendRollup.spendsOnly(dao.amounts(withdrawal, days.first(), todayEnd), categories)
            val week = days.takeLast(SpendDays.WEEK)

            val byId = categories.associateBy { it.id }
            val excluded = SpendRollup.excludedIds(categories)
            val recent = dao.latest(withdrawal, RECENT * 4)
                .filter { it.categoryId == null || it.categoryId !in excluded }
                .take(RECENT)
                .map { r ->
                    val cat = r.categoryId?.let { byId[it] }
                    val color = cat?.let { SpendRollup.rootOf(it, byId).colorHex ?: it.colorHex }
                    Recent(r.id, r.merchant?.takeIf { it.isNotBlank() } ?: cat?.name, r.amount, r.dateEpoch, color)
                }

            return Numbers(
                todayRial = today,
                monthRial = monthSpend.total,
                overallBudgetRial = dao.overallBudget()?.monthlyLimitRial,
                monthOneOffRial = monthSpend.oneOffTotal,
                weekRial = SpendDays.perDay(rows, week, todayEnd),
                weekStarts = week,
                typicalRial = SpendDays.typical(rows, days.dropLast(1), todayStart, dao.firstDate(withdrawal)),
                recent = recent,
            )
        }
    }
}
