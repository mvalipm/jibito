package ir.jibito.app.data.repository

import androidx.room.withTransaction
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.data.sms.TransactionItem
import ir.jibito.app.domain.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * تنها راه صفحه‌ها برای رسیدن به تراکنش‌ها (قانون سند معماری: ViewModel هرگز مستقیم Room نمی‌بیند).
 */
interface TransactionRepository {
    fun observeTransactions(): Flow<List<Transaction>>

    /** پیامک‌ها را می‌خواند و دیتابیس را به‌روز می‌کند. تعداد تراکنش‌های جدید را برمی‌گرداند. */
    suspend fun syncFromSms(): Int

    /** دسته‌ی یک تراکنش را تعیین می‌کند (null = بدون دسته). */
    suspend fun setCategory(transactionId: Long, categoryId: Long?)
}

class TransactionRepositoryImpl(
    private val db: AppDatabase,
    private val smsReader: SmsReader,
) : TransactionRepository {

    private val dao = db.transactionFlowDao()

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
                    categoryName = row.categoryName,
                )
            }
        }

    override suspend fun syncFromSms(): Int {
        ensureDefaultCategories()
        val items = smsReader.readTransactions()
        val now = System.currentTimeMillis()

        var inserted = 0
        db.withTransaction {
            val existing = dao.smsKeys().associateBy { it.smsId }
            val toInsert = mutableListOf<TransactionFlowEntity>()
            val toUpdate = mutableListOf<TransactionFlowEntity>()

            for (item in items) {
                val old = existing[item.id]
                if (old == null) {
                    toInsert += item.toEntity(id = 0, categoryId = null, notifiedAt = null, now = now)
                } else {
                    // دسته‌ای که کاربر انتخاب کرده حفظ می‌شود؛ بقیه‌ی ستون‌ها از نتیجه‌ی تازه می‌آیند
                    toUpdate += item.toEntity(id = old.id, categoryId = old.categoryId, notifiedAt = old.notifiedAt, now = now)
                }
            }

            // پیامکی که قبلاً تراکنش بود ولی دیگر نیست (مثلاً بعداً معلوم شد پولِ برگشتی بوده) ← حذف نرم
            val currentIds = items.mapTo(HashSet()) { it.id }
            val gone = existing.values.filter { !it.isDeleted && it.smsId !in currentIds }.map { it.id }

            if (toInsert.isNotEmpty()) dao.insertAll(toInsert)
            if (toUpdate.isNotEmpty()) dao.updateAll(toUpdate)
            gone.chunked(500).forEach { dao.softDelete(it, now) }
            inserted = toInsert.size
        }
        return inserted
    }

    private suspend fun ensureDefaultCategories() {
        val categoryDao = db.categoryDao()
        if (categoryDao.count() == 0) categoryDao.insertAll(AppDatabase.DEFAULT_CATEGORIES)
    }

    override suspend fun setCategory(transactionId: Long, categoryId: Long?) {
        dao.setCategory(transactionId, categoryId, System.currentTimeMillis())
    }

    private fun TransactionItem.toEntity(id: Long, categoryId: Long?, notifiedAt: Long?, now: Long) = TransactionFlowEntity(
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
        source = "SMS_AUTO",
        isDeleted = false,
        updatedAt = now,
        notifiedAt = notifiedAt,
    )
}
