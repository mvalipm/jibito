package ir.jibito.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ir.jibito.app.data.local.entity.BudgetEntity
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.CategorySum
import ir.jibito.app.data.local.entity.ReviewSmsEntity
import ir.jibito.app.data.local.entity.SenderRuleEntity
import ir.jibito.app.data.local.entity.SmsTemplateEntity
import ir.jibito.app.data.local.entity.SmsContentKey
import ir.jibito.app.data.local.entity.SmsFlowKey
import ir.jibito.app.data.local.entity.BankBalanceRow
import ir.jibito.app.data.local.entity.OwnAccountEntity
import ir.jibito.app.data.local.entity.OverallBudgetEntity
import ir.jibito.app.data.local.entity.RecurringPaymentEntity
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.local.entity.TransactionWithCategory
import kotlinx.coroutines.flow.Flow
import ir.jibito.app.data.local.entity.DatedAmount

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

    /** همه‌ی ردیف‌های پیامکی (ثبت دستی نه)؛ smsId ردیفی که شناسه‌اش آزاد شده null است */
    @Query("SELECT id, smsId, categoryId, isDeleted, notifiedAt, isAutoCategorized, source, dateEpoch, transferState, transferPairId FROM transaction_flows WHERE source != 'MANUAL'")
    suspend fun smsKeys(): List<SmsFlowKey>

    /** «زمان + متن» ردیف‌های پیامکی، برای پیدا کردن ردیف قبلی وقتی شناسه‌ی پیامک‌ها عوض شده (گوشی تازه) */
    @Query("SELECT id, dateEpoch, smsContent FROM transaction_flows WHERE source != 'MANUAL' AND smsContent IS NOT NULL")
    suspend fun smsContentKeys(): List<SmsContentKey>

    /** شناسه‌ی پیامک این ردیف‌ها را آزاد می‌کند (چون در این گوشی مال پیامک دیگری است) */
    @Query("UPDATE transaction_flows SET smsId = NULL WHERE id IN (:ids)")
    suspend fun detachSms(ids: List<Long>)

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
     * یادگیری: دسته‌هایی که «خود کاربر» برای این طرف حساب انتخاب کرده، تازه‌ترین اول.
     * (دسته‌های خودکار حساب نمی‌شوند، تا یک اشتباه خودش را تکرار نکند.)
     */
    @Query(
        """
        SELECT categoryId FROM transaction_flows
        WHERE merchant = :merchant AND flowType = :flowType AND categoryId IS NOT NULL
          AND isAutoCategorized = 0 AND isDeleted = 0 AND transferState != 1
        ORDER BY dateEpoch DESC LIMIT :limit
        """
    )
    suspend fun userChoices(merchant: String, flowType: Int, limit: Int): List<Long>

    /** اپ مطمئن شده ← تراکنش‌های بی‌دسته‌ی همین طرف حساب این دسته را (خودکار) می‌گیرند. */
    @Query(
        """
        UPDATE transaction_flows SET categoryId = :categoryId, isAutoCategorized = 1, updatedAt = :now
        WHERE merchant = :merchant AND flowType = :flowType AND categoryId IS NULL AND isDeleted = 0
          AND transferState != 1
        """
    )
    suspend fun applyToSameMerchant(merchant: String, flowType: Int, categoryId: Long, now: Long): Int

    /** هنوز مطمئن نیست ← تراکنش‌های بی‌دسته‌ی همین طرف حساب این دسته را «پیشنهاد» می‌گیرند. */
    @Query(
        """
        UPDATE transaction_flows SET suggestedCategory = :categoryName, updatedAt = :now
        WHERE merchant = :merchant AND flowType = :flowType AND categoryId IS NULL AND isDeleted = 0
          AND transferState != 1
        """
    )
    suspend fun suggestForSameMerchant(merchant: String, flowType: Int, categoryName: String, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<TransactionFlowEntity>)

    /**
     * «چقد دارم؟»: برای هر بانک، مانده‌ی آخرین پیامکی که مانده داشته.
     * (اگر یک بانک چند حساب داشته باشد، مانده‌ی آخرین پیامکِ هر کدام که تازه‌تر است.)
     */
    @Query(
        """
        SELECT t.bankId AS bankId, t.remainAfter AS remainAfter, t.dateEpoch AS dateEpoch
        FROM transaction_flows t
        WHERE t.isDeleted = 0 AND t.remainAfter IS NOT NULL AND t.bankId IS NOT NULL
          AND t.dateEpoch = (
            SELECT MAX(t2.dateEpoch) FROM transaction_flows t2
            WHERE t2.bankId = t.bankId AND t2.isDeleted = 0 AND t2.remainAfter IS NOT NULL
          )
        ORDER BY t.dateEpoch DESC
        """
    )
    fun observeBankBalances(): Flow<List<BankBalanceRow>>

    /** یک تراکنش (ثبت دستی)؛ شناسه‌اش را برمی‌گرداند */
    @Insert
    suspend fun insert(item: TransactionFlowEntity): Long

    /** دسته‌هایی که بیشترین تراکنش را دارند (برای دکمه‌های سریع ثبت دستی) */
    @Query(
        """
        SELECT t.categoryId FROM transaction_flows t
        JOIN categories c ON c.id = t.categoryId
        WHERE t.isDeleted = 0 AND t.flowType = :flowType AND c.isArchived = 0
        GROUP BY t.categoryId
        ORDER BY COUNT(*) DESC
        LIMIT :limit
        """
    )
    suspend fun frequentCategoryIds(flowType: Int, limit: Int): List<Long>

    @Update
    suspend fun updateAll(items: List<TransactionFlowEntity>)

    @Query("UPDATE transaction_flows SET isDeleted = 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<Long>, now: Long)

    /** تراکنش‌های بی‌دسته‌ی یک طرف حساب: همان‌هایی که یادگیری دسته ممکن است عوضشان کند (برای «برگردان») */
    @Query(
        """
        SELECT * FROM transaction_flows
        WHERE merchant = :merchant AND flowType = :flowType AND categoryId IS NULL AND isDeleted = 0
          AND transferState != 1
        """
    )
    suspend fun uncategorizedSameMerchant(merchant: String, flowType: Int): List<TransactionFlowEntity>

    /** «برگردان»: دسته، پیشنهاد، وضعیت انتقال و حذف یک تراکنش را به حالت قبل برمی‌گرداند */
    @Query(
        """
        UPDATE transaction_flows SET categoryId = :categoryId, isAutoCategorized = :isAuto,
            suggestedCategory = :suggested, transferState = :transferState, transferPairId = :transferPairId,
            isDeleted = :isDeleted, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun restoreUserState(
        id: Long,
        categoryId: Long?,
        isAuto: Boolean,
        suggested: String?,
        transferState: Int,
        transferPairId: Long?,
        isDeleted: Boolean,
        now: Long,
    )

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

    // ── انتقال دسته‌های نسخه‌ی قبل به ساختار جدید (یک بار) ──

    @Query("UPDATE transaction_flows SET categoryId = :newId WHERE categoryId = :oldId")
    suspend fun moveCategory(oldId: Long, newId: Long)

    /** حذف دسته‌ی شخصی: تراکنش‌هایش به دسته‌ی بالاتر (یا بی‌دسته) می‌روند */
    @Query("UPDATE transaction_flows SET categoryId = :newId, isAutoCategorized = 0 WHERE categoryId IN (:ids)")
    suspend fun reassign(ids: List<Long>, newId: Long?)

    @Query("UPDATE transaction_flows SET categoryId = NULL, isAutoCategorized = 0 WHERE categoryId = :oldId")
    suspend fun uncategorize(oldId: Long)

    @Query("UPDATE transaction_flows SET suggestedCategory = :newName WHERE suggestedCategory = :oldName")
    suspend fun renameSuggestion(oldName: String, newName: String?)

    @Query("SELECT merchant FROM own_accounts")
    suspend fun ownAccounts(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOwnAccount(account: OwnAccountEntity)

    @Query("DELETE FROM own_accounts WHERE merchant = :merchant")
    suspend fun deleteOwnAccount(merchant: String)
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<CategoryEntity>>

    /** همه (با بایگانی‌شده‌ها) — برای پیدا کردن دسته‌ی اصلیِ هر تراکنش */
    @Query("SELECT * FROM categories")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun all(): List<CategoryEntity>

    @Insert
    suspend fun insert(item: CategoryEntity): Long

    @Insert
    suspend fun insertAll(items: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE code = :code")
    suspend fun byCode(code: String): CategoryEntity?

    @Query("UPDATE categories SET colorHex = :color WHERE code = :code")
    suspend fun setColorByCode(code: String, color: String)

    @Query("UPDATE categories SET colorHex = :color WHERE id = :id")
    suspend fun setColor(id: Long, color: String)

    /** زیردسته‌های یک دسته ← زیر دسته‌ی دیگر (برای یکی کردن دسته‌های تکراری) */
    @Query("UPDATE categories SET parentId = :newParentId WHERE parentId = :oldParentId")
    suspend fun reparent(oldParentId: Long, newParentId: Long)

    @Query("UPDATE categories SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    /** دسته‌ی قدیمی‌ای که جای مشخصی در ساختار جدید ندارد ← دسته‌ی شخصی کاربر */
    @Query("UPDATE categories SET isCustom = 1, sortOrder = :sortOrder WHERE id = :id")
    suspend fun markCustom(id: Long, sortOrder: Int)

    /** دسته‌ها به ترتیب «بیشترین استفاده»؛ برای انتخاب دکمه‌های نوتیفیکیشن. */
    @Query(
        """
        SELECT c.* FROM categories c
        LEFT JOIN transaction_flows t ON t.categoryId = c.id AND t.isDeleted = 0
        WHERE c.isArchived = 0 AND c.flowType = :flowType AND c.countsAsSpend = 1
        GROUP BY c.id
        ORDER BY COUNT(t.id) DESC, c.sortOrder ASC, c.id ASC
        """
    )
    suspend fun byUsage(flowType: Int): List<CategoryEntity>
}

@Dao
interface SummaryDao {

    /**
     * جمع واریز یا برداشت هر دسته در یک بازه (null = بی‌دسته).
     * خریدهای ناموفق، حذف‌شده‌ها و انتقال به خودم حساب نمی‌شوند.
     * جمع زدن روی دسته‌ی اصلی (درخت) در کاتلین انجام می‌شود: SpendRollup.
     */
    @Query(
        """
        SELECT categoryId, COALESCE(SUM(amount), 0) AS totalRial FROM transaction_flows
        WHERE isDeleted = 0 AND isFailedPurchase = 0 AND transferState != 1 AND flowType = :flowType
          AND dateEpoch >= :from AND dateEpoch < :to
        GROUP BY categoryId
        """
    )
    fun observeSums(flowType: Int, from: Long, to: Long): Flow<List<CategorySum>>

    /** همان شرط‌های observeSums، ولی هر تراکنش جدا با زمانش (روند ماه‌ها، خلاصه‌ی هفتگی) */
    @Query(
        """
        SELECT categoryId, amount, dateEpoch FROM transaction_flows
        WHERE isDeleted = 0 AND isFailedPurchase = 0 AND transferState != 1 AND flowType = :flowType
          AND dateEpoch >= :from AND dateEpoch < :to
        """
    )
    fun observeAmounts(flowType: Int, from: Long, to: Long): Flow<List<DatedAmount>>

    @Query(
        """
        SELECT categoryId, amount, dateEpoch FROM transaction_flows
        WHERE isDeleted = 0 AND isFailedPurchase = 0 AND transferState != 1 AND flowType = :flowType
          AND dateEpoch >= :from AND dateEpoch < :to
        """
    )
    suspend fun amounts(flowType: Int, from: Long, to: Long): List<DatedAmount>

    @Query(
        """
        SELECT categoryId, COALESCE(SUM(amount), 0) AS totalRial FROM transaction_flows
        WHERE isDeleted = 0 AND isFailedPurchase = 0 AND transferState != 1 AND flowType = :flowType
          AND dateEpoch >= :from AND dateEpoch < :to
        GROUP BY categoryId
        """
    )
    suspend fun sums(flowType: Int, from: Long, to: Long): List<CategorySum>

    @Query("SELECT * FROM budgets")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun budgets(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId")
    suspend fun budgetFor(categoryId: Long): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun deleteBudget(categoryId: Long)

    @Query("UPDATE budgets SET alertedMonthKey = :monthKey, alertedLevel = :level WHERE categoryId = :categoryId")
    suspend fun markAlerted(categoryId: Long, monthKey: Int, level: Int)

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

    /**
     * ردیف بررسیِ کهنه با همین شناسه ولی زمان دیگر را پاک می‌کند: بعد از انتقال به گوشی تازه،
     * این شناسه مال پیامک دیگری است و نباید جلوی ورود پیامک تازه به صندوق بررسی را بگیرد.
     */
    @Query("DELETE FROM review_sms WHERE smsId = :smsId AND dateEpoch != :dateEpoch")
    suspend fun deleteStale(smsId: Long, dateEpoch: Long)

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

@Dao
interface RecurringDao {

    @Query("SELECT * FROM recurring_payments ORDER BY dayOfMonth, id")
    fun observeAll(): Flow<List<RecurringPaymentEntity>>

    @Query("SELECT * FROM recurring_payments")
    suspend fun all(): List<RecurringPaymentEntity>

    @Insert
    suspend fun insert(item: RecurringPaymentEntity): Long

    @Query("DELETE FROM recurring_payments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE recurring_payments SET lastRemindedMonthKey = :monthKey WHERE id = :id")
    suspend fun markReminded(id: Long, monthKey: Int)
}
