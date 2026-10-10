package ir.jibito.app.data.repository

import androidx.room.withTransaction
import ir.jibito.app.data.category.CategoryLearner
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.TransactionFlowEntity

/** نتیجه‌ی عوض کردن دسته‌ی تراکنش‌های مشابه: چند تا عوض شد و چطور برگردند */
class RecategorizeResult internal constructor(val count: Int, val undo: UndoSnapshot)

/**
 * وقتی کاربر دسته‌ی یک تراکنش را عوض می‌کند، دیگر تراکنش‌های همین طرف حساب که بی‌دسته‌اند
 * یا دسته‌شان را خود اپ (خودکار) گذاشته می‌توانند همراهش عوض شوند.
 * دسته‌ای که خود کاربر گذاشته دست نمی‌خورد، و انتقال کارت‌به‌کارت به شخص هم نه (یک کارت هم اجاره است هم قرض).
 */
internal class SimilarRecategorizer(private val db: AppDatabase) {

    private val dao = db.transactionFlowDao()

    /** @return تعداد تراکنش‌هایی که با [applyTo] عوض می‌شوند */
    suspend fun count(transactionId: Long, categoryId: Long): Int = candidates(transactionId, categoryId).size

    suspend fun applyTo(transactionId: Long, categoryId: Long): RecategorizeResult? {
        var result: RecategorizeResult? = null
        db.withTransaction {
            val rows = candidates(transactionId, categoryId)
            if (rows.isEmpty()) return@withTransaction
            val now = System.currentTimeMillis()
            rows.map { it.id }.chunked(CHUNK).forEach { dao.applyAutoCategory(it, categoryId, now) }
            result = RecategorizeResult(rows.size, UndoSnapshot(rows))
        }
        return result
    }

    private suspend fun candidates(transactionId: Long, categoryId: Long): List<TransactionFlowEntity> {
        val row = dao.byId(transactionId) ?: return emptyList()
        val merchant = row.merchant ?: return emptyList()
        if (CategoryLearner.isPersonTransfer(merchant)) return emptyList()
        return dao.similarToRecategorize(transactionId, merchant, row.flowType, categoryId)
    }

    private companion object {
        /** سقف متغیرهای یک پرس‌وجوی SQLite */
        const val CHUNK = 500
    }
}
