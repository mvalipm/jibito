package ir.jibito.app.data.repository

import androidx.room.withTransaction
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.category.CategoryLearning
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.category.Taxonomy
import ir.jibito.app.data.parser.EventKind
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.data.sms.SmsRowMatcher
import ir.jibito.app.data.sms.SyncState
import ir.jibito.app.data.sms.TransactionItem
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.Transaction
import ir.jibito.app.domain.TransferSuggestion
import ir.jibito.app.domain.BankBalance
import ir.jibito.app.data.local.entity.OwnAccountEntity
import ir.jibito.app.data.local.entity.SmsFlowKey
import ir.jibito.app.data.transfer.TransferCandidate
import ir.jibito.app.data.transfer.TransferMatcher
import ir.jibito.app.data.local.entity.ReviewSmsEntity
import ir.jibito.app.data.review.LearnedTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import ir.jibito.app.data.category.CategorySeeder
import ir.jibito.app.data.category.CreateCategoryResult
import ir.jibito.app.data.category.CustomCategories
import ir.jibito.app.data.category.CategoryPalette
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.category.SpendRollup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** منبع تراکنش: خودکار از پیامک، یا تأییدشده توسط کاربر از صندوق بررسی */
const val SOURCE_SMS_AUTO = "SMS_AUTO"
const val SOURCE_SMS_MANUAL = "SMS_MANUAL"
/** ثبت دستی (نقدی یا تراکنشی که پیامک ندارد) */
const val SOURCE_MANUAL = "MANUAL"
/** پیشوند ذخیره‌ی کارمزد انتقال در ستون description */
const val FEE_PREFIX = "fee:"

/**
 * تنها راه صفحه‌ها برای رسیدن به تراکنش‌ها (قانون سند معماری: ViewModel هرگز مستقیم Room نمی‌بیند).
 */
interface TransactionRepository {
    fun observeTransactions(): Flow<List<Transaction>>

    fun observeCategories(): Flow<List<Category>>

    /**
     * پیامک‌ها را می‌خواند و دیتابیس را به‌روز می‌کند. تعداد تراکنش‌های جدید را برمی‌گرداند.
     * معمولاً فقط پیامک‌های تازه خوانده می‌شوند؛ بار اول، بعد از به‌روزرسانی اپ، هفته‌ای یک بار، یا با forceFull کل صندوق.
     */
    suspend fun syncFromSms(forceFull: Boolean = false): Int

    /** true وقتی خواندن پیامک‌ها در جریان است */
    val isSyncing: StateFlow<Boolean>

    /** دسته‌ی یک تراکنش را تعیین می‌کند (null = بدون دسته). */
    suspend fun setCategory(transactionId: Long, categoryId: Long?)

    /** جفت‌های «برداشت ← واریزِ هم‌مبلغ تا ۲۴ ساعت» که شاید انتقال بین حساب‌های خود کاربر باشند (تازه‌ترها اول) */
    fun observeTransferSuggestions(): Flow<List<TransferSuggestion>>

    /** «بله، انتقال به خودم بود»: هر دو تراکنش از خرج و درآمد بیرون می‌روند و کارت مقصد یاد گرفته می‌شود. */
    suspend fun confirmTransfer(suggestion: TransferSuggestion)

    /** «نه»: این برداشت دیگر به‌عنوان انتقال پیشنهاد نمی‌شود. */
    suspend fun rejectTransfer(suggestion: TransferSuggestion)

    /** علامت زدن/برداشتن دستیِ «انتقال به خودم» برای یک تراکنش */
    suspend fun setSelfTransfer(transactionId: Long, isSelfTransfer: Boolean)

    /**
     * دسته‌ی شخصی می‌سازد. parentId = null یعنی دسته‌ی اصلی جدید.
     * @param icon فقط برای دسته‌ی اصلی
     */
    suspend fun createCategory(name: String, parentId: Long?, flowType: Int, icon: String?): CreateCategoryResult

    /** حذف دسته‌ی شخصی (و زیردسته‌هایش): تراکنش‌هایشان به دسته‌ی بالاتر یا بی‌دسته می‌روند */
    suspend fun deleteCustomCategory(categoryId: Long)

    /**
     * ثبت دستی یک خرج یا درآمد. شناسه‌ی تراکنش تازه را برمی‌گرداند.
     * @param note «برای چی بود؟» — مثل طرف حساب رفتار می‌کند (پیشنهاد و یادگیری دسته)
     */
    suspend fun addManual(type: FlowType, amountRial: Long, categoryId: Long?, note: String?, dateMillis: Long): Long

    /** حذف تراکنش دستی (فقط تراکنش‌های دستی؛ پیامکی‌ها از روی پیامک دوباره ساخته می‌شوند) */
    suspend fun deleteManual(transactionId: Long)

    /** پرکاربردترین دسته‌ها برای این نوع (بیشترین استفاده اول) */
    suspend fun frequentCategoryIds(flowType: Int, limit: Int): List<Long>

    /** «چقد دارم؟»: آخرین مانده‌ی هر بانک، تازه‌ترین اول */
    fun observeBankBalances(): Flow<List<BankBalance>>
}

class TransactionRepositoryImpl(
    private val db: AppDatabase,
    private val smsReader: SmsReader,
    private val syncState: SyncState,
    /** بعد از تعیین دسته صدا زده می‌شود (برای بررسی هشدار بودجه) */
    private val onCategoryChanged: suspend () -> Unit = {},
    /** بعد از هر همگام‌سازی پیامک‌ها (مثلاً برای به‌روز کردن ویجت) */
    private val onSynced: suspend () -> Unit = {},
    /** دامنه‌ی کل اپ؛ برای این‌که فهرست تراکنش‌ها یک بار ساخته و بین همه‌ی صفحه‌ها مشترک شود */
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : TransactionRepository {

    private val dao = db.transactionFlowDao()
    private val syncMutex = Mutex()
    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    override fun observeTransactions(): Flow<List<Transaction>> = sharedTransactions

    override fun observeTransferSuggestions(): Flow<List<TransferSuggestion>> = sharedTransferSuggestions

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
                    kind = EventKind.of(f.eventKind),
                )
            }
        }

    private fun buildTransferSuggestions(transactions: Flow<List<Transaction>>): Flow<List<TransferSuggestion>> =
        transactions.map { all ->
            val rows = all.filter { !it.isFailedPurchase && !it.isSelfTransfer && !it.isTransferRejected }
            val byId = rows.associateBy { it.id }
            TransferMatcher.findPairs(
                rows.map {
                    TransferCandidate(
                        id = it.id,
                        isWithdrawal = it.transaction.type == FlowType.WITHDRAWAL,
                        amountRial = it.transaction.amountRial,
                        dateMillis = it.dateMillis,
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
                icon = if (parent == null) icon ?: CustomCategories.ICONS.first() else null,
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
            )
        )
        onCategoryChanged()
        return id
    }

    override suspend fun deleteManual(transactionId: Long) {
        val row = dao.byId(transactionId) ?: return
        if (row.source != SOURCE_MANUAL) return
        dao.softDelete(listOf(row.id), System.currentTimeMillis())
        onCategoryChanged()
    }

    override suspend fun frequentCategoryIds(flowType: Int, limit: Int): List<Long> =
        dao.frequentCategoryIds(flowType, limit)

    override fun observeBankBalances(): Flow<List<BankBalance>> =
        dao.observeBankBalances().map { rows ->
            rows.distinctBy { it.bankId }.mapNotNull { row ->
                BankDirectory.byId(row.bankId)?.let { BankBalance(it, row.remainAfter, row.dateEpoch) }
            }
        }.flowOn(Dispatchers.Default)

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
            doSync(forceFull).also { onSynced() }
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun doSync(forceFull: Boolean): Int {
        ensureDefaultCategories()
        // برگشت پول خودکار دسته‌ی «برگشت پول» می‌گیرد تا درآمد ماه را بزرگ نکند.
        // برداشت از خودپرداز عمداً خودکار نیست: خرج حساب می‌شود و کاربر با یک لمس دسته‌اش را می‌گوید
        // (پول نقد بالاخره جایی خرج می‌شود؛ ثبت دستی دوباره‌اش اصطکاک اضافه است).
        val kindDefaults = mapOf(
            EventKind.REFUND to db.categoryDao().byCode(Taxonomy.CODE_REFUND)?.id,
        )
        val ownAccounts = dao.ownAccounts().toHashSet()
        val learning = CategoryLearning(db)
        val reviewDao = db.reviewDao()
        val startedAt = System.currentTimeMillis()
        val full = forceFull || syncState.needsFullScan(startedAt)
        val scan = smsReader.scan(
            afterSmsId = if (full) null else syncState.lastSmsId,
            afterSmsDate = syncState.lastSmsDate,
            knownOtpSenders = syncState.otpSenders,
            ignoredSenders = reviewDao.ignoredSenders().toSet(),
            adoptedSenders = reviewDao.bankSenderRules().associate { it.sender to (it.bankId ?: BankDirectory.OTHER.id) },
            templates = reviewDao.templates().groupBy({ it.sender }) {
                LearnedTemplate(it.skeleton, it.numberCount, it.amountPos, it.balancePos, it.typeMode)
            },
        )
        val items = scan.transactions
        val now = System.currentTimeMillis()

        var inserted = 0
        db.withTransaction {
            val rows = dao.smsKeys()
            val rowsById = rows.associateBy { it.id }
            // کدام ردیف مال کدام پیامک است (با در نظر گرفتن عوض شدن شناسه‌ها در گوشی تازه / بعد از بازگردانی پشتیبان).
            // جستجو با «زمان + متن» فقط در اسکن کامل؛ اسکن افزایشی فقط شناسه + زمان.
            val match = SmsRowMatcher.match(
                scanned = items.map { SmsRowMatcher.Scanned(it.id, it.dateMillis, it.body) },
                rows = rows.map { SmsRowMatcher.Row(it.id, it.smsId, it.dateEpoch) },
                contentLookup = if (scan.scannedFromDate == null) {
                    suspend { dao.smsContentKeys().associate { (it.dateEpoch to it.smsContent) to it.id } }
                } else {
                    null
                },
            )
            val toInsert = mutableListOf<TransactionFlowEntity>()
            val toUpdate = mutableListOf<TransactionFlowEntity>()

            for (item in items) {
                val old = match.matches[item.id]?.let { rowsById[it] }
                if (old != null && (old.categoryId != null || old.transferState == TransactionFlowEntity.TRANSFER_SELF)) {
                    // دسته و وضعیت «انتقال به خودم» که قبلاً گذاشته شده حفظ می‌شود؛ بقیه‌ی ستون‌ها از نتیجه‌ی تازه می‌آیند
                    toUpdate += item.toEntity(
                        old.id, old.categoryId, old.isAutoCategorized, old.notifiedAt, now,
                        old.transferState, old.transferPairId,
                    )
                    continue
                }
                // برداشت به کارت/حسابی که کاربر گفته مال خودش است ← انتقال به خودم (بی‌دسته)
                val toOwnAccount = item.transaction.type == FlowType.WITHDRAWAL &&
                    item.merchant != null && item.merchant in ownAccounts &&
                    (old == null || old.transferState == TransactionFlowEntity.TRANSFER_NONE)
                if (toOwnAccount) {
                    toInsertOrUpdate(old, item, now, toInsert, toUpdate, null, TransactionFlowEntity.TRANSFER_SELF, null)
                    continue
                }
                // بی‌دسته: از انتخاب‌های قبلی کاربر برای همین طرف حساب یاد بگیر
                // (۳ تأیید پشت سر هم ← خودکار؛ کمتر ← فقط پیشنهاد)
                val decision = learning.decide(item.merchant, item.transaction.type.code)
                // انتخاب‌های خود کاربر برای این طرف حساب مقدم‌اند؛ اگر چیزی یاد نگرفته‌ایم، نوع رویداد
                val autoCategory = decision?.takeIf { it.auto }?.categoryId
                    ?: if (decision == null) kindDefaults[item.kind] else null
                val suggestion = decision?.takeIf { !it.auto }?.let { learning.nameOf(it.categoryId) }
                toInsertOrUpdate(
                    old, item, now, toInsert, toUpdate, autoCategory,
                    old?.transferState ?: TransactionFlowEntity.TRANSFER_NONE, suggestion,
                )
            }

            // پیامکی که قبلاً خودکار تراکنش شده بود ولی دیگر نیست (مثلاً بعداً معلوم شد پولِ برگشتی بوده) ← حذف نرم.
            // تراکنش‌هایی که کاربر خودش از صندوق بررسی ثبت کرده (SMS_MANUAL) دست نمی‌خورند.
            // در اسکن افزایشی فقط بازه‌ی خوانده‌شده بررسی می‌شود (پیامک‌های قدیمی‌تر اصلاً خوانده نشده‌اند).
            val matchedRows = match.matches.values.toHashSet()
            val from = scan.scannedFromDate
            val gone = rows
                .filter { !it.isDeleted && it.source == SOURCE_SMS_AUTO && it.id !in matchedRows }
                .filter { from == null || (it.dateEpoch >= from && (it.smsId == null || it.smsId <= scan.maxSmsId)) }
                .map { it.id }

            // اول شناسه‌ی ردیف‌هایی که مال پیامک دیگری است آزاد شود، بعد نوشتن (وگرنه ایندکس یکتای smsId جلویش را می‌گیرد)
            match.detach.chunked(500).forEach { dao.detachSms(it) }
            if (toInsert.isNotEmpty()) dao.insertAll(toInsert)
            if (toUpdate.isNotEmpty()) dao.updateAll(toUpdate)
            gone.chunked(500).forEach { dao.softDelete(it, now) }
            inserted = toInsert.size

            // صندوق بررسی: پیامک‌های تازه‌ی خوانده‌نشده اضافه می‌شوند (تکراری‌ها نادیده)،
            // و آن‌هایی که حالا خودکار خوانده شده‌اند، دیگر منتظر نمی‌مانند
            scan.reviewCandidates.forEach { reviewDao.deleteStale(it.smsId, it.dateMillis) }
            reviewDao.insertAll(scan.reviewCandidates.map {
                ReviewSmsEntity(smsId = it.smsId, sender = it.sender, body = it.body, dateEpoch = it.dateMillis, bankId = it.bankId)
            })
            reviewDao.resolveAlreadyParsed(now)
        }
        syncState.save(
            maxId = scan.maxSmsId,
            maxDate = scan.maxSmsDate,
            otpSenders = scan.otpSenders,
            fullScanAt = if (full) startedAt else null,
        )
        return inserted
    }

    /** دسته‌های پیش‌فرض (و یک بار، انتقال دسته‌های نسخه‌ی قبل به ساختار درختی) */
    private suspend fun ensureDefaultCategories() = CategorySeeder(db).ensure()

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

    private fun toInsertOrUpdate(
        old: SmsFlowKey?,
        item: TransactionItem,
        now: Long,
        toInsert: MutableList<TransactionFlowEntity>,
        toUpdate: MutableList<TransactionFlowEntity>,
        categoryId: Long?,
        transferState: Int,
        learnedSuggestion: String?,
    ) {
        if (old == null) {
            toInsert += item.toEntity(0, categoryId, categoryId != null, null, now, transferState, null, learnedSuggestion)
        } else {
            toUpdate += item.toEntity(
                old.id, categoryId, categoryId != null, old.notifiedAt, now, transferState, old.transferPairId, learnedSuggestion,
            )
        }
    }

    private fun TransactionItem.toEntity(
        id: Long,
        categoryId: Long?,
        isAutoCategorized: Boolean,
        notifiedAt: Long?,
        now: Long,
        transferState: Int,
        transferPairId: Long?,
        /** دسته‌ای که از انتخاب‌های کاربر یاد گرفته شده؛ بر پیشنهادِ کلمه‌ای (CategorySuggester) مقدم است */
        learnedSuggestion: String? = null,
    ) = TransactionFlowEntity(
        id = id,
        smsId = this.id,
        bankId = bank.id,
        flowType = transaction.type.code,
        amount = transaction.amountRial,
        remainAfter = transaction.balanceRial,
        dateEpoch = dateMillis,
        merchant = merchant,
        // پیشنهاد: یادگرفته از کاربر ← از روی فروشنده (دقیق‌تر، مثلاً «برق») ← از روی نوع رویداد
        suggestedCategory = learnedSuggestion ?: suggestedCategory ?: kindSuggestion(kind),
        isFailedPurchase = refundDateMillis != null,
        categoryId = categoryId,
        // ستون description برای پیامک‌ها: کارمزد انتقال (به ریال)، اگر معلوم باشد
        description = feeRial?.let { FEE_PREFIX + it },
        smsContent = body,
        source = SOURCE_SMS_AUTO,
        isDeleted = false,
        updatedAt = now,
        notifiedAt = notifiedAt,
        isAutoCategorized = isAutoCategorized,
        transferState = transferState,
        transferPairId = transferPairId,
        eventKind = kind.code,
    )

    private fun kindSuggestion(kind: EventKind): String? = when (kind) {
        EventKind.BILL_PAYMENT -> Taxonomy.byCode("finance.bills")?.name
        EventKind.FEE -> Taxonomy.byCode("finance.fees")?.name
        else -> null
    }

    private companion object {
        const val SHARE_TIMEOUT_MILLIS = 5_000L
    }
}
