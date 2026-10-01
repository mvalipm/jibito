package ir.jibito.app.data.repository

import androidx.room.withTransaction
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.data.sms.TransactionItem
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.Transaction
import ir.jibito.app.data.local.entity.ReviewSmsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** منبع تراکنش: خودکار از پیامک، یا تأییدشده توسط کاربر از صندوق بررسی */
const val SOURCE_SMS_AUTO = "SMS_AUTO"
const val SOURCE_SMS_MANUAL = "SMS_MANUAL"

/**
 * تنها راه صفحه‌ها برای رسیدن به تراکنش‌ها (قانون سند معماری: ViewModel هرگز مستقیم Room نمی‌بیند).
 */
interface TransactionRepository {
    fun observeTransactions(): Flow<List<Transaction>>

    fun observeCategories(): Flow<List<Category>>

    /** پیامک‌ها را می‌خواند و دیتابیس را به‌روز می‌کند. تعداد تراکنش‌های جدید را برمی‌گرداند. */
    suspend fun syncFromSms(): Int

    /** true وقتی خواندن پیامک‌ها در جریان است */
    val isSyncing: StateFlow<Boolean>

    /** دسته‌ی یک تراکنش را تعیین می‌کند (null = بدون دسته). */
    suspend fun setCategory(transactionId: Long, categoryId: Long?)
}

class TransactionRepositoryImpl(
    private val db: AppDatabase,
    private val smsReader: SmsReader,
    /** بعد از تعیین دسته صدا زده می‌شود (برای بررسی هشدار بودجه) */
    private val onCategoryChanged: suspend () -> Unit = {},
) : TransactionRepository {

    private val dao = db.transactionFlowDao()
    private val syncMutex = Mutex()
    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    override fun observeTransactions(): Flow<List<Transaction>> =
        dao.observeAll().map { rows ->
            rows.map { row ->
                val f = row.flow
                Transaction(
                    id = f.id,
                    bank = f.bankId?.let { id -> BankDirectory.banks.firstOrNull { it.id == id } },
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
                    categoryIcon = row.categoryIcon,
                    isAutoCategorized = f.isAutoCategorized,
                )
            }
        }

    override fun observeCategories(): Flow<List<Category>> =
        db.categoryDao().observeActive().map { rows ->
            rows.map { Category(id = it.id, name = it.name, icon = it.icon, colorHex = it.colorHex, flowType = it.flowType) }
        }

    /** اگر هم‌زمان دو جا (مثلاً اپ و کار پس‌زمینه) بخواهند همگام کنند، دومی منتظر اولی می‌ماند. */
    override suspend fun syncFromSms(): Int = syncMutex.withLock {
        _isSyncing.value = true
        try {
            doSync()
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun doSync(): Int {
        ensureDefaultCategories()
        val reviewDao = db.reviewDao()
        val scan = smsReader.scan(ignoredSenders = reviewDao.ignoredSenders().toSet())
        val items = scan.transactions
        val now = System.currentTimeMillis()

        var inserted = 0
        db.withTransaction {
            val existing = dao.smsKeys().associateBy { it.smsId }
            val toInsert = mutableListOf<TransactionFlowEntity>()
            val toUpdate = mutableListOf<TransactionFlowEntity>()

            for (item in items) {
                val old = existing[item.id]
                if (old != null && old.categoryId != null) {
                    // دسته‌ای که قبلاً گذاشته شده حفظ می‌شود؛ بقیه‌ی ستون‌ها از نتیجه‌ی تازه می‌آیند
                    toUpdate += item.toEntity(old.id, old.categoryId, old.isAutoCategorized, old.notifiedAt, now)
                    continue
                }
                // بی‌دسته: اگر کاربر قبلاً برای همین طرف حساب دسته‌ای انتخاب کرده، همان را خودکار بگذار
                val learned = item.merchant?.let { dao.learnedCategory(it, item.transaction.type.code) }
                if (old == null) {
                    toInsert += item.toEntity(0, learned, learned != null, null, now)
                } else {
                    toUpdate += item.toEntity(old.id, learned, learned != null, old.notifiedAt, now)
                }
            }

            // پیامکی که قبلاً خودکار تراکنش شده بود ولی دیگر نیست (مثلاً بعداً معلوم شد پولِ برگشتی بوده) ← حذف نرم.
            // تراکنش‌هایی که کاربر خودش از صندوق بررسی ثبت کرده (SMS_MANUAL) دست نمی‌خورند.
            val currentIds = items.mapTo(HashSet()) { it.id }
            val gone = existing.values
                .filter { !it.isDeleted && it.source == SOURCE_SMS_AUTO && it.smsId !in currentIds }
                .map { it.id }

            if (toInsert.isNotEmpty()) dao.insertAll(toInsert)
            if (toUpdate.isNotEmpty()) dao.updateAll(toUpdate)
            gone.chunked(500).forEach { dao.softDelete(it, now) }
            inserted = toInsert.size

            // صندوق بررسی: پیامک‌های تازه‌ی خوانده‌نشده اضافه می‌شوند (تکراری‌ها نادیده)،
            // و آن‌هایی که حالا خودکار خوانده شده‌اند، دیگر منتظر نمی‌مانند
            reviewDao.insertAll(scan.reviewCandidates.map {
                ReviewSmsEntity(smsId = it.smsId, sender = it.sender, body = it.body, dateEpoch = it.dateMillis, bankId = it.bankId)
            })
            reviewDao.resolveAlreadyParsed(now)
        }
        return inserted
    }

    private suspend fun ensureDefaultCategories() {
        val categoryDao = db.categoryDao()
        if (categoryDao.count() == 0) categoryDao.insertAll(AppDatabase.DEFAULT_CATEGORIES)
    }

    override suspend fun setCategory(transactionId: Long, categoryId: Long?) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.setCategory(transactionId, categoryId, now)
            // یادگیری: بقیه‌ی تراکنش‌های بی‌دسته‌ی همین طرف حساب هم همین دسته را می‌گیرند
            val row = dao.byId(transactionId)
            if (row != null && categoryId != null && row.merchant != null) {
                dao.applyToSameMerchant(row.merchant, row.flowType, categoryId, now)
            }
        }
        onCategoryChanged()
    }

    private fun TransactionItem.toEntity(
        id: Long,
        categoryId: Long?,
        isAutoCategorized: Boolean,
        notifiedAt: Long?,
        now: Long,
    ) = TransactionFlowEntity(
        id = id,
        smsId = this.id,
        bankId = bank.id,
        flowType = transaction.type.code,
        amount = transaction.amountRial,
        remainAfter = transaction.balanceRial,
        dateEpoch = dateMillis,
        merchant = merchant,
        suggestedCategory = suggestedCategory,
        isFailedPurchase = refundDateMillis != null,
        categoryId = categoryId,
        description = null,
        smsContent = body,
        source = SOURCE_SMS_AUTO,
        isDeleted = false,
        updatedAt = now,
        notifiedAt = notifiedAt,
        isAutoCategorized = isAutoCategorized,
    )
}
