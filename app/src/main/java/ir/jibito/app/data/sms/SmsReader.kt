package ir.jibito.app.data.sms

import android.content.Context
import android.provider.Telephony
import ir.jibito.app.data.bank.Bank
import ir.jibito.app.data.bank.SenderClassifier
import ir.jibito.app.data.bank.SenderType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.data.parser.TransactionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** یک پیامک بانکی که تراکنش بودنش تأیید شده. */
data class RawSms(
    val id: Long,
    val sender: String,
    val body: String,
    val dateMillis: Long,
    val bank: Bank,
    val transaction: ParsedTransaction,
)

/**
 * پیامک‌های صندوق ورودی را می‌خواند و فقط آن‌هایی را برمی‌گرداند
 * که (۱) از سرشماره‌ی یک بانک شناخته‌شده آمده‌اند و (۲) متنشان یک تراکنش واقعی است.
 * پیامک شماره‌های شخصی، فرستنده‌های ناشناس، رمز/کد و تبلیغ بانک‌ها کنار گذاشته می‌شوند.
 */
class SmsReader(private val context: Context) {

    suspend fun readBankSms(maxToScan: Int = 3000): List<RawSms> = withContext(Dispatchers.IO) {
        val result = mutableListOf<RawSms>()
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
            var scanned = 0
            while (cursor.moveToNext() && scanned < maxToScan) {
                scanned++
                val body = cursor.getString(bodyCol) ?: continue
                val sender = cursor.getString(addrCol) ?: continue
                val type = SenderClassifier.classify(sender)
                if (type is SenderType.BankSender) {
                    val parsed = TransactionParser.parse(type.bank, body) ?: continue
                    result += RawSms(
                        id = cursor.getLong(idCol),
                        sender = sender,
                        body = body,
                        dateMillis = cursor.getLong(dateCol),
                        bank = type.bank,
                        transaction = parsed,
                    )
                }
            }
        }
        result
    }
}
