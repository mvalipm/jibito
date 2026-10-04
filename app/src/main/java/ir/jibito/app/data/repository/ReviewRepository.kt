package ir.jibito.app.data.repository

import androidx.room.withTransaction
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.category.CategorySuggester
import ir.jibito.app.data.category.CategoryLearning
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.ReviewSmsEntity
import ir.jibito.app.data.local.entity.SenderRuleEntity
import ir.jibito.app.data.local.entity.SmsTemplateEntity
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.AccountExtractor
import ir.jibito.app.data.parser.MerchantExtractor
import ir.jibito.app.data.parser.SmsTextNormalizer
import ir.jibito.app.data.review.NumberToken
import ir.jibito.app.data.review.ReviewDetector
import ir.jibito.app.data.review.TemplateMatcher
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

    /**
     * «ثبت تراکنش»: مبلغ‌ها همان عددهای داخل پیامک‌اند؛ اگر پیامک تومانی باشد این‌جا ریال می‌شوند.
     * علاوه بر ثبت، «قالب» این پیامک یاد گرفته می‌شود تا دفعه‌های بعد خودکار خوانده شود.
     * @param bankId برای فرستنده‌ی ناشناس: بانک/موسسه‌ای که کاربر انتخاب کرده (BankDirectory.OTHER.id برای «سایر»)
     */
    suspend fun confirm(item: ReviewItem, type: FlowType, amount: NumberToken, balance: NumberToken?, bankId: Int?)

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

    override suspend fun confirm(
        item: ReviewItem,
        type: FlowType,
        amount: NumberToken,
        balance: NumberToken?,
        bankId: Int?,
    ) {
        val now = System.currentTimeMillis()
        val factor = if (item.guess.inToman) 10 else 1
        val row = dao.byId(item.smsId) ?: return
        val text = SmsTextNormalizer.normalize(row.body)
        val merchant = MerchantExtractor.find(text)
        val normalizedSender = BankDirectory.normalizeSender(row.sender)
        val finalBankId = row.bankId ?: bankId
        db.withTransaction {
            // فرستنده‌ی ناشناس ← از این به بعد جزو بانک‌ها/موسسه‌ها
            if (row.bankId == null && bankId != null) {
                dao.upsertSenderRule(
                    SenderRuleEntity(normalizedSender, SenderRuleEntity.ACTION_BANK, bankId, now)
                )
            }
            // یادگیری قالب: پیامک‌های بعدیِ همین فرستنده با همین قالب خودکار خوانده می‌شوند
            val learned = TemplateMatcher.learn(text, amount, balance, type)
            dao.insertTemplate(
                SmsTemplateEntity(
                    sender = normalizedSender,
                    skeleton = learned.skeleton,
                    numberCount = learned.numberCount,
                    amountPos = learned.amountPos,
                    balancePos = learned.balancePos,
                    typeMode = learned.typeMode,
                    createdAt = now,
                )
            )

            val flowDao = db.transactionFlowDao()
            // یادگیری دسته: ۳ تأیید پشت سر هم ← خودکار؛ کمتر ← فقط پیشنهاد
            val learning = CategoryLearning(db)
            val decision = learning.decide(merchant, type.code)
            val learnedCategory = decision?.takeIf { it.auto }?.categoryId
            val learnedSuggestion = decision?.takeIf { !it.auto }?.let { learning.nameOf(it.categoryId) }
            flowDao.insertAll(
                listOf(
                    TransactionFlowEntity(
                        smsId = row.smsId,
                        bankId = finalBankId,
                        flowType = type.code,
                        amount = amount.value * factor,
                        remainAfter = balance?.value?.times(factor),
                        dateEpoch = row.dateEpoch,
                        merchant = merchant,
                        suggestedCategory = learnedSuggestion
                            ?: if (type == FlowType.WITHDRAWAL) CategorySuggester.suggest(merchant) else null,
                        isFailedPurchase = false,
                        categoryId = learnedCategory,
                        description = null,
                        smsContent = row.body,
                        source = SOURCE_SMS_MANUAL,
                        updatedAt = now,
                        notifiedAt = now, // نوتیفیکیشن «مال چی بود؟» لازم نیست؛ کاربر همین الان در اپ است
                        isAutoCategorized = learnedCategory != null,
                        account = AccountExtractor.find(text),
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
                // بقیه‌ی پیامک‌های منتظرِ همین سرشماره هم کنار می‌روند.
                // مقایسه با شکل نرمال‌شده: «+98۲۰۰۰…» و «۲۰۰۰…» یک سرشماره‌اند.
                val target = BankDirectory.normalizeSender(item.sender)
                val sameSender = dao.pendingList()
                    .filter { BankDirectory.normalizeSender(it.sender) == target }
                    .map { it.smsId }
                sameSender.chunked(500).forEach { dao.dismissMany(it, now) }
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
        bankName = BankDirectory.byId(bankId)?.name,
        guess = ReviewDetector.guess(SmsTextNormalizer.normalize(body)),
    )
}
