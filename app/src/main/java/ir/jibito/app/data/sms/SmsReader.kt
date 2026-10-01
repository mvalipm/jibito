package ir.jibito.app.data.sms

import android.content.Context
import android.provider.Telephony
import ir.jibito.app.data.bank.Bank
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
class SmsReader(private val context: Context) {

    private data class Raw(val id: Long, val sender: String, val body: String, val date: Long, val bank: Bank)

    suspend fun readTransactions(): List<TransactionItem> = withContext(Dispatchers.IO) {
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
                val type = SenderClassifier.classify(sender) as? SenderType.BankSender ?: continue
                val body = cursor.getString(bodyCol) ?: continue
                val raw = Raw(cursor.getLong(idCol), sender, body, cursor.getLong(dateCol), type.bank)

                val tx = TransactionParser.parse(raw.bank, body)
                if (tx != null) {
                    raws[raw.id] = raw
                    txRecords += TxRecord(raw.id, raw.date, raw.bank.id, tx)
                    continue
                }
                val otp = TransactionParser.parseOtp(body)
                if (otp != null) {
                    otpRecords += OtpRecord(raw.id, raw.date, raw.bank.id, otp)
                }
            }
        }

        PurchaseLinker.link(txRecords, otpRecords).map { linked ->
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
    }
}
