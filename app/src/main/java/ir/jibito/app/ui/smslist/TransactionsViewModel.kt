package ir.jibito.app.ui.smslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.jibito.app.data.repository.TransactionRepository
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.Transaction
import ir.jibito.app.domain.TransferSuggestion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    /** خواندن پیامک‌ها را خود صفحه‌ی اصلی موقع باز شدن اپ شروع می‌کند (MainScreen) */
    val isSyncing: StateFlow<Boolean> = repository.isSyncing

    /** پیشنهادهای «انتقال بین حساب‌های خودم» */
    val transferSuggestions: StateFlow<List<TransferSuggestion>> = repository.observeTransferSuggestions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * دسته‌ی یک تراکنش را عوض می‌کند (null = بدون دسته). فهرست خودش از دیتابیس به‌روز می‌شود.
     * اگر تراکنش «انتقال به خودم» بود، با انتخاب دسته دیگر انتقال حساب نمی‌شود.
     */
    fun setCategory(transaction: Transaction, categoryId: Long?) {
        viewModelScope.launch {
            if (transaction.isSelfTransfer) repository.setSelfTransfer(transaction.id, false)
            repository.setCategory(transaction.id, categoryId)
        }
    }

    fun setSelfTransfer(transactionId: Long, isSelfTransfer: Boolean) {
        viewModelScope.launch { repository.setSelfTransfer(transactionId, isSelfTransfer) }
    }

    fun confirmTransfer(suggestion: TransferSuggestion) {
        viewModelScope.launch { repository.confirmTransfer(suggestion) }
    }

    fun rejectTransfer(suggestion: TransferSuggestion) {
        viewModelScope.launch { repository.rejectTransfer(suggestion) }
    }

    companion object {
        fun factory(repository: TransactionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { TransactionsViewModel(repository) }
        }
    }
}
