package ir.jibito.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.jibito.app.data.repository.BalanceRepository
import ir.jibito.app.data.repository.TransactionRepository
import ir.jibito.app.data.wallet.AccountRef
import ir.jibito.app.data.wallet.AccountTrend
import ir.jibito.app.data.wallet.BalanceHistory
import ir.jibito.app.data.wallet.BalanceSeries
import ir.jibito.app.data.wallet.DayGrid
import ir.jibito.app.data.wallet.DepositRhythm
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** بازه‌های صفحه‌ی جزئیات حساب */
enum class AccountRange(val days: Int) {
    MONTH(30),
    QUARTER(90),
    YEAR(365),
}

/** داده‌ی صفحه‌ی جزئیات یک حساب (یا همه‌ی حساب‌ها) در بازه‌ی انتخاب‌شده */
data class AccountDetail(
    /** روند خود صفحه: همان حساب، یا جمع حساب‌هایی که در کیف پول حساب می‌شوند */
    val series: BalanceSeries,
    /** همان حساب؛ null برای «همه‌ی حساب‌ها» یا حسابی که دیگر پیامک مانده‌دار ندارد */
    val account: AccountTrend?,
    /** «همه‌ی حساب‌ها»: فهرست حساب‌ها؛ برای یک حساب خالی */
    val accounts: List<AccountTrend>,
    /** «بعد از واریز بزرگ چه شد؟» (فقط برای یک حساب) */
    val rhythm: DepositRhythm?,
)

/** تراکنش‌های همان حساب در بازه، تازه‌ترین اول (با دسته‌ها برای رنگ ردیف‌ها) */
data class AccountTransactions(val items: List<Transaction>, val categories: List<Category>)

class AccountViewModel(
    /** null یعنی «همه‌ی حساب‌ها» */
    val ref: AccountRef?,
    balance: BalanceRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {

    private val _range = MutableStateFlow(AccountRange.MONTH)
    val range: StateFlow<AccountRange> = _range.asStateFlow()

    /** null یعنی «هنوز در حال بارگذاری» */
    val detail: StateFlow<AccountDetail?> = combine(balance.observeSource(), _range) { source, r ->
        val now = System.currentTimeMillis()
        val grid = DayGrid.lastDays(r.days, now)
        val overview = BalanceHistory.overview(source.points, source.links, source.excluded, grid)
        if (ref == null) {
            AccountDetail(overview.total, account = null, accounts = overview.accounts, rhythm = null)
        } else {
            val account = overview.accounts.firstOrNull { it.ref == ref }
            val points = BalanceHistory.byAccount(source.points, source.links)[ref].orEmpty()
            AccountDetail(
                series = account?.series ?: BalanceSeries(grid, List(grid.size) { null }),
                account = account,
                accounts = emptyList(),
                rhythm = BalanceHistory.depositRhythm(points, grid, now),
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** فقط برای یک حساب؛ «همه‌ی حساب‌ها» به‌جایش فهرست حساب‌ها را دارد */
    val transactions: StateFlow<AccountTransactions?> = if (ref == null) {
        MutableStateFlow(null)
    } else {
        combine(
            transactionRepository.observeTransactions(),
            transactionRepository.observeCategories(),
            balance.observeFlowIds(ref),
            _range,
        ) { all, categories, ids, r ->
            val from = DayGrid.lastDays(r.days, System.currentTimeMillis()).starts.first()
            AccountTransactions(
                items = all.filter { it.id in ids && it.dateMillis >= from }.sortedByDescending { it.dateMillis },
                categories = categories,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    fun setRange(range: AccountRange) {
        _range.value = range
    }

    companion object {
        fun factory(ref: AccountRef?, balance: BalanceRepository, transactions: TransactionRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { AccountViewModel(ref, balance, transactions) } }
    }
}
