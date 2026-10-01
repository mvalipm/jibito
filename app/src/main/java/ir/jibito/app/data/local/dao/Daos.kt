package ir.jibito.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ir.jibito.app.data.local.entity.BudgetEntity
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.CategorySpendRow
import ir.jibito.app.data.local.entity.SmsFlowKey
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.local.entity.TransactionWithCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionFlowDao {

    @Query(
        """
        SELECT t.*, c.name AS categoryName
        FROM transaction_flows t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.isDeleted = 0
        ORDER BY t.dateEpoch DESC
        """
    )
    fun observeAll(): Flow<List<TransactionWithCategory>>

    @Query("SELECT id, smsId, categoryId, isDeleted, notifiedAt FROM transaction_flows WHERE smsId IS NOT NULL")
    suspend fun smsKeys(): List<SmsFlowKey>

    /** تراکنش‌های پیامکیِ تازه (برای نوتیفیکیشن). */
    @Query("SELECT * FROM transaction_flows WHERE smsId IS NOT NULL AND isDeleted = 0 AND dateEpoch >= :since")
    suspend fun recentSmsFlows(since: Long): List<TransactionFlowEntity>

    @Query("UPDATE transaction_flows SET notifiedAt = :now WHERE id = :id")
    suspend fun markNotified(id: Long, now: Long)

    @Query("UPDATE transaction_flows SET categoryId = :categoryId, updatedAt = :now WHERE id = :id")
    suspend fun setCategory(id: Long, categoryId: Long?, now: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<TransactionFlowEntity>)

    @Update
    suspend fun updateAll(items: List<TransactionFlowEntity>)

    @Query("UPDATE transaction_flows SET isDeleted = 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<Long>, now: Long)
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY id")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insertAll(items: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    /** دسته‌ها به ترتیب «بیشترین استفاده»؛ برای انتخاب دکمه‌های نوتیفیکیشن. */
    @Query(
        """
        SELECT c.* FROM categories c
        LEFT JOIN transaction_flows t ON t.categoryId = c.id AND t.isDeleted = 0
        WHERE c.isArchived = 0 AND c.flowType = :flowType
        GROUP BY c.id
        ORDER BY COUNT(t.id) DESC, c.id ASC
        """
    )
    suspend fun byUsage(flowType: Int): List<CategoryEntity>
}

@Dao
interface SummaryDao {

    /** جمع واریز یا برداشت در یک بازه (خریدهای ناموفق حساب نمی‌شوند). */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transaction_flows
        WHERE isDeleted = 0 AND isFailedPurchase = 0 AND flowType = :flowType
          AND dateEpoch >= :from AND dateEpoch < :to
        """
    )
    fun observeTotal(flowType: Int, from: Long, to: Long): Flow<Long>

    /** جمع هر دسته (خرج یا درآمد، بسته به flowType) در یک بازه + بودجه‌اش. */
    @Query(
        """
        SELECT c.id AS categoryId, c.name AS name, c.icon AS icon, c.colorHex AS colorHex,
               COALESCE(SUM(t.amount), 0) AS spentRial, b.monthlyLimitRial AS budgetRial
        FROM categories c
        LEFT JOIN transaction_flows t
          ON t.categoryId = c.id AND t.isDeleted = 0 AND t.isFailedPurchase = 0
         AND t.flowType = :flowType AND t.dateEpoch >= :from AND t.dateEpoch < :to
        LEFT JOIN budgets b ON b.categoryId = c.id
        WHERE c.isArchived = 0 AND c.flowType = :flowType
        GROUP BY c.id
        ORDER BY spentRial DESC, c.id ASC
        """
    )
    fun observeCategorySpend(flowType: Int, from: Long, to: Long): Flow<List<CategorySpendRow>>

    @Query(
        """
        SELECT c.id AS categoryId, c.name AS name, c.icon AS icon, c.colorHex AS colorHex,
               COALESCE(SUM(t.amount), 0) AS spentRial, b.monthlyLimitRial AS budgetRial
        FROM categories c
        LEFT JOIN transaction_flows t
          ON t.categoryId = c.id AND t.isDeleted = 0 AND t.isFailedPurchase = 0
         AND t.flowType = :flowType AND t.dateEpoch >= :from AND t.dateEpoch < :to
        LEFT JOIN budgets b ON b.categoryId = c.id
        WHERE c.isArchived = 0 AND c.flowType = :flowType
        GROUP BY c.id
        """
    )
    suspend fun categorySpend(flowType: Int, from: Long, to: Long): List<CategorySpendRow>

    @Query("SELECT * FROM budgets")
    suspend fun budgets(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun deleteBudget(categoryId: Long)

    @Query("UPDATE budgets SET alertedMonthKey = :monthKey, alertedLevel = :level WHERE categoryId = :categoryId")
    suspend fun markAlerted(categoryId: Long, monthKey: Int, level: Int)
}
