package ir.jibito.app.ui.smslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.jibito.app.data.repository.TransactionRepository
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionsViewModel(
    private val repository: TransactionRepository,
) : ViewModel() {

    /** null یعنی «هنوز چیزی از دیتابیس نیامده». */
    val transactions: StateFlow<List<Transaction>?> = repository.observeTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        sync()
    }

    /** پیامک‌ها را دوباره می‌خواند؛ فهرست خودش از دیتابیس به‌روز می‌شود. */
    fun sync() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                repository.syncFromSms()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /** دسته‌ی یک تراکنش را عوض می‌کند (null = بدون دسته). فهرست خودش از دیتابیس به‌روز می‌شود. */
    fun setCategory(transactionId: Long, categoryId: Long?) {
        viewModelScope.launch { repository.setCategory(transactionId, categoryId) }
    }

    companion object {
        fun factory(repository: TransactionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { TransactionsViewModel(repository) }
        }
    }
}
