package ir.jibito.app.data.repository

import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.toLink
import ir.jibito.app.data.wallet.AccountLink
import ir.jibito.app.data.wallet.AccountRef
import ir.jibito.app.data.wallet.BalanceHistory
import ir.jibito.app.data.wallet.BalancePointRow
import ir.jibito.app.data.wallet.WalletSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** همه‌ی چیزی که «روند موجودی» لازم دارد: پیامک‌های مانده‌دار، تصمیم‌های «جدا یا یکی؟» و حساب‌های بیرون از جمع */
data class BalanceSource(
    val points: List<BalancePointRow>,
    val links: List<AccountLink>,
    val excluded: Set<String>,
)

/** «روند موجودی» از روی مانده‌ی پیامک‌ها (BalanceHistory) */
class BalanceRepository(private val db: AppDatabase, private val walletSettings: WalletSettings) {

    private val accountDao = db.accountDao()

    fun observeSource(): Flow<BalanceSource> =
        combine(accountDao.observeBalancePoints(), accountDao.observeLinks(), walletSettings.excluded) { points, links, excluded ->
            BalanceSource(points, links.map { it.toLink() }, excluded)
        }.flowOn(Dispatchers.Default)

    /** شناسه‌ی تراکنش‌های حساب [ref] (برای فهرست تراکنش‌های صفحه‌ی جزئیات) */
    fun observeFlowIds(ref: AccountRef): Flow<Set<Long>> =
        combine(accountDao.observeBankFlowAccounts(ref.bankId), accountDao.observeLinks()) { rows, links ->
            BalanceHistory.flowIdsOf(ref, rows, links.map { it.toLink() })
        }.flowOn(Dispatchers.Default)
}
