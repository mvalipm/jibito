package ir.jibito.app.data.sms

import android.content.Context
import android.provider.Telephony
import ir.jibito.app.data.bank.Bank
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.bank.SenderClassifier
import ir.jibito.app.data.bank.SenderType
import ir.jibito.app.data.category.CategorySuggester
import ir.jibito.app.data.linking.OtpRecord
import ir.jibito.app.data.linking.PurchaseLinker
import ir.jibito.app.data.linking.TxRecord
import ir.jibito.app.data.parser.MerchantExtractor
import ir.jibito.app.data.parser.NonTransactionFilter
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.data.parser.AccountExtractor
import ir.jibito.app.data.parser.SmsTextNormalizer
import ir.jibito.app.data.parser.TransactionParser
import ir.jibito.app.data.review.LearnedTemplate
import ir.jibito.app.data.review.ReviewDetector
import ir.jibito.app.data.review.TemplateMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** یک تراکنش آماده‌ی نمایش. */
data class TransactionItem(
    val id: Long,
    val bank: Bank,
    val sender: String,
    val body: String,
    val dateMillis: Long,
    val transaction: ParsedTransaction,
    /** مقصد خرید (از پیامک رمز دوم)، مثلاً «اسنپ». */
    val merchant: String?,
    /** دسته‌ی پیشنهادی بر اساس مقصد خرید. */
    val suggestedCategory: String?,
    /** پر باشد یعنی خرید قبول نشده و پول برگشته؛ این تاریخِ برگشت پول است. */
    val refundDateMillis: Long?,
    /** کارمزد انتقال، اگر از رمز دوم معلوم شده باشد */
    val feeRial: Long? = null,
    /** پیامک دیگر در صندوق گوشی نیست و از کپی خود اپ (sms_archive) خوانده شد؛ id منفی است */
    val fromArchive: Boolean = false,
)

/**
 * پیامک‌ها را می‌خواند و به فهرست تراکنش‌ها تبدیل می‌کند:
 * ۱. فقط پیامک سرشماره‌های بانکی (شخصی و ناشناس کنار گذاشته می‌شوند).
 * ۲. هر پیامک یا «تراکنش» است، یا «رمز دوم خرید»، یا هیچ‌کدام (کد، تبلیغ...).
 * ۳. رمز دوم‌ها به برداشت‌ها وصل می‌شوند و برگشت پول‌ها تشخیص داده می‌شود (PurchaseLinker).
 */
/** پیامکی که شبیه تراکنش است ولی خوانده نشد (برای «صندوق بررسی»). */
data class ReviewCandidate(
    val smsId: Long,
    val sender: String,
    val body: String,
    val dateMillis: Long,
    val bankId: Int?,
)

/** یک پیامک از کپی خود اپ (sms_archive) */
data class ArchivedSms(
    val archiveId: Long,
    val sender: String?,
    val bankId: Int?,
    val body: String,
    val dateMillis: Long,
)

/** پیامکی که تراکنش خوانده شد و باید در کپی اپ (sms_archive) نگه داشته شود */
data class ArchiveCandidate(
    val sender: String,
    val bankId: Int,
    val body: String,
    val dateMillis: Long,
)

data class ScanResult(
    val transactions: List<TransactionItem>,
    val reviewCandidates: List<ReviewCandidate>,
    /** بزرگ‌ترین _id و تاریخ پیامکی که در این اسکن دیده شد (برای اسکن افزایشی بعدی) */
    val maxSmsId: Long,
    val maxSmsDate: Long,
    /** null یعنی اسکن کامل؛ وگرنه فقط پیامک‌های از این زمان به بعد خوانده شده‌اند */
    val scannedFromDate: Long?,
    /** سرشماره‌های ناشناسی که رمز پویا فرستاده‌اند (قبلی‌ها + تازه‌ها) */
    val otpSenders: Set<String>,
    /**
     * پیامک‌های بانکی که در این اسکن دیده شدند (در صندوق یا کپی اپ) ولی تراکنش نیستند — رمز، تبلیغ،
     * پولِ برگشتیِ یک خرید ناموفق، یا پیامکی که دیگر خوانده نمی‌شود. فقط ردیف‌های همین پیامک‌ها حذف نرم می‌شوند؛
     * ردیفی که پیامکش اصلاً پیدا نشد دست نمی‌خورد.
     */
    val nonTransactions: List<SmsRowMatcher.Scanned> = emptyList(),
    /** پیامک‌های صندوق که تراکنش خوانده شدند، برای کپی در sms_archive */
    val toArchive: List<ArchiveCandidate> = emptyList(),
)

class SmsReader(private val context: Context) {

    private data class Raw(
        val id: Long,
        val sender: String,
        val body: String,
        val date: Long,
        val bank: Bank,
        val fromArchive: Boolean = false,
    )

    /**
     * @param ignoredSenders سرشماره‌هایی (نرمال‌شده) که کاربر گفته «دیگر نشان نده»
     * @param adoptedSenders سرشماره‌هایی (نرمال‌شده) که کاربر گفته «مال این بانک است» ← شناسه‌ی بانک
     * @param templates قالب‌های یادگرفته‌شده، برای هر سرشماره (نرمال‌شده)
     */
    suspend fun scan(
        ignoredSenders: Set<String> = emptySet(),
        adoptedSenders: Map<String, Int> = emptyMap(),
        templates: Map<String, List<LearnedTemplate>> = emptyMap(),
        /** null = اسکن کامل. وگرنه فقط پیامک‌هایی با _id بزرگ‌تر از این (به‌علاوه‌ی حاشیه‌ی زمانی) خوانده می‌شوند */
        afterSmsId: Long? = null,
        /** تاریخ آخرین پیامک پردازش‌شده؛ برای حاشیه‌ی زمانی اسکن افزایشی */
        afterSmsDate: Long = 0L,
        knownOtpSenders: Set<String> = emptySet(),
        /**
         * کپی پیامک‌ها در خود اپ؛ با زمان شروع اسکن صدا زده می‌شود (null = اسکن کامل، همه‌ی کپی‌ها).
         * کپی‌ای که هنوز در صندوق گوشی هست نادیده گرفته می‌شود؛ بقیه مثل پیامک صندوق خوانده می‌شوند.
         */
        archived: suspend (fromDate: Long?) -> List<ArchivedSms> = { emptyList() },
    ): ScanResult = withContext(Dispatchers.IO) {
        val reviewSince = System.currentTimeMillis() - REVIEW_WINDOW_MILLIS
        val candidates = mutableListOf<ReviewCandidate>()
        // فرستنده‌های ناشناس: امتیازشان بعد از خواندن همه حساب می‌شود (چون رمز پویای همان فرستنده ممکن است قدیمی‌تر باشد)
        val unknownPending = mutableListOf<Pair<ReviewCandidate, Int>>()
        val otpSenders = HashSet(knownOtpSenders)
        var maxId = 0L
        var maxDate = 0L

        // اسکن افزایشی: پیامک‌های تازه (_id بزرگ‌تر)، به‌علاوه‌ی ۱۰ دقیقه‌ی قبل از آخرین پیامک،
        // تا رمز دوم ↔ برداشت ↔ برگشت پول (که تا ۳ دقیقه فاصله دارند) باز هم به هم وصل شوند.
        val fromDate = afterSmsId?.let { (afterSmsDate - INCREMENTAL_MARGIN_MILLIS).coerceAtLeast(0) }
        val selection = if (afterSmsId != null) "${Telephony.Sms._ID} > ? OR ${Telephony.Sms.DATE} >= ?" else null
        val selectionArgs = if (afterSmsId != null) arrayOf(afterSmsId.toString(), fromDate.toString()) else null
        val raws = HashMap<Long, Raw>()
        val txRecords = mutableListOf<TxRecord>()
        val otpRecords = mutableListOf<OtpRecord>()
        // همه‌ی پیامک‌های بانکیِ خوانده‌شده (برای پیدا کردن ردیف‌هایی که دیگر تراکنش نیستند)
        val seen = mutableListOf<SmsRowMatcher.Scanned>()
        // متن پیامک‌های بانکیِ صندوق ← زمان‌هایشان (تا کپیِ همان پیامک‌ها دوباره خوانده نشود)
        val inboxBodies = HashMap<String, MutableList<Long>>()

        /**
         * یک پیامک بانکی (از صندوق یا کپی اپ): تراکنش، رمز دوم، یا اگر شبیه تراکنش بود ← صندوق بررسی.
         * اول پارسرهای اپ؛ اگر نشد، قالب‌هایی که کاربر یاد داده.
         */
        fun readBankSms(raw: Raw, normalizedSender: String?) {
            seen += SmsRowMatcher.Scanned(raw.id, raw.date, raw.body)
            val text = SmsTextNormalizer.normalize(raw.body)
            val tx = TransactionParser.parse(raw.bank, raw.body)
                ?: normalizedSender?.let { templates[it] }?.let { TemplateMatcher.match(text, it) }
            if (tx != null) {
                raws[raw.id] = raw
                txRecords += TxRecord(
                    raw.id, raw.date, raw.bank.id, tx,
                    isCorrection = PurchaseLinker.isCorrectionText(text),
                    account = AccountExtractor.find(text),
                )
                return
            }
            if (raw.fromArchive) return // کپی فقط از پیامک‌هایی است که تراکنش بودند؛ صندوق بررسی مال پیامک‌های صندوق است
            val otp = TransactionParser.parseOtp(raw.body)
            if (otp != null) {
                otpRecords += OtpRecord(raw.id, raw.date, raw.bank.id, otp)
                return
            }
            // فرستنده بانک است ولی متن خوانده نشد: اگر شبیه تراکنش بود ← صندوق بررسی
            if (raw.date >= reviewSince && ReviewDetector.isCandidate(text, ReviewDetector.BANK_SENDER_BONUS)) {
                candidates += ReviewCandidate(raw.id, raw.sender, raw.body, raw.date, raw.bank.id)
            }
        }

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addrCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            // کل صندوق پیامک خوانده می‌شود (قبلاً فقط ۳۰۰۰ پیامک آخر — برای همین پیامک‌های قدیمی‌تر جا می‌افتادند).
            // سرعت: اول فقط فرستنده چک می‌شود (سریع)؛ متن فقط برای پیامک‌های بانکی پارس می‌شود.
            while (cursor.moveToNext()) {
                val smsId = cursor.getLong(idCol)
                if (smsId > maxId) maxId = smsId
                val smsDate = cursor.getLong(dateCol)
                if (smsDate > maxDate) maxDate = smsDate
                val sender = cursor.getString(addrCol) ?: continue
                val normalizedSender = BankDirectory.normalizeSender(sender)
                // اول سرشماره‌هایی که خود کاربر به یک بانک/موسسه نسبت داده، بعد بانک‌های رسمی.
                // (اگر سرشماره‌ای بعداً به فهرست اپ اضافه شود — مثل MofidCard — موسسه‌ای که کاربر خودش ساخته بود
                // می‌ماند، تا تراکنش‌ها و مانده‌اش بین دو «بانک» تقسیم نشود.)
                // شماره‌ی شبه‌شخصی‌ای که کاربر گفته «مال این بانک است» هم این‌طوری پذیرفته می‌شود.
                val adopted = adoptedSenders[normalizedSender]
                val senderType = if (adopted != null) {
                    SenderType.BankSender(BankDirectory.byId(adopted) ?: BankDirectory.OTHER)
                } else {
                    SenderClassifier.classify(sender)
                }
                if (senderType == SenderType.Personal) {
                    // شماره‌ی شبه‌موبایل: فقط پیامکی که قطعاً شکل بانکی دارد ← صندوق بررسی (هرگز خودکار)
                    if (smsDate >= reviewSince && normalizedSender !in ignoredSenders) {
                        val body = cursor.getString(bodyCol)
                        if (body != null && ReviewDetector.isBankLikeFromPersonal(SmsTextNormalizer.normalize(body))) {
                            candidates += ReviewCandidate(smsId, sender, body, smsDate, null)
                        }
                    }
                    continue
                }
                if (senderType !is SenderType.BankSender) {
                    // فرستنده‌ی ناشناس (نه شخصی): اگر تازه و شبیه تراکنش بود ← صندوق بررسی
                    if (senderType == SenderType.Unknown) {
                        if (smsDate >= reviewSince && normalizedSender !in ignoredSenders) {
                            val body = cursor.getString(bodyCol) ?: continue
                            val text = SmsTextNormalizer.normalize(body)
                            if (NonTransactionFilter.looksLikeOtp(text)) {
                                otpSenders += normalizedSender
                                continue
                            }
                            val content = ReviewDetector.contentScore(text)
                            if (content > 0) {
                                val serviceBonus = if (ReviewDetector.isServiceNumber(normalizedSender)) ReviewDetector.SERVICE_NUMBER_BONUS else 0
                                unknownPending += ReviewCandidate(smsId, sender, body, smsDate, null) to (content + serviceBonus)
                            }
                        }
                    }
                    continue
                }
                val body = cursor.getString(bodyCol) ?: continue
                inboxBodies.getOrPut(body) { mutableListOf() } += smsDate
                readBankSms(Raw(smsId, sender, body, smsDate, senderType.bank), normalizedSender)
            }
        }

        // پیامک‌هایی که از صندوق گوشی رفته‌اند (پاک شده، گوشی تازه، بازگردانی پشتیبان) ← از کپی خود اپ
        for (a in SmsArchiveMerge.notInInbox(archived(fromDate), inboxBodies)) {
            val normalizedSender = a.sender?.let(BankDirectory::normalizeSender)
            val bank = BankDirectory.byId(a.bankId)
                ?: normalizedSender?.let { adoptedSenders[it] }?.let { BankDirectory.byId(it) }
                ?: (a.sender?.let(SenderClassifier::classify) as? SenderType.BankSender)?.bank
                ?: continue
            readBankSms(Raw(-a.archiveId, a.sender.orEmpty(), a.body, a.dateMillis, bank, fromArchive = true), normalizedSender)
        }

        // فرستنده‌ی ناشناس: فقط اگر امتیاز کل به آستانه برسد به صندوق بررسی می‌رود (ثبت خودکار هرگز)
        for ((candidate, partial) in unknownPending) {
            val otpBonus = if (BankDirectory.normalizeSender(candidate.sender) in otpSenders) ReviewDetector.OTP_SENDER_BONUS else 0
            if (partial + otpBonus >= ReviewDetector.REVIEW_THRESHOLD) candidates += candidate
        }

        val transactions = PurchaseLinker.link(txRecords, otpRecords).map { linked ->
            val raw = raws.getValue(linked.record.id)
            // طرف حساب: اول از پیامک رمز دوم، وگرنه از متن خود پیامک (مثلاً «خرید از فروشگاه ...» یا «انتقال به کارت ...»)
            val merchant = linked.merchant ?: MerchantExtractor.find(SmsTextNormalizer.normalize(raw.body))
            TransactionItem(
                id = raw.id,
                bank = raw.bank,
                sender = raw.sender,
                body = raw.body,
                dateMillis = raw.date,
                transaction = linked.record.tx,
                merchant = merchant,
                suggestedCategory = CategorySuggester.suggest(merchant),
                refundDateMillis = linked.refund?.timeMillis,
                feeRial = linked.feeRial,
                fromArchive = raw.fromArchive,
            )
        }
        val transactionIds = transactions.mapTo(HashSet()) { it.id }
        ScanResult(
            transactions = transactions,
            reviewCandidates = candidates,
            maxSmsId = maxId,
            maxSmsDate = maxDate,
            scannedFromDate = fromDate,
            otpSenders = otpSenders,
            nonTransactions = seen.filter { it.smsId !in transactionIds },
            toArchive = raws.values.filter { !it.fromArchive }
                .map { ArchiveCandidate(it.sender, it.bank.id, it.body, it.date) },
        )
    }

    companion object {
        /** فقط پیامک‌های خوانده‌نشده‌ی این مدت اخیر به صندوق بررسی می‌روند (نه کل تاریخچه) */
        const val REVIEW_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000

        /** حاشیه‌ی زمانی اسکن افزایشی (بیشتر از پنجره‌ی ۳ دقیقه‌ای اتصال رمز دوم و برگشت پول) */
        const val INCREMENTAL_MARGIN_MILLIS = 10L * 60 * 1000
    }
}
