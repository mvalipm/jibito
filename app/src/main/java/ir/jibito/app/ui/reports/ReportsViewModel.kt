package ir.jibito.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.jibito.app.data.repository.BalanceRepository
import ir.jibito.app.data.repository.BudgetRepository
import ir.jibito.app.data.repository.MonthReport
import ir.jibito.app.data.repository.ReportRepository
import ir.jibito.app.data.repository.SpendTrend
import ir.jibito.app.data.wallet.BalanceHistory
import ir.jibito.app.data.wallet.BalanceOverview
import ir.jibito.app.data.wallet.DayGrid
import ir.jibito.app.util.JalaliMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** بازه‌ی نمودار اصلی «گزارش‌ها» */
enum class ReportRange(val months: Int) {
    /** خرج تجمعی همین ماه، روزبه‌روز */
    MONTH(1),
    HALF_YEAR(6),
    YEAR(12),
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    reports: ReportRepository,
    budgets: BudgetRepository,
    balance: BalanceRepository,
) : ViewModel() {

    val month: JalaliMonth = JalaliMonth.current()

    private val _range = MutableStateFlow(ReportRange.MONTH)
    val range: StateFlow<ReportRange> = _range.asStateFlow()

    /** null یعنی «هنوز در حال بارگذاری» */
    val report: StateFlow<MonthReport?> = reports.observeMonth(month)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** خرج ماه‌های تمام‌شده (ماه جاری نه، چون هنوز تمام نشده)؛ فقط برای بازه‌های چندماهه */
    val months: StateFlow<SpendTrend?> = _range
        .flatMapLatest { r -> if (r == ReportRange.MONTH) flowOf(null) else budgets.observeTrend(month.plus(-1), r.months) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * «روند موجودی» در همان بازه‌ی نمودار خرج، ولی تا امروز: این ماه، یا ۶/۱۲ ماه اخیر (با همین ماه).
     * null یعنی «هنوز در حال بارگذاری».
     */
    val balances: StateFlow<BalanceOverview?> = combine(balance.observeSource(), _range) { source, r ->
        val grid = DayGrid.of(month.plus(-(r.months - 1)).startMillis(), System.currentTimeMillis())
        BalanceHistory.overview(source.points, source.links, source.excluded, grid)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setRange(range: ReportRange) {
        _range.value = range
    }

    companion object {
        fun factory(reports: ReportRepository, budgets: BudgetRepository, balance: BalanceRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportsViewModel(reports, budgets, balance) }
        }
    }
}
