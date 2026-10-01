package ir.jibito.app.data.repository

import androidx.room.withTransaction
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.category.CategorySuggester
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.ReviewSmsEntity
import ir.jibito.app.data.local.entity.SenderRuleEntity
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.MerchantExtractor
import ir.jibito.app.data.parser.SmsTextNormalizer
import ir.jibito.app.data.review.ReviewDetector
import ir.jibito.app.data.review.ReviewGuess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** یک پیامک در صندوق بررسی، با حدس اولیه‌ی اپ. */
data class ReviewItem(
    val smsId: Long,
    val sender: String,
    val body: String,
    val dateMillis: Long,
    /** اسم بانک اگر فرستنده شناخته‌شده باشد */
    val bankName: String?,
    val guess: ReviewGuess,
)

interface ReviewRepository {
    fun observePending(): Flow<List<ReviewItem>>

    /** چند پیامک تازه هست که صفحه‌ی بررسی هنوز خودکار نشانشان نداده؟ */
    suspend fun countNotYetShown(): Int

    suspend fun markAllShown()

    /** «ثبت تراکنش»: مبلغ‌ها همان عددهای داخل پیامک‌اند؛ اگر پیامک تومانی باشد این‌جا ریال می‌شوند. */
    suspend fun confirm(item: ReviewItem, type: FlowType, amount: Long, balance: Long?)

    /** «تراکنش نیست» — و اگر ignoreSender، پیامک‌های این فرستنده دیگر به صندوق نمی‌آیند. */
    suspend fun dismiss(item: ReviewItem, ignoreSender: Boolean)

    /** متن آماده‌ی «ارسال برای بهبود اپ» — همه‌ی رقم‌ها پوشانده شده. */
    fun shareText(item: ReviewItem): String
}

class ReviewRepositoryImpl(
    private val db: AppDatabase,
    /** بعد از ثبت یک تراکنش تازه (برای بررسی هشدار بودجه) */
    private val onTransactionAdded: suspend () -> Unit,
) : ReviewRepository {

    private val dao = db.reviewDao()

    override fun observePending(): Flow<List<ReviewItem>> = dao.observePending().map { rows ->
        rows.map { it.toItem() }
    }

    override suspend fun countNotYetShown(): Int = dao.countNotYetShown()

    override suspend fun markAllShown() = dao.markAllShown(System.currentTimeMillis())

    override suspend fun confirm(item: ReviewItem, type: FlowType, amount: Long, balance: Long?) {
        val now = System.currentTimeMillis()
        val factor = if (item.guess.inToman) 10 else 1
        val row = dao.byId(item.smsId) ?: return
        val text = SmsTextNormalizer.normalize(row.body)
        val merchant = MerchantExtractor.find(text)
        db.withTransaction {
            val flowDao = db.transactionFlowDao()
            val learned = merchant?.let { flowDao.learnedCategory(it, type.code) }
            flowDao.insertAll(
                listOf(
                    TransactionFlowEntity(
                        smsId = row.smsId,
                        bankId = row.bankId,
                        flowType = type.code,
                        amount = amount * factor,
                        remainAfter = balance?.times(factor),
                        dateEpoch = row.dateEpoch,
                        merchant = merchant,
                        suggestedCategory = if (type == FlowType.WITHDRAWAL) CategorySuggester.suggest(merchant) else null,
                        isFailedPurchase = false,
                        categoryId = learned,
                        description = null,
                        smsContent = row.body,
                        source = SOURCE_SMS_MANUAL,
                        updatedAt = now,
                        notifiedAt = now, // نوتیفیکیشن «مال چی بود؟» لازم نیست؛ کاربر همین الان در اپ است
                        isAutoCategorized = learned != null,
                    )
                )
            )
            dao.resolve(row.smsId, ReviewSmsEntity.STATUS_CONFIRMED, now)
        }
        onTransactionAdded()
    }

    override suspend fun dismiss(item: ReviewItem, ignoreSender: Boolean) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.resolve(item.smsId, ReviewSmsEntity.STATUS_DISMISSED, now)
            if (ignoreSender) {
                dao.upsertSenderRule(
                    SenderRuleEntity(
                        sender = BankDirectory.normalizeSender(item.sender),
                        action = SenderRuleEntity.ACTION_IGNORE,
                        bankId = null,
                        createdAt = now,
                    )
                )
                dao.dismissAllFromSender(item.sender, now)
            }
        }
    }

    override fun shareText(item: ReviewItem): String = buildString {
        appendLine("نمونه پیامک خوانده‌نشده — جیبیتو")
        appendLine("فرستنده: ${item.sender}")
        item.bankName?.let { appendLine("بانک: $it") }
        appendLine("———")
        append(ReviewDetector.mask(item.body))
    }

    private fun ReviewSmsEntity.toItem(): ReviewItem = ReviewItem(
        smsId = smsId,
        sender = sender,
        body = body,
        dateMillis = dateEpoch,
        bankName = bankId?.let { id -> BankDirectory.banks.firstOrNull { it.id == id }?.name },
        guess = ReviewDetector.guess(SmsTextNormalizer.normalize(body)),
    )
}
