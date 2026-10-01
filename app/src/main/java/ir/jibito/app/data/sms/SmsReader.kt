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
import ir.jibito.app.data.parser.ParsedTransaction
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

data class ScanResult(
    val transactions: List<TransactionItem>,
    val reviewCandidates: List<ReviewCandidate>,
)

class SmsReader(private val context: Context) {

    private data class Raw(val id: Long, val sender: String, val body: String, val date: Long, val bank: Bank)

    /**
     * @param ignoredSenders سرشماره‌هایی (نرمال‌شده) که کاربر گفته «دیگر نشان نده»
     * @param adoptedSenders سرشماره‌هایی (نرمال‌شده) که کاربر گفته «مال این بانک است» ← شناسه‌ی بانک
     * @param templates قالب‌های یادگرفته‌شده، برای هر سرشماره (نرمال‌شده)
     */
    suspend fun scan(
        ignoredSenders: Set<String> = emptySet(),
        adoptedSenders: Map<String, Int> = emptyMap(),
        templates: Map<String, List<LearnedTemplate>> = emptyMap(),
    ): ScanResult = withContext(Dispatchers.IO) {
        val reviewSince = System.currentTimeMillis() - REVIEW_WINDOW_MILLIS
        val candidates = mutableListOf<ReviewCandidate>()
        val raws = HashMap<Long, Raw>()
        val txRecords = mutableListOf<TxRecord>()
        val otpRecords = mutableListOf<OtpRecord>()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addrCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            // کل صندوق پیامک خوانده می‌شود (قبلاً فقط ۳۰۰۰ پیامک آخر — برای همین پیامک‌های قدیمی‌تر جا می‌افتادند).
            // سرعت: اول فقط فرستنده چک می‌شود (سریع)؛ متن فقط برای پیامک‌های بانکی پارس می‌شود.
            while (cursor.moveToNext()) {
                val sender = cursor.getString(addrCol) ?: continue
                val normalizedSender = BankDirectory.normalizeSender(sender)
                // اول بانک‌های رسمی، بعد سرشماره‌هایی که خود کاربر به یک بانک/موسسه نسبت داده
                val senderType = SenderClassifier.classify(sender).let { t ->
                    val adopted = adoptedSenders[normalizedSender]
                    if (t == SenderType.Unknown && adopted != null) {
                        SenderType.BankSender(BankDirectory.byId(adopted) ?: BankDirectory.OTHER)
                    } else {
                        t
                    }
                }
                if (senderType !is SenderType.BankSender) {
                    // فرستنده‌ی ناشناس (نه شخصی): اگر تازه و شبیه تراکنش بود ← صندوق بررسی
                    if (senderType == SenderType.Unknown) {
                        val date = cursor.getLong(dateCol)
                        if (date >= reviewSince && normalizedSender !in ignoredSenders) {
                            val body = cursor.getString(bodyCol) ?: continue
                            if (ReviewDetector.isCandidate(SmsTextNormalizer.normalize(body))) {
                                candidates += ReviewCandidate(cursor.getLong(idCol), sender, body, date, null)
                            }
                        }
                    }
                    continue
                }
                val type = senderType
                val body = cursor.getString(bodyCol) ?: continue
                val raw = Raw(cursor.getLong(idCol), sender, body, cursor.getLong(dateCol), type.bank)

                // اول پارسرهای اپ؛ اگر نشد، قالب‌هایی که کاربر یاد داده
                val tx = TransactionParser.parse(raw.bank, body)
                    ?: templates[normalizedSender]?.let { TemplateMatcher.match(SmsTextNormalizer.normalize(body), it) }
                if (tx != null) {
                    raws[raw.id] = raw
                    txRecords += TxRecord(raw.id, raw.date, raw.bank.id, tx)
                    continue
                }
                val otp = TransactionParser.parseOtp(body)
                if (otp != null) {
                    otpRecords += OtpRecord(raw.id, raw.date, raw.bank.id, otp)
                    continue
                }
                // فرستنده بانک است ولی متن خوانده نشد: اگر شبیه تراکنش بود ← صندوق بررسی
                if (raw.date >= reviewSince && ReviewDetector.isCandidate(SmsTextNormalizer.normalize(body))) {
                    candidates += ReviewCandidate(raw.id, sender, body, raw.date, raw.bank.id)
                }
            }
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
            )
        }
        ScanResult(transactions, candidates)
    }

    companion object {
        /** فقط پیامک‌های خوانده‌نشده‌ی این مدت اخیر به صندوق بررسی می‌روند (نه کل تاریخچه) */
        const val REVIEW_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
