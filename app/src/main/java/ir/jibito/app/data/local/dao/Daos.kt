package ir.jibito.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ir.jibito.app.data.local.entity.BudgetEntity
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.CategorySpendRow
import ir.jibito.app.data.local.entity.ReviewSmsEntity
import ir.jibito.app.data.local.entity.SenderRuleEntity
import ir.jibito.app.data.local.entity.SmsTemplateEntity
import ir.jibito.app.data.local.entity.SmsFlowKey
import ir.jibito.app.data.local.entity.OwnAccountEntity
import ir.jibito.app.data.local.entity.OverallBudgetEntity
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.local.entity.TransactionWithCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionFlowDao {

    @Query(
        """
        SELECT t.*, c.name AS categoryName, c.icon AS categoryIcon
        FROM transaction_flows t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.isDeleted = 0
        ORDER BY t.dateEpoch DESC
        """
    )
    fun observeAll(): Flow<List<TransactionWithCategory>>

    @Query("SELECT id, smsId, categoryId, isDeleted, notifiedAt, isAutoCategorized, source, dateEpoch, transferState, transferPairId FROM transaction_flows WHERE smsId IS NOT NULL")
    suspend fun smsKeys(): List<SmsFlowKey>

    /** تراکنش‌های پیامکیِ تازه (برای نوتیفیکیشن). */
    @Query("SELECT * FROM transaction_flows WHERE smsId IS NOT NULL AND isDeleted = 0 AND dateEpoch >= :since")
    suspend fun recentSmsFlows(since: Long): List<TransactionFlowEntity>

    @Query("UPDATE transaction_flows SET notifiedAt = :now WHERE id = :id")
    suspend fun markNotified(id: Long, now: Long)

    /** دسته‌ای که خود کاربر انتخاب کرده (دیگر «خودکار» نیست). */
    @Query("UPDATE transaction_flows SET categoryId = :categoryId, isAutoCategorized = 0, updatedAt = :now WHERE id = :id")
    suspend fun setCategory(id: Long, categoryId: Long?, now: Long)

    @Query("SELECT * FROM transaction_flows WHERE id = :id")
    suspend fun byId(id: Long): TransactionFlowEntity?

    /**
     * یادگیری: آخرین دسته‌ای که «خود کاربر» برای این طرف حساب انتخاب کرده.
     * (دسته‌های خودکار حساب نمی‌شوند، تا یک اشتباه خودش را تکرار نکند.)
     */
    @Query(
        """
        SELECT categoryId FROM transaction_flows
        WHERE merchant = :merchant AND flowType = :flowType AND categoryId IS NOT NULL
          AND isAutoCategorized = 0 AND isDeleted = 0
        ORDER BY dateEpoch DESC LIMIT 1
        """
    )
    suspend fun learnedCategory(merchant: String, flowType: Int): Long?

    /** همه‌ی تراکنش‌های بی‌دسته‌ی همین طرف حساب هم همین دسته را (خودکار) می‌گیرند. */
    @Query(
        """
        UPDATE transaction_flows SET categoryId = :categoryId, isAutoCategorized = 1, updatedAt = :now
        WHERE merchant = :merchant AND flowType = :flowType AND categoryId IS NULL AND isDeleted = 0
        """
    )
    suspend fun applyToSameMerchant(merchant: String, flowType: Int, categoryId: Long, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<TransactionFlowEntity>)

    @Update
    suspend fun updateAll(items: List<TransactionFlowEntity>)

    @Query("UPDATE transaction_flows SET isDeleted = 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<Long>, now: Long)

    /** وضعیت انتقال یک تراکنش (۰ عادی، ۱ انتقال به خودم، ۲ «انتقال نیست»). انتقال به خودم دسته ندارد. */
    @Query(
        """
        UPDATE transaction_flows SET transferState = :state, transferPairId = :pairId,
               categoryId = CASE WHEN :state = 1 THEN NULL ELSE categoryId END,
               isAutoCategorized = CASE WHEN :state = 1 THEN 0 ELSE isAutoCategorized END,
               updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun setTransfer(id: Long, state: Int, pairId: Long?, now: Long)

    /** برداشت‌های عادیِ به این مقصد ← انتقال به خودم (بعد از یاد گرفتن کارت خودم) */
    @Query(
        """
        UPDATE transaction_flows SET transferState = 1, categoryId = NULL, isAutoCategorized = 0, updatedAt = :now
        WHERE merchant = :merchant AND flowType = 2 AND transferState = 0 AND isDeleted = 0
        """
    )
    suspend fun markSelfTransferByMerchant(merchant: String, now: Long): Int

    @Query("SELECT merchant FROM own_accounts")
    suspend fun ownAccounts(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOwnAccount(account: OwnAccountEntity)

    @Query("DELETE FROM own_accounts WHERE merchant = :merchant")
    suspend fun deleteOwnAccount(merchant: String)
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY id")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insertAll(items: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: Long): CategoryEntity?

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
        WHERE isDeleted = 0 AND isFailedPurchase = 0 AND transferState != 1 AND flowType = :flowType
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
          ON t.categoryId = c.id AND t.isDeleted = 0 AND t.isFailedPurchase = 0 AND t.transferState != 1
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
          ON t.categoryId = c.id AND t.isDeleted = 0 AND t.isFailedPurchase = 0 AND t.transferState != 1
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

    /** جمع واریز یا برداشت در یک بازه (یک بار، برای هشدار) */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transaction_flows
        WHERE isDeleted = 0 AND isFailedPurchase = 0 AND transferState != 1 AND flowType = :flowType
          AND dateEpoch >= :from AND dateEpoch < :to
        """
    )
    suspend fun total(flowType: Int, from: Long, to: Long): Long

    // ── بودجه‌ی کل ماه ──

    @Query("SELECT * FROM overall_budget WHERE id = 1")
    fun observeOverallBudget(): Flow<OverallBudgetEntity?>

    @Query("SELECT * FROM overall_budget WHERE id = 1")
    suspend fun overallBudget(): OverallBudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverallBudget(budget: OverallBudgetEntity)

    @Query("DELETE FROM overall_budget")
    suspend fun deleteOverallBudget()

    @Query("UPDATE overall_budget SET alertedMonthKey = :monthKey, alertedLevel = :level WHERE id = 1")
    suspend fun markOverallAlerted(monthKey: Int, level: Int)
}

@Dao
interface ReviewDao {

    @Query("SELECT * FROM review_sms WHERE status = 0 ORDER BY dateEpoch DESC")
    fun observePending(): Flow<List<ReviewSmsEntity>>

    @Query("SELECT COUNT(*) FROM review_sms WHERE status = 0 AND autoShownAt IS NULL")
    suspend fun countNotYetShown(): Int

    @Query("UPDATE review_sms SET autoShownAt = :now WHERE status = 0 AND autoShownAt IS NULL")
    suspend fun markAllShown(now: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ReviewSmsEntity>)

    @Query("SELECT * FROM review_sms WHERE smsId = :smsId")
    suspend fun byId(smsId: Long): ReviewSmsEntity?

    @Query("UPDATE review_sms SET status = :status, resolvedAt = :now WHERE smsId = :smsId")
    suspend fun resolve(smsId: Long, status: Int, now: Long)

    /** پیامکی که حالا (مثلاً بعد از به‌روزرسانی پارسرها) خودکار خوانده شد، دیگر نیاز به بررسی ندارد. */
    @Query(
        """
        UPDATE review_sms SET status = 1, resolvedAt = :now
        WHERE status = 0 AND smsId IN (SELECT smsId FROM transaction_flows WHERE smsId IS NOT NULL AND isDeleted = 0)
        """
    )
    suspend fun resolveAlreadyParsed(now: Long)

    @Query("SELECT sender FROM sender_rules WHERE action = 1")
    suspend fun ignoredSenders(): List<String>

    /** سرشماره‌هایی که کاربر گفته «مال این بانک است» */
    @Query("SELECT * FROM sender_rules WHERE action = 2")
    suspend fun bankSenderRules(): List<SenderRuleEntity>

    @Query("SELECT * FROM sms_templates")
    suspend fun templates(): List<SmsTemplateEntity>

    @Insert
    suspend fun insertTemplate(template: SmsTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSenderRule(rule: SenderRuleEntity)

    /** همه‌ی پیامک‌های منتظر بررسی (یک بار، نه Flow) */
    @Query("SELECT * FROM review_sms WHERE status = 0")
    suspend fun pendingList(): List<ReviewSmsEntity>

    /** چند پیامک با هم «تراکنش نیست» می‌شوند */
    @Query("UPDATE review_sms SET status = 2, resolvedAt = :now WHERE status = 0 AND smsId IN (:smsIds)")
    suspend fun dismissMany(smsIds: List<Long>, now: Long)
}
