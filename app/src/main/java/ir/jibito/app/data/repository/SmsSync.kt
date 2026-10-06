package ir.jibito.app.data.repository

import androidx.room.withTransaction
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.category.CategoryLearning
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.data.sms.SmsRowMatcher
import ir.jibito.app.data.sms.SyncState
import ir.jibito.app.data.sms.TransactionItem
import ir.jibito.app.data.local.entity.SmsFlowKey
import ir.jibito.app.data.local.entity.ReviewSmsEntity
import ir.jibito.app.data.review.LearnedTemplate
import kotlinx.coroutines.flow.map
import ir.jibito.app.data.category.CategorySeeder

/**
 * خواندن پیامک‌ها و نوشتن تراکنش‌ها در دیتابیس (یک بار همگام‌سازی).
 * TransactionRepositoryImpl.syncFromSms آن را با قفل و وضعیت «در حال خواندن» صدا می‌زند.
 */
internal class SmsSync(
    private val db: AppDatabase,
    private val smsReader: SmsReader,
    private val syncState: SyncState,
) {
    private val dao = db.transactionFlowDao()

    suspend fun run(forceFull: Boolean): Int {
        ensureDefaultCategories()
        val ownAccounts = dao.ownAccounts().toHashSet()
        val learning = CategoryLearning(db)
        val reviewDao = db.reviewDao()
        val startedAt = System.currentTimeMillis()
        val full = forceFull || syncState.needsFullScan(startedAt)
        val scan = smsReader.scan(
            afterSmsId = if (full) null else syncState.lastSmsId,
            afterSmsDate = syncState.lastSmsDate,
            knownOtpSenders = syncState.otpSenders,
            ignoredSenders = reviewDao.ignoredSenders().toSet(),
            adoptedSenders = reviewDao.bankSenderRules().associate { it.sender to (it.bankId ?: BankDirectory.OTHER.id) },
            templates = reviewDao.templates().groupBy({ it.sender }) {
                LearnedTemplate(it.skeleton, it.numberCount, it.amountPos, it.balancePos, it.typeMode)
            },
        )
        val items = scan.transactions
        val now = System.currentTimeMillis()

        var inserted = 0
        db.withTransaction {
            val rows = dao.smsKeys()
            val rowsById = rows.associateBy { it.id }
            // کدام ردیف مال کدام پیامک است (با در نظر گرفتن عوض شدن شناسه‌ها در گوشی تازه / بعد از بازگردانی پشتیبان).
            // جستجو با «زمان + متن» فقط در اسکن کامل؛ اسکن افزایشی فقط شناسه + زمان.
            val match = SmsRowMatcher.match(
                scanned = items.map { SmsRowMatcher.Scanned(it.id, it.dateMillis, it.body) },
                rows = rows.map { SmsRowMatcher.Row(it.id, it.smsId, it.dateEpoch) },
                contentLookup = if (scan.scannedFromDate == null) {
                    suspend { dao.smsContentKeys().associate { (it.dateEpoch to it.smsContent) to it.id } }
                } else {
                    null
                },
            )
            val toInsert = mutableListOf<TransactionFlowEntity>()
            val toUpdate = mutableListOf<TransactionFlowEntity>()

            for (item in items) {
                val old = match.matches[item.id]?.let { rowsById[it] }
                if (old != null && (old.categoryId != null || old.transferState == TransactionFlowEntity.TRANSFER_SELF)) {
                    // دسته و وضعیت «انتقال به خودم» که قبلاً گذاشته شده حفظ می‌شود؛ بقیه‌ی ستون‌ها از نتیجه‌ی تازه می‌آیند
                    toUpdate += item.toEntity(
                        old.id, old.categoryId, old.isAutoCategorized, old.notifiedAt, now,
                        old.transferState, old.transferPairId, categorizedAt = old.categorizedAt, note = old.note,
                        oneOffState = old.oneOffState,
                    )
                    continue
                }
                // برداشت به کارت/حسابی که کاربر گفته مال خودش است ← انتقال به خودم (بی‌دسته)
                val toOwnAccount = item.transaction.type == FlowType.WITHDRAWAL &&
                    item.merchant != null && item.merchant in ownAccounts &&
                    (old == null || old.transferState == TransactionFlowEntity.TRANSFER_NONE)
                if (toOwnAccount) {
                    toInsertOrUpdate(old, item, now, toInsert, toUpdate, null, TransactionFlowEntity.TRANSFER_SELF, null)
                    continue
                }
                // بی‌دسته: از انتخاب‌های قبلی کاربر برای همین طرف حساب یاد بگیر
                // (۳ تأیید پشت سر هم ← خودکار؛ کمتر ← فقط پیشنهاد)
                val decision = learning.decide(item.merchant, item.transaction.type.code)
                val autoCategory = decision?.takeIf { it.auto }?.categoryId
                val suggestion = decision?.takeIf { !it.auto }?.let { learning.nameOf(it.categoryId) }
                toInsertOrUpdate(
                    old, item, now, toInsert, toUpdate, autoCategory,
                    old?.transferState ?: TransactionFlowEntity.TRANSFER_NONE, suggestion,
                )
            }

            // پیامکی که قبلاً خودکار تراکنش شده بود ولی دیگر نیست (مثلاً بعداً معلوم شد پولِ برگشتی بوده) ← حذف نرم.
            // فقط اگر خود پیامک هنوز در صندوق باشد: پیامکِ پاک‌شده (یا گوشی تازه بدون پیامک‌های قدیمی)
            // تراکنش و دسته‌اش را پاک نمی‌کند.
            // تراکنش‌هایی که کاربر خودش از صندوق بررسی ثبت کرده (SMS_MANUAL) دست نمی‌خورند.
            // در اسکن افزایشی فقط بازه‌ی خوانده‌شده بررسی می‌شود (پیامک‌های قدیمی‌تر اصلاً خوانده نشده‌اند).
            val matchedRows = match.matches.values.toHashSet()
            val from = scan.scannedFromDate
            val unmatched = rows
                .filter { !it.isDeleted && it.source == SOURCE_SMS_AUTO && it.id !in matchedRows }
                .filter { from == null || (it.dateEpoch >= from && (it.smsId == null || it.smsId <= scan.maxSmsId)) }
            val gone = SmsRowMatcher.reclassified(
                unmatched = unmatched.map { SmsRowMatcher.Row(it.id, it.smsId, it.dateEpoch) },
                inboxIds = scan.inboxIds,
                inboxContent = scan.inboxBankContent,
                rowContent = { dao.smsContentKeys().associate { it.id to it.smsContent } },
            )

            // اول شناسه‌ی ردیف‌هایی که مال پیامک دیگری است آزاد شود، بعد نوشتن (وگرنه ایندکس یکتای smsId جلویش را می‌گیرد)
            match.detach.chunked(500).forEach { dao.detachSms(it) }
            if (toInsert.isNotEmpty()) dao.insertAll(toInsert)
            if (toUpdate.isNotEmpty()) dao.updateAll(toUpdate)
            gone.chunked(500).forEach { dao.softDelete(it, now) }
            inserted = toInsert.size

            // صندوق بررسی: پیامک‌های تازه‌ی خوانده‌نشده اضافه می‌شوند (تکراری‌ها نادیده)،
            // و آن‌هایی که حالا خودکار خوانده شده‌اند، دیگر منتظر نمی‌مانند
            scan.reviewCandidates.forEach { reviewDao.deleteStale(it.smsId, it.dateMillis) }
            reviewDao.insertAll(scan.reviewCandidates.map {
                ReviewSmsEntity(smsId = it.smsId, sender = it.sender, body = it.body, dateEpoch = it.dateMillis, bankId = it.bankId)
            })
            reviewDao.resolveAlreadyParsed(now)
        }
        syncState.save(
            maxId = scan.maxSmsId,
            maxDate = scan.maxSmsDate,
            otpSenders = scan.otpSenders,
            fullScanAt = if (full) startedAt else null,
        )
        return inserted
    }

    /** دسته‌های پیش‌فرض (و یک بار، انتقال دسته‌های نسخه‌ی قبل به ساختار درختی) */
    private suspend fun ensureDefaultCategories() = CategorySeeder(db).ensure()

    private fun toInsertOrUpdate(
        old: SmsFlowKey?,
        item: TransactionItem,
        now: Long,
        toInsert: MutableList<TransactionFlowEntity>,
        toUpdate: MutableList<TransactionFlowEntity>,
        categoryId: Long?,
        transferState: Int,
        learnedSuggestion: String?,
    ) {
        if (old == null) {
            toInsert += item.toEntity(0, categoryId, categoryId != null, null, now, transferState, null, learnedSuggestion)
        } else {
            toUpdate += item.toEntity(
                old.id, categoryId, categoryId != null, old.notifiedAt, now, transferState, old.transferPairId, learnedSuggestion,
                note = old.note, oneOffState = old.oneOffState,
            )
        }
    }

    private fun TransactionItem.toEntity(
        id: Long,
        categoryId: Long?,
        isAutoCategorized: Boolean,
        notifiedAt: Long?,
        now: Long,
        transferState: Int,
        transferPairId: Long?,
        /** دسته‌ای که از انتخاب‌های کاربر یاد گرفته شده؛ بر پیشنهادِ کلمه‌ای (CategorySuggester) مقدم است */
        learnedSuggestion: String? = null,
        /** زمان انتخاب دسته توسط کاربر؛ با خواندن دوباره‌ی پیامک‌ها حفظ می‌شود */
        categorizedAt: Long? = null,
        /** یادداشت خود کاربر؛ با خواندن دوباره‌ی پیامک‌ها حفظ می‌شود */
        note: String? = null,
        /** علامت «خرج یک‌باره»؛ با خواندن دوباره‌ی پیامک‌ها حفظ می‌شود */
        oneOffState: Int = TransactionFlowEntity.ONE_OFF_NONE,
    ) = TransactionFlowEntity(
        id = id,
        smsId = this.id,
        bankId = bank.id,
        flowType = transaction.type.code,
        amount = transaction.amountRial,
        remainAfter = transaction.balanceRial,
        dateEpoch = dateMillis,
        merchant = merchant,
        suggestedCategory = learnedSuggestion ?: suggestedCategory,
        isFailedPurchase = refundDateMillis != null,
        categoryId = categoryId,
        // ستون description برای پیامک‌ها: کارمزد انتقال (به ریال)، اگر معلوم باشد
        description = feeRial?.let { FEE_PREFIX + it },
        smsContent = body,
        source = SOURCE_SMS_AUTO,
        isDeleted = false,
        updatedAt = now,
        notifiedAt = notifiedAt,
        isAutoCategorized = isAutoCategorized,
        transferState = transferState,
        transferPairId = transferPairId,
        categorizedAt = categorizedAt,
        account = account,
        note = note,
        oneOffState = oneOffState,
    )
}
