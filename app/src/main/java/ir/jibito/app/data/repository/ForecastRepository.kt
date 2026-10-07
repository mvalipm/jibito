package ir.jibito.app.data.repository

import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.wallet.BalanceForecast
import ir.jibito.app.data.wallet.BalanceForecastCalc
import ir.jibito.app.data.wallet.BalanceHistory
import ir.jibito.app.data.wallet.DayGrid
import ir.jibito.app.data.wallet.PlannedPayment
import ir.jibito.app.data.wallet.Salary
import ir.jibito.app.data.wallet.SalaryDetector
import ir.jibito.app.data.wallet.SalarySettings
import ir.jibito.app.util.JalaliMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn

/**
 * @param forecast null یعنی پیش‌بینی ممکن نیست (موجودی امروز معلوم نیست یا هنوز پایه‌ای برای الگو نیست)
 * @param suggestion «این واریز حقوقته؟»؛ فقط تا وقتی حقوقی تأیید نشده
 */
data class ForecastState(val forecast: BalanceForecast?, val suggestion: Salary?)

/** «پولم تا حقوق بعدی می‌رسه؟» (BalanceForecastCalc) و پیشنهاد حقوق (SalaryDetector)، برای «خلاصه»، «گزارش‌ها» و «کارها» */
class ForecastRepository(
    db: AppDatabase,
    balance: BalanceRepository,
    recurring: RecurringRepository,
    private val salarySettings: SalarySettings,
    appScope: CoroutineScope,
) {
    /** یک بار حساب می‌شود و بین صفحه‌ها مشترک است */
    val state: Flow<ForecastState> = combine(
        balance.observeSource(),
        // شش ماه برای تشخیص حقوق (پیش‌بینی فقط ماه قبل و همین ماه را لازم دارد)
        db.accountDao().observeFlowsSince(JalaliMonth.current().plus(-SalaryDetector.MIN_MONTHS * 2).startMillis()),
        recurring.observeAll(),
        salarySettings.choice,
    ) { source, rows, planned, choice ->
        val now = System.currentTimeMillis()
        val detected = SalaryDetector.detect(rows, emptySet(), now)
        // حقوق تأییدشده با عددهای تازه‌اش (اگر هنوز تشخیص داده می‌شود؛ مثلاً حقوق زیاد شده)
        val salary = choice.confirmed?.let { c -> detected.firstOrNull { it.key == c.key } ?: c }
        val suggestion = if (salary == null) detected.firstOrNull { it.key !in choice.dismissed } else null
        val overview = BalanceHistory.overview(source.points, source.links, source.excluded, DayGrid.lastDays(1, now))
        val forecast = overview.total.values.lastOrNull()?.let { today ->
            BalanceForecastCalc.compute(
                todayRial = today,
                rows = BalanceForecastCalc.walletRows(rows, overview, source.links),
                planned = planned.map { PlannedPayment(it.title, it.amountRial, it.dayOfMonth) },
                salary = salary,
                nowMillis = now,
            )
        }
        ForecastState(forecast, suggestion)
    }
        .flowOn(Dispatchers.Default)
        .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** «آره، حقوقمه» */
    fun confirmSalary(salary: Salary) = salarySettings.confirm(salary)

    /** «نه» یا «این حقوقم نیست» */
    fun rejectSalary(salary: Salary) = salarySettings.dismiss(salary.key)
}
