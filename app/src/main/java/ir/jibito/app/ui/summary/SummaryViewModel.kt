package ir.jibito.app.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.jibito.app.data.repository.BudgetRepository
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.util.JalaliMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ir.jibito.app.data.repository.SpendTrend

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModel(
    private val repository: BudgetRepository,
) : ViewModel() {

    private val _month = MutableStateFlow(JalaliMonth.current())
    val month: StateFlow<JalaliMonth> = _month.asStateFlow()

    /** null یعنی «هنوز در حال بارگذاری» */
    val summary: StateFlow<MonthSummary?> = _month
        .flatMapLatest { repository.observeMonth(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** روند ۶ ماه منتهی به ماه انتخاب‌شده؛ null یعنی «هنوز در حال بارگذاری» */
    val trend: StateFlow<SpendTrend?> = _month
        .flatMapLatest { repository.observeTrend(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() {
        _month.value = _month.value.plus(-1)
    }

    fun nextMonth() {
        // ماه‌های آینده خرجی ندارند
        if (_month.value != JalaliMonth.current()) _month.value = _month.value.plus(1)
    }

    fun setBudget(categoryId: Long, monthlyLimitRial: Long?) {
        viewModelScope.launch { repository.setBudget(categoryId, monthlyLimitRial) }
    }

    fun setOverallBudget(monthlyLimitRial: Long?) {
        viewModelScope.launch { repository.setOverallBudget(monthlyLimitRial) }
    }

    companion object {
        fun factory(repository: BudgetRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SummaryViewModel(repository) }
        }
    }
}
