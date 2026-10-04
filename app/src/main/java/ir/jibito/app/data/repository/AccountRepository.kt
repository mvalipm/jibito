package ir.jibito.app.data.repository

import androidx.room.withTransaction
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.AccountLinkEntity
import ir.jibito.app.data.local.entity.toEntity
import ir.jibito.app.data.local.entity.toLink
import ir.jibito.app.data.wallet.AccountGroup
import ir.jibito.app.data.wallet.AccountGrouping
import ir.jibito.app.data.wallet.AccountLink
import ir.jibito.app.data.wallet.AccountQuestion
import ir.jibito.app.data.wallet.KnownAccountRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** حالت قانون‌های حساب پیش از یک تغییر، برای «برگردان» (null = آن ردیف نبود) */
class AccountUndo internal constructor(internal val previous: List<Pair<Pair<Int, String>, AccountLinkEntity?>>)

/**
 * چند حساب در یک بانک: سؤال «جدا هستند یا یکی؟»، صفحه‌ی «حساب‌های من»، و تصمیم‌های کاربر.
 * منطق در AccountGrouping است؛ این‌جا فقط خواندن و نوشتن قانون‌ها (هر تغییر با «برگردان»).
 */
class AccountRepository(private val db: AppDatabase) {

    private val dao = db.accountDao()

    /** سؤال بعدی، یا null */
    fun observeQuestion(): Flow<AccountQuestion?> =
        combine(dao.observeKnownAccounts(), dao.observeLinks()) { known, links ->
            AccountGrouping.question(known, links.map { it.toLink() })
        }.flowOn(Dispatchers.Default)

    /** همه‌ی حساب‌ها برای صفحه‌ی «حساب‌های من» */
    fun observeGroups(): Flow<List<AccountGroup>> =
        combine(dao.observeKnownAccounts(), dao.observeLatestBalances(), dao.observeLinks()) { known, rows, links ->
            AccountGrouping.groups(known, rows, links.map { it.toLink() })
        }.flowOn(Dispatchers.Default)

    suspend fun answer(question: AccountQuestion, same: Boolean): AccountUndo =
        change { links, _ -> AccountGrouping.answer(question, same, links) }

    suspend fun merge(bankId: Int, rep: String, intoRep: String): AccountUndo =
        change { links, known -> AccountGrouping.merge(bankId, rep, intoRep, known, links) }

    suspend fun split(bankId: Int, account: String): AccountUndo =
        change { links, known -> AccountGrouping.split(bankId, account, known, links) }

    suspend fun rename(bankId: Int, rep: String, name: String): AccountUndo =
        change { links, _ -> listOf(AccountGrouping.rename(bankId, rep, name, links)) }

    /** «برگردان»: همان ردیف‌ها به حالت قبل (ردیفی که قبلاً نبود، پاک می‌شود) */
    suspend fun undo(undo: AccountUndo) {
        db.withTransaction {
            for ((key, old) in undo.previous) {
                if (old == null) dao.delete(key.first, key.second) else dao.upsertAll(listOf(old))
            }
        }
    }

    private suspend fun change(
        compute: (List<AccountLink>, List<KnownAccountRow>) -> List<AccountLink>,
    ): AccountUndo = db.withTransaction {
        val current = dao.links()
        val byKey = current.associateBy { it.bankId to it.account }
        val known = dao.knownAccounts()
        val changes = compute(current.map { it.toLink() }, known)
        val now = System.currentTimeMillis()
        val previous = changes.map { (it.bankId to it.account) }.distinct().map { it to byKey[it] }
        if (changes.isNotEmpty()) dao.upsertAll(changes.map { it.toEntity(now) })
        AccountUndo(previous)
    }
}
