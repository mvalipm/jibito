package ir.jibito.app.data.repository

import androidx.room.withTransaction
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.category.CategoryLearning
import ir.jibito.app.data.category.ReplyCategoryMatcher
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.data.sms.SyncState
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.CategoryTree
import ir.jibito.app.domain.OneOffDetector
import ir.jibito.app.domain.Transaction
import ir.jibito.app.domain.TransferSuggestion
import ir.jibito.app.domain.BankBalance
import ir.jibito.app.data.local.entity.OwnAccountEntity
import ir.jibito.app.data.linking.PurchaseLinker
import ir.jibito.app.data.parser.AccountExtractor
import ir.jibito.app.data.parser.SmsTextNormalizer
import ir.jibito.app.data.transfer.TransferCandidate
import ir.jibito.app.data.transfer.TransferMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import ir.jibito.app.data.category.CreateCategoryResult
import ir.jibito.app.data.category.CustomCategories
import ir.jibito.app.data.category.CategoryPalette
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.category.SpendRollup
import ir.jibito.app.data.wallet.AccountGrouping
import ir.jibito.app.data.local.entity.toLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TransactionRepositoryImpl(
    private val db: AppDatabase,
    smsReader: SmsReader,
    syncState: SyncState,
    /** بعد از تعیین دسته صدا زده می‌شود (برای بررسی هشدار بودجه) */
    private val onCategoryChanged: suspend () -> Unit = {},
    /** بعد از هر همگام‌سازی پیامک‌ها (مثلاً برای به‌روز کردن ویجت) */
    private val onSynced: suspend () -> Unit = {},
    /** دامنه‌ی کل اپ؛ برای این‌که فهرست تراکنش‌ها یک بار ساخته و بین همه‌ی صفحه‌ها مشترک شود */
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : TransactionRepository {

    private val dao = db.transactionFlowDao()
    private val smsSync = SmsSync(db, smsReader, syncState)
    private val syncMutex = Mutex()
    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    override fun observeTransactions(): Flow<List<Transaction>> = sharedTransactions

    override fun observeTransferSuggestions(): Flow<List<TransferSuggestion>> = sharedTransferSuggestions

    override fun observeOneOffSuggestions(): Flow<List<Transaction>> =
        combine(sharedTransactions, observeCategories()) { all, categories ->
            OneOffDetector.find(all, CategoryTree.nonSpendIds(categories), System.currentTimeMillis())
        }.flowOn(Dispatchers.Default)

    /**
     * فهرست تراکنش‌ها: یک بار برای همه‌ی صفحه‌ها (نه یک بار برای هر صفحه) و بیرون از رشته‌ی اصلی (UI)،
     * تا با هزاران تراکنش هم صفحه گیر نکند. ۵ ثانیه بعد از رفتن آخرین صفحه، خاموش می‌شود.
     */
    private val sharedTransactions: Flow<List<Transaction>> by lazy {
        buildTransactions()
            .flowOn(Dispatchers.Default)
            .shareIn(appScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MILLIS), replay = 1)
    }

    private val sharedTransferSuggestions: Flow<List<TransferSuggestion>> by lazy {
        buildTransferSuggestions(sharedTransactions)
            .flowOn(Dispatchers.Default)
            .shareIn(appScope, SharingStarted.WhileSubscribed(SHARE_TIMEOUT_MILLIS), replay = 1)
    }

    private fun buildTransactions(): Flow<List<Transaction>> =
        combine(dao.observeAll(), db.categoryDao().observeAll()) { rows, categories ->
            val byId = categories.associateBy { it.id }
            rows.map { row ->
                val f = row.flow
                Transaction(
                    id = f.id,
                    bank = BankDirectory.byId(f.bankId),
                    body = f.smsContent.orEmpty(),
                    dateMillis = f.dateEpoch,
                    transaction = ParsedTransaction(
                        type = if (f.flowType == FlowType.DEPOSIT.code) FlowType.DEPOSIT else FlowType.WITHDRAWAL,
                        amountRial = f.amount,
                        balanceRial = f.remainAfter,
                    ),
                    merchant = f.merchant,
                    suggestedCategory = f.suggestedCategory,
                    isFailedPurchase = f.isFailedPurchase,
                    categoryId = f.categoryId,
                    categoryName = row.categoryName,
                    // زیردسته‌ها آیکون ندارند ← آیکون دسته‌ی اصلی
                    categoryIcon = row.categoryIcon
                        ?: row.flow.categoryId?.let { id -> byId[id]?.let { SpendRollup.rootOf(it, byId).icon } },
                    isAutoCategorized = f.isAutoCategorized,
                    isSelfTransfer = f.transferState == TransactionFlowEntity.TRANSFER_SELF,
                    isTransferRejected = f.transferState == TransactionFlowEntity.TRANSFER_REJECTED,
                    isManual = f.source == SOURCE_MANUAL,
                    feeRial = f.description?.takeIf { it.startsWith(FEE_PREFIX) }?.removePrefix(FEE_PREFIX)?.toLongOrNull(),
                    smsId = f.smsId,
                    note = f.note,
                    isOneOff = f.oneOffState == TransactionFlowEntity.ONE_OFF_YES,
                    isOneOffRejected = f.oneOffState == TransactionFlowEntity.ONE_OFF_REJECTED,
                )
            }
        }

    private fun buildTransferSuggestions(transactions: Flow<List<Transaction>>): Flow<List<TransferSuggestion>> =
        transactions.map { all ->
            val rows = all.filter { !it.isFailedPurchase && !it.isSelfTransfer && !it.isTransferRejected }
            val byId = rows.associateBy { it.id }
            TransferMatcher.findPairs(
                rows.map {
                    val text = SmsTextNormalizer.normalize(it.body)
                    TransferCandidate(
                        id = it.id,
                        isWithdrawal = it.transaction.type == FlowType.WITHDRAWAL,
                        amountRial = it.transaction.amountRial,
                        dateMillis = it.dateMillis,
                        bankId = it.bank?.id,
                        balanceRial = it.transaction.balanceRial,
                        isInterbank = TransferMatcher.isInterbankText(text),
                        isCorrection = PurchaseLinker.isCorrectionText(text),
                        account = AccountExtractor.find(text),
                    )
                }
            ).mapNotNull { p ->
                val w = byId[p.withdrawalId] ?: return@mapNotNull null
                val d = byId[p.depositId] ?: return@mapNotNull null
                TransferSuggestion(w, d)
            }.sortedByDescending { it.withdrawal.dateMillis }
        }

    override suspend fun confirmTransfer(suggestion: TransferSuggestion) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.setTransfer(suggestion.withdrawal.id, TransactionFlowEntity.TRANSFER_SELF, suggestion.deposit.id, now)
            dao.setTransfer(suggestion.deposit.id, TransactionFlowEntity.TRANSFER_SELF, suggestion.withdrawal.id, now)
            suggestion.withdrawal.merchant?.let { learnOwnAccount(it, now) }
        }
        onCategoryChanged()
    }

    override suspend fun rejectTransfer(suggestion: TransferSuggestion) {
        dao.setTransfer(suggestion.withdrawal.id, TransactionFlowEntity.TRANSFER_REJECTED, null, System.currentTimeMillis())
    }

    override suspend fun setOneOff(transactionId: Long, isOneOff: Boolean) {
        val state = if (isOneOff) TransactionFlowEntity.ONE_OFF_YES else TransactionFlowEntity.ONE_OFF_REJECTED
        dao.setOneOff(transactionId, state, System.currentTimeMillis())
        onCategoryChanged()
    }

    override suspend fun setSelfTransfer(transactionId: Long, isSelfTransfer: Boolean) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val row = dao.byId(transactionId) ?: return@withTransaction
            val isWithdrawal = row.flowType == FlowType.WITHDRAWAL.code
            if (isSelfTransfer) {
                dao.setTransfer(row.id, TransactionFlowEntity.TRANSFER_SELF, row.transferPairId, now)
                if (isWithdrawal) row.merchant?.let { learnOwnAccount(it, now) }
            } else {
                // «انتقال به خودم نیست» ← دوباره خرج/درآمد حساب می‌شود و دیگر پیشنهاد نمی‌شود
                dao.setTransfer(row.id, TransactionFlowEntity.TRANSFER_REJECTED, null, now)
                row.transferPairId?.let { dao.setTransfer(it, TransactionFlowEntity.TRANSFER_REJECTED, null, now) }
                // اگر این مقصد قبلاً «کارت خودم» یاد گرفته شده بود، فراموش شود
                if (isWithdrawal) row.merchant?.let { dao.deleteOwnAccount(it) }
            }
        }
        onCategoryChanged()
    }

    override suspend fun createCategory(
        name: String,
        parentId: Long?,
        flowType: Int,
        icon: String?,
    ): CreateCategoryResult = db.withTransaction {
        val categoryDao = db.categoryDao()
        val all = categoryDao.all()
        val sameType = all.filter { it.flowType == flowType && !it.isArchived }
        CustomCategories.validate(name, sameType.map { it.name })?.let {
            return@withTransaction CreateCategoryResult.Invalid(it)
        }
        val parent = parentId?.let { id -> all.firstOrNull { it.id == id } }
        val customRoots = all.count { it.isCustom && it.parentId == null }
        val id = categoryDao.insert(
            CategoryEntity(
                name = CustomCategories.clean(name),
                icon = if (parent == null) icon ?: CustomCategories.ICONS.first() else icon,
                colorHex = if (parent == null) CategoryPalette.forCustom(customRoots) else null,
                flowType = flowType,
                parentId = parent?.id,
                // بعد از دسته‌های پیش‌فرض، به ترتیب ساخته شدن
                sortOrder = 5_000 + all.size,
                isCustom = true,
                // زیر «پس‌انداز و قرض» هم خرج حساب نمی‌شود
                countsAsSpend = parent?.countsAsSpend ?: true,
            )
        )
        CreateCategoryResult.Created(id)
    }

    override suspend fun addManual(
        type: FlowType,
        amountRial: Long,
        categoryId: Long?,
        note: String?,
        dateMillis: Long,
    ): Long {
        val now = System.currentTimeMillis()
        val merchant = note?.trim()?.takeIf { it.isNotEmpty() }
        val id = dao.insert(
            TransactionFlowEntity(
                smsId = null,
                bankId = null,
                flowType = type.code,
                amount = amountRial,
                remainAfter = null,
                dateEpoch = dateMillis,
                merchant = merchant,
                suggestedCategory = null,
                isFailedPurchase = false,
                categoryId = categoryId,
                description = null,
                smsContent = null,
                source = SOURCE_MANUAL,
                updatedAt = now,
                // خود کاربر همین الان ثبتش کرده؛ نوتیفیکیشن «مال چی بود؟» لازم نیست
                notifiedAt = now,
                isAutoCategorized = false,
                // دسته‌ای که موقع ثبت دستی انتخاب شده، انتخاب خود کاربر است (برای یادگیری)
                categorizedAt = if (categoryId != null) now else null,
            )
        )
        onCategoryChanged()
        return id
    }

    override suspend fun snapshotForUndo(transactionId: Long): UndoSnapshot {
        val row = dao.byId(transactionId) ?: return UndoSnapshot(emptyList())
        val rows = buildList {
            add(row)
            row.transferPairId?.let { dao.byId(it) }?.let { add(it) }
            row.merchant?.let { addAll(dao.uncategorizedSameMerchant(it, row.flowType)) }
        }.distinctBy { it.id }
        return UndoSnapshot(rows)
    }

    override suspend fun restore(snapshot: UndoSnapshot) {
        if (snapshot.rows.isEmpty()) return
        val now = System.currentTimeMillis()
        db.withTransaction {
            snapshot.rows.forEach {
                dao.restoreUserState(
                    it.id, it.categoryId, it.isAutoCategorized, it.suggestedCategory,
                    it.transferState, it.transferPairId, it.isDeleted, it.categorizedAt, now,
                )
            }
        }
        onCategoryChanged()
    }

    override suspend fun deleteManual(transactionId: Long) {
        val row = dao.byId(transactionId) ?: return
        if (row.source != SOURCE_MANUAL) return
        dao.softDelete(listOf(row.id), System.currentTimeMillis())
        onCategoryChanged()
    }

    override suspend fun frequentCategoryIds(flowType: Int, limit: Int): List<Long> =
        dao.frequentCategoryIds(flowType, limit)

    /** مانده‌ی هر حساب (چند حساب در یک بانک جدا، طبق تصمیم‌های کاربر: AccountGrouping) */
    override fun observeBankBalances(): Flow<List<BankBalance>> {
        val accountDao = db.accountDao()
        return combine(accountDao.observeLatestBalances(), accountDao.observeLinks()) { rows, links ->
            AccountGrouping.balances(rows, links.map { it.toLink() }).mapNotNull { b ->
                BankDirectory.byId(b.bankId)?.let { BankBalance(it, b.balanceRial, b.dateMillis, b.account, b.name) }
            }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun deleteCustomCategory(categoryId: Long) {
        db.withTransaction {
            val categoryDao = db.categoryDao()
            val all = categoryDao.all()
            val target = all.firstOrNull { it.id == categoryId } ?: return@withTransaction
            if (!target.isCustom) return@withTransaction
            // خودش + همه‌ی زیردسته‌هایش
            val ids = mutableListOf(target.id)
            var i = 0
            while (i < ids.size) {
                ids += all.filter { it.parentId == ids[i] && !it.isArchived }.map { it.id }
                i++
            }
            dao.reassign(ids, target.parentId)
            for (id in ids) {
                db.summaryDao().deleteBudget(id)
                categoryDao.archive(id)
            }
        }
        onCategoryChanged()
    }

    override suspend fun renameCustomCategory(categoryId: Long, name: String): CreateCategoryResult =
        db.withTransaction {
            val categoryDao = db.categoryDao()
            val all = categoryDao.all()
            val target = all.firstOrNull { it.id == categoryId && it.isCustom && !it.isArchived }
                ?: return@withTransaction CreateCategoryResult.Created(categoryId)
            val others = all.filter { it.flowType == target.flowType && !it.isArchived && it.id != target.id }
            CustomCategories.validate(name, others.map { it.name })?.let {
                return@withTransaction CreateCategoryResult.Invalid(it)
            }
            categoryDao.rename(target.id, CustomCategories.clean(name))
            CreateCategoryResult.Created(target.id)
        }

    /** کارت/حساب مقصد ← «مال خودم»؛ برداشت‌های عادیِ دیگر به همین مقصد هم انتقال به خودم می‌شوند */
    private suspend fun learnOwnAccount(merchant: String, now: Long) {
        dao.insertOwnAccount(OwnAccountEntity(merchant, now))
        dao.markSelfTransferByMerchant(merchant, now)
    }


    override fun observeCategories(): Flow<List<Category>> =
        db.categoryDao().observeActive().map { rows ->
            rows.map {
                Category(
                    id = it.id,
                    name = it.name,
                    icon = it.icon,
                    colorHex = it.colorHex,
                    flowType = it.flowType,
                    parentId = it.parentId,
                    countsAsSpend = it.countsAsSpend,
                    isCustom = it.isCustom,
                )
            }
        }

    /** اگر هم‌زمان دو جا (مثلاً اپ و کار پس‌زمینه) بخواهند همگام کنند، دومی منتظر اولی می‌ماند. */
    override suspend fun syncFromSms(forceFull: Boolean): Int = syncMutex.withLock {
        _isSyncing.value = true
        try {
            smsSync.run(forceFull).also { onSynced() }
        } finally {
            _isSyncing.value = false
        }
    }

    override suspend fun setCategory(transactionId: Long, categoryId: Long?) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.setCategory(transactionId, categoryId, now)
            // یادگیری: بقیه‌ی تراکنش‌های بی‌دسته‌ی همین طرف حساب، بسته به تعداد تأییدها،
            // یا خودکار همین دسته را می‌گیرند (۳ تأیید پشت سر هم) یا فقط پیشنهادش را
            val row = dao.byId(transactionId)
            if (row != null && categoryId != null && row.merchant != null) {
                val learning = CategoryLearning(db)
                val decision = learning.decide(row.merchant, row.flowType)
                if (decision != null) {
                    if (decision.auto) {
                        dao.applyToSameMerchant(row.merchant, row.flowType, decision.categoryId, now)
                    } else {
                        learning.nameOf(decision.categoryId)?.let {
                            dao.suggestForSameMerchant(row.merchant, row.flowType, it, now)
                        }
                    }
                }
            }
        }
        onCategoryChanged()
    }

    override suspend fun setNote(transactionId: Long, note: String?) {
        dao.setNote(transactionId, TransactionNotes.clean(note), System.currentTimeMillis())
    }

    override suspend fun applyNoteReply(transactionId: Long, text: String, maxDepth: Int): NoteReply? {
        val note = TransactionNotes.clean(text) ?: return null
        val row = dao.byId(transactionId) ?: return null
        dao.setNote(transactionId, note, System.currentTimeMillis())
        val candidates = db.categoryDao().all()
            .filter { !it.isArchived && it.flowType == row.flowType }
            .map { ReplyCategoryMatcher.Candidate(it.id, it.name, it.parentId) }
        val matchedId = ReplyCategoryMatcher.match(note, candidates) ?: return NoteReply(note, null, null)
        val liftedId = CategoryTree.idAtDepth(matchedId, candidates.associate { it.id to it.parentId }, maxDepth)
        val match = candidates.first { it.id == liftedId }
        setCategory(transactionId, match.id)
        return NoteReply(note, match.id, match.name)
    }

    private companion object {
        const val SHARE_TIMEOUT_MILLIS = 5_000L
    }
}
