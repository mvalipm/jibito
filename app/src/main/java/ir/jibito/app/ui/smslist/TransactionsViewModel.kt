package ir.jibito.app.ui.smslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.jibito.app.data.repository.TransactionRepository
import ir.jibito.app.data.repository.MerchantLesson
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.Transaction
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.category.CreateCategoryResult
import ir.jibito.app.domain.TransferSuggestion
import ir.jibito.app.domain.BankBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ir.jibito.app.data.repository.UndoSnapshot

class TransactionsViewModel(
    private val repository: TransactionRepository,
) : ViewModel() {

    /** null یعنی «هنوز چیزی از دیتابیس نیامده». */
    val transactions: StateFlow<List<Transaction>?> = repository.observeTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val onlyUncategorized = MutableStateFlow(false)

    private val _search = MutableStateFlow(TxSearch())
    /** جست‌وجوی فعلی؛ با رفتن به تب دیگر و برگشتن هم می‌ماند */
    val search: StateFlow<TxSearch> = _search

    fun setSearch(value: TxSearch) {
        _search.value = value
    }

    /** «فقط خرج‌های بی‌دسته» (از «کارهای لازم» در خلاصه) */
    fun setOnlyUncategorized(value: Boolean) {
        onlyUncategorized.value = value
    }

    /** فهرستی که صفحه نشان می‌دهد (فیلترشده و روزبه‌روز)؛ null تا وقتی تراکنش‌ها از دیتابیس نیامده‌اند */
    val visible: StateFlow<VisibleTransactions?> =
        combine(repository.observeTransactions(), categories, onlyUncategorized, _search) { all, cats, onlyUncat, search ->
            visibleTransactions(all, cats, onlyUncat, search)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** خواندن پیامک‌ها را خود صفحه‌ی اصلی موقع باز شدن اپ شروع می‌کند (MainScreen) */
    val isSyncing: StateFlow<Boolean> = repository.isSyncing

    /** آخرین مانده‌ی هر بانک */
    val bankBalances: StateFlow<List<BankBalance>> = repository.observeBankBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** پیشنهادهای «انتقال بین حساب‌های خودم» */
    val transferSuggestions: StateFlow<List<TransferSuggestion>> = repository.observeTransferSuggestions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * دسته‌ی یک تراکنش را عوض می‌کند (null = بدون دسته). فهرست خودش از دیتابیس به‌روز می‌شود.
     * اگر تراکنش «انتقال به خودم» بود، با انتخاب دسته دیگر انتقال حساب نمی‌شود.
     */
    fun setCategory(transaction: Transaction, categoryId: Long?, onDone: (UndoSnapshot) -> Unit = {}) {
        viewModelScope.launch {
            val before = repository.snapshotForUndo(transaction.id)
            if (transaction.isSelfTransfer) repository.setSelfTransfer(transaction.id, false)
            repository.setCategory(transaction.id, categoryId)
            onDone(before)
        }
    }

    /** «برگردان» آخرین تغییر (دسته یا حذف) */
    fun undo(snapshot: UndoSnapshot) {
        viewModelScope.launch { repository.restore(snapshot) }
    }

    /** دسته‌ی شخصی می‌سازد و همان لحظه برای این تراکنش انتخابش می‌کند */
    fun createCategoryAndPick(
        transaction: Transaction,
        name: String,
        parentId: Long?,
        icon: String?,
        nature: Int,
        onResult: (CreateCategoryResult) -> Unit,
    ) {
        viewModelScope.launch {
            val result = repository.createCategory(name, parentId, transaction.transaction.type.code, icon, nature)
            if (result is CreateCategoryResult.Created) {
                if (transaction.isSelfTransfer) repository.setSelfTransfer(transaction.id, false)
                repository.setCategory(transaction.id, result.id)
            }
            onResult(result)
        }
    }

    /** ثبت دستی؛ بعد از ذخیره، شناسه‌ی تراکنش تازه به onSaved داده می‌شود */
    fun addManual(
        type: FlowType,
        amountRial: Long,
        categoryId: Long?,
        note: String?,
        dateMillis: Long,
        onSaved: (Long) -> Unit,
    ) {
        viewModelScope.launch { onSaved(repository.addManual(type, amountRial, categoryId, note, dateMillis)) }
    }

    fun deleteManual(transactionId: Long, onDone: (UndoSnapshot) -> Unit = {}) {
        viewModelScope.launch {
            val before = repository.snapshotForUndo(transactionId)
            repository.deleteManual(transactionId)
            onDone(before)
        }
    }

    fun loadQuickCategories(flowType: Int, onResult: (List<Long>) -> Unit) {
        viewModelScope.launch { onResult(repository.frequentCategoryIds(flowType, 6)) }
    }

    fun setNote(transactionId: Long, note: String) {
        viewModelScope.launch { repository.setNote(transactionId, note) }
    }

    /** «اسم فروشگاه کدومه؟»؛ [onDone] با تعداد تراکنش‌های دیگری که با همین درس اسم گرفتند */
    fun teachMerchant(transactionId: Long, merchant: String, lesson: MerchantLesson?, onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repository.teachMerchant(transactionId, merchant, lesson)) }
    }

    fun setSelfTransfer(transactionId: Long, isSelfTransfer: Boolean) {
        viewModelScope.launch { repository.setSelfTransfer(transactionId, isSelfTransfer) }
    }

    fun setOneOff(transactionId: Long, isOneOff: Boolean) {
        viewModelScope.launch { repository.setOneOff(transactionId, isOneOff) }
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
