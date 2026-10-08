package ir.jibito.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ir.jibito.app.data.local.entity.BudgetEntity
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.CategorySum
import ir.jibito.app.data.local.entity.ReviewSmsEntity
import ir.jibito.app.data.local.entity.SenderRuleEntity
import ir.jibito.app.data.local.entity.SmsTemplateEntity
import ir.jibito.app.data.wallet.AccountBalanceRow
import ir.jibito.app.data.wallet.BalancePointRow
import ir.jibito.app.data.wallet.FlowAccountRow
import ir.jibito.app.data.wallet.FlowRow
import ir.jibito.app.data.local.entity.AccountLinkEntity
import ir.jibito.app.data.wallet.KnownAccountRow
import ir.jibito.app.data.local.entity.OverallBudgetEntity
import ir.jibito.app.data.local.entity.RecurringPaymentEntity
import kotlinx.coroutines.flow.Flow
import ir.jibito.app.data.local.entity.DatedAmount

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

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

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

/**
 * تنها تعریفِ «کدام تراکنش در جمع‌ها حساب می‌شود» در SQL: خریدهای ناموفق، حذف‌شده‌ها و انتقال به خودم نه.
 * همه‌ی کوئری‌های گزارش و خلاصه همین را به کار می‌برند؛ قاعده‌ی تازه (مثلاً «خرج یک‌باره») فقط اینجا اضافه می‌شود.
 * بخش دسته‌ای قاعده (پس‌انداز و قرض دادن خرج نیستند) در کاتلین است: SpendRollup.
 */
const val COUNTED_FLOWS = "isDeleted = 0 AND isFailedPurchase = 0 AND transferState != 1"

@Dao
interface SummaryDao {

    /**
     * جمع واریز یا برداشت هر دسته در یک بازه (null = بی‌دسته)؛ فقط تراکنش‌های COUNTED_FLOWS.
     * جمع زدن روی دسته‌ی اصلی (درخت) در کاتلین انجام می‌شود: SpendRollup.
     */
    @Query(
        "SELECT categoryId, COALESCE(SUM(amount), 0) AS totalRial, " +
            "COALESCE(SUM(CASE WHEN oneOffState = 1 THEN amount ELSE 0 END), 0) AS oneOffRial FROM transaction_flows WHERE " + COUNTED_FLOWS +
            " AND flowType = :flowType AND dateEpoch >= :from AND dateEpoch < :to GROUP BY categoryId"
    )
    fun observeSums(flowType: Int, from: Long, to: Long): Flow<List<CategorySum>>

    /** همان شرط‌های observeSums، ولی هر تراکنش جدا با زمانش (روند ماه‌ها، خلاصه‌ی هفتگی) */
    @Query(
        "SELECT categoryId, amount, dateEpoch, oneOffState = 1 AS isOneOff FROM transaction_flows WHERE " + COUNTED_FLOWS +
            " AND flowType = :flowType AND dateEpoch >= :from AND dateEpoch < :to"
    )
    fun observeAmounts(flowType: Int, from: Long, to: Long): Flow<List<DatedAmount>>

    @Query(
        "SELECT categoryId, amount, dateEpoch, oneOffState = 1 AS isOneOff FROM transaction_flows WHERE " + COUNTED_FLOWS +
            " AND flowType = :flowType AND dateEpoch >= :from AND dateEpoch < :to"
    )
    suspend fun amounts(flowType: Int, from: Long, to: Long): List<DatedAmount>

    @Query(
        "SELECT categoryId, COALESCE(SUM(amount), 0) AS totalRial, " +
            "COALESCE(SUM(CASE WHEN oneOffState = 1 THEN amount ELSE 0 END), 0) AS oneOffRial FROM transaction_flows WHERE " + COUNTED_FLOWS +
            " AND flowType = :flowType AND dateEpoch >= :from AND dateEpoch < :to GROUP BY categoryId"
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

/** چند حساب در یک بانک: مانده‌ی هر شماره حساب و تصمیم‌های کاربر (AccountGrouping) */
@Dao
interface AccountDao {

    /**
     * «چقد دارم؟»: آخرین مانده‌ی هر (بانک، شماره حساب)؛ پیامک‌های بی‌شماره‌حساب یک گروه جدا (account = null).
     * (در SQLite ستون‌های کنار MAX() از همان ردیفِ بیشینه می‌آیند.)
     */
    @Query(
        """
        SELECT bankId, account, remainAfter, MAX(dateEpoch) AS dateEpoch FROM transaction_flows
        WHERE isDeleted = 0 AND remainAfter IS NOT NULL AND bankId IS NOT NULL
        GROUP BY bankId, account
        """
    )
    fun observeLatestBalances(): Flow<List<AccountBalanceRow>>

    /** «روند موجودی»: همه‌ی پیامک‌های مانده‌دار، به ترتیب زمان (BalanceHistory) */
    @Query(
        """
        SELECT id, bankId, account, remainAfter, dateEpoch, flowType, amount FROM transaction_flows
        WHERE isDeleted = 0 AND remainAfter IS NOT NULL AND bankId IS NOT NULL
        ORDER BY dateEpoch, id
        """
    )
    fun observeBalancePoints(): Flow<List<BalancePointRow>>

    /**
     * «پولم تا حقوق می‌رسه؟»: تراکنش‌های بانکی از [from] به بعد (بدون انتقال به خودم و خرید ناموفق)،
     * برای الگوی برداشت‌ها و تشخیص حقوق (BalanceForecastCalc، SalaryDetector).
     */
    @Query(
        "SELECT id, bankId, account, flowType, amount, dateEpoch, merchant, oneOffState = 1 AS isOneOff FROM transaction_flows " +
            "WHERE " + COUNTED_FLOWS + " AND bankId IS NOT NULL AND dateEpoch >= :from"
    )
    fun observeFlowsSince(from: Long): Flow<List<FlowRow>>

    /** شماره حسابِ هر تراکنشِ یک بانک (صفحه‌ی جزئیات حساب: کدام تراکنش‌ها مال این حساب‌اند) */
    @Query("SELECT id, account FROM transaction_flows WHERE isDeleted = 0 AND bankId = :bankId")
    fun observeBankFlowAccounts(bankId: Int): Flow<List<FlowAccountRow>>

    /** شماره حساب‌هایی که در پیامک‌های هر بانک دیده شده‌اند */
    @Query(
        """
        SELECT bankId, account, MAX(dateEpoch) AS lastSeen FROM transaction_flows
        WHERE isDeleted = 0 AND bankId IS NOT NULL AND account IS NOT NULL
        GROUP BY bankId, account
        """
    )
    fun observeKnownAccounts(): Flow<List<KnownAccountRow>>

    @Query(
        """
        SELECT bankId, account, MAX(dateEpoch) AS lastSeen FROM transaction_flows
        WHERE isDeleted = 0 AND bankId IS NOT NULL AND account IS NOT NULL
        GROUP BY bankId, account
        """
    )
    suspend fun knownAccounts(): List<KnownAccountRow>

    @Query("SELECT * FROM account_links")
    fun observeLinks(): Flow<List<AccountLinkEntity>>

    @Query("SELECT * FROM account_links")
    suspend fun links(): List<AccountLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(links: List<AccountLinkEntity>)

    @Query("DELETE FROM account_links WHERE bankId = :bankId AND account = :account")
    suspend fun delete(bankId: Int, account: String)
}
