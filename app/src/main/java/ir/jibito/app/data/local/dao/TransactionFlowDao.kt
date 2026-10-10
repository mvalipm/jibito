package ir.jibito.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ir.jibito.app.data.local.entity.DatedAmount
import ir.jibito.app.data.local.entity.MerchantChoice
import ir.jibito.app.data.local.entity.SmsContentKey
import ir.jibito.app.data.local.entity.SmsFlowKey
import ir.jibito.app.data.local.entity.OwnAccountEntity
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

    /** همه‌ی ردیف‌های پیامکی (ثبت دستی نه)؛ smsId ردیفی که شناسه‌اش آزاد شده null است */
    @Query("SELECT id, smsId, categoryId, isDeleted, notifiedAt, isAutoCategorized, source, dateEpoch, transferState, transferPairId, categorizedAt, note, oneOffState, merchant FROM transaction_flows WHERE source != 'MANUAL'")
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

    /**
     * «مال چی بود؟» را در یک قدم برای این تراکنش رزرو می‌کند: فقط اگر هنوز دسته ندارد و قبلاً پرسیده نشده.
     * دو همگام‌سازی هم‌زمان (یا همگام‌سازی و لمس دکمه‌ی نوتیفیکیشن) با این، سؤال را دو بار نمی‌پرسند
     * و «رفت تو …» را با سؤال کهنه عوض نمی‌کنند.
     * @return ۱ یعنی همین فراخوانی باید بپرسد؛ ۰ یعنی پیش‌تر پرسیده شده یا دسته گرفته
     */
    @Query("UPDATE transaction_flows SET notifiedAt = :now WHERE id = :id AND notifiedAt IS NULL AND categoryId IS NULL")
    suspend fun claimQuestion(id: Long, now: Long): Int

    /** نوتیفیکیشن نشان داده نشد (مثلاً اجازه‌اش نیست): دفعه‌ی بعد دوباره امتحان شود */
    @Query("UPDATE transaction_flows SET notifiedAt = NULL WHERE id = :id")
    suspend fun clearNotified(id: Long)

    /** دسته‌ای که خود کاربر انتخاب کرده (دیگر «خودکار» نیست)؛ زمان انتخاب برای یادگیری ثبت می‌شود. */
    @Query(
        """
        UPDATE transaction_flows SET categoryId = :categoryId, isAutoCategorized = 0, updatedAt = :now,
            categorizedAt = CASE WHEN :categoryId IS NULL THEN NULL ELSE :now END
        WHERE id = :id
        """
    )
    suspend fun setCategory(id: Long, categoryId: Long?, now: Long)

    /** یادداشت خود کاربر؛ null یعنی پاک شود */
    @Query("UPDATE transaction_flows SET note = :note, updatedAt = :now WHERE id = :id")
    suspend fun setNote(id: Long, note: String?, now: Long)

    /** خرج یک‌باره: ۰ عادی، ۱ یک‌باره، ۲ «یک‌باره نیست» */
    @Query("UPDATE transaction_flows SET oneOffState = :state, updatedAt = :now WHERE id = :id")
    suspend fun setOneOff(id: Long, state: Int, now: Long)

    @Query("SELECT * FROM transaction_flows WHERE id = :id")
    suspend fun byId(id: Long): TransactionFlowEntity?

    /** «اسم فروشگاه کدومه؟»: اسم طرف حساب و دسته‌ی پیشنهادی‌اش */
    @Query("UPDATE transaction_flows SET merchant = :merchant, suggestedCategory = :suggested, updatedAt = :now WHERE id = :id")
    suspend fun setMerchant(id: Long, merchant: String, suggested: String?, now: Long)

    /** تراکنش‌های پیامکیِ یک بانک که هنوز اسم طرف حساب ندارند (برای اعمال شکلی که کاربر تازه یاد داده) */
    @Query("SELECT * FROM transaction_flows WHERE bankId = :bankId AND merchant IS NULL AND isDeleted = 0 AND source != 'MANUAL'")
    suspend fun withoutMerchant(bankId: Int): List<TransactionFlowEntity>

    /**
     * یادگیری: دسته‌هایی که «خود کاربر» برای این طرف حساب انتخاب کرده، تازه‌ترین انتخاب اول
     * (به زمان انتخاب؛ انتخاب‌های قبل از نسخه‌ی ۱۲ دیتابیس که زمان ندارند، به تاریخ تراکنش).
     * (دسته‌های خودکار حساب نمی‌شوند، تا یک اشتباه خودش را تکرار نکند.)
     */
    @Query(
        """
        SELECT categoryId FROM transaction_flows
        WHERE merchant = :merchant AND flowType = :flowType AND categoryId IS NOT NULL
          AND isAutoCategorized = 0 AND isDeleted = 0 AND transferState != 1
        ORDER BY COALESCE(categorizedAt, dateEpoch) DESC, id DESC LIMIT :limit
        """
    )
    suspend fun userChoices(merchant: String, flowType: Int, limit: Int): List<Long>

    /**
     * همه‌ی (طرف حساب، دسته)هایی که خود کاربر انتخاب کرده، تازه‌ترین اول و هر جفت یک بار؛ بدون دسته‌های بایگانی‌شده.
     * خوراک MerchantWordModel (حدس دسته برای طرف حسابِ تازه از روی کلمه‌های مشترک).
     */
    @Query(
        """
        SELECT t.merchant AS merchant, t.categoryId AS categoryId FROM transaction_flows t
        JOIN categories c ON c.id = t.categoryId AND c.isArchived = 0
        WHERE t.merchant IS NOT NULL AND t.flowType = :flowType
          AND t.isAutoCategorized = 0 AND t.isDeleted = 0 AND t.transferState != 1
        GROUP BY t.merchant, t.categoryId
        ORDER BY MAX(COALESCE(t.categorizedAt, t.dateEpoch)) DESC LIMIT :limit
        """
    )
    suspend fun merchantChoices(flowType: Int, limit: Int): List<MerchantChoice>

    /** برداشت‌های عادی به یک طرف حساب از زمان [from]؛ مبلغ و زمان (برای حدس اجاره‌ی کارت‌به‌کارت) */
    @Query(
        """
        SELECT categoryId, amount, dateEpoch, 0 AS isOneOff FROM transaction_flows
        WHERE merchant = :merchant AND flowType = 2 AND dateEpoch >= :from
          AND isDeleted = 0 AND isFailedPurchase = 0 AND transferState != 1
        """
    )
    suspend fun paymentsTo(merchant: String, from: Long): List<DatedAmount>

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

    /**
     * «تراکنش‌های مشابه هم عوض شود؟»: دیگر تراکنش‌های همین طرف حساب که یا بی‌دسته‌اند یا دسته‌ی «خودکار» (غیر از دسته‌ی تازه) دارند.
     * دسته‌ای که خود کاربر گذاشته هیچ‌وقت در این فهرست نیست.
     */
    @Query(
        """
        SELECT * FROM transaction_flows
        WHERE merchant = :merchant AND flowType = :flowType AND id != :excludeId AND isDeleted = 0
          AND transferState != 1
          AND (categoryId IS NULL OR (isAutoCategorized = 1 AND categoryId != :categoryId))
        """
    )
    suspend fun similarToRecategorize(excludeId: Long, merchant: String, flowType: Int, categoryId: Long): List<TransactionFlowEntity>

    /** دسته‌ی «خودکار» (نه انتخاب کاربر، پس در یادگیری شمرده نمی‌شود) برای چند تراکنش */
    @Query("UPDATE transaction_flows SET categoryId = :categoryId, isAutoCategorized = 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun applyAutoCategory(ids: List<Long>, categoryId: Long, now: Long)

    /** «برگردان»: دسته، پیشنهاد، وضعیت انتقال و حذف یک تراکنش را به حالت قبل برمی‌گرداند */
    @Query(
        """
        UPDATE transaction_flows SET categoryId = :categoryId, isAutoCategorized = :isAuto,
            suggestedCategory = :suggested, transferState = :transferState, transferPairId = :transferPairId,
            isDeleted = :isDeleted, categorizedAt = :categorizedAt, updatedAt = :now
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
        categorizedAt: Long?,
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
