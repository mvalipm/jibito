package ir.jibito.app.data.sms

import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** یک پیامک خوانده‌شده از صندوق گوشی. */
data class RawSms(
    val id: Long,
    val sender: String,
    val body: String,
    val dateMillis: Long,
)

/**
 * پیامک‌های صندوق ورودی را می‌خواند و فقط آن‌هایی را برمی‌گرداند
 * که شبیه پیامک بانکی‌اند. (فعلاً با چند کلمه‌ی کلیدی ساده؛
 * در قدم‌های بعد، تشخیص دقیق بر اساس شماره‌ی هر بانک اضافه می‌شود.)
 */
class SmsReader(private val context: Context) {

    private val bankKeywords = listOf(
        "مبلغ", "برداشت", "واريز", "واریز", "مانده", "موجودي", "موجودی",
        "خريد", "خرید", "انتقال", "حساب", "كارت", "کارت", "ريال", "ریال",
    )

    suspend fun readBankSms(maxToScan: Int = 1000): List<RawSms> = withContext(Dispatchers.IO) {
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
                if (looksLikeBankSms(body)) {
                    result += RawSms(
                        id = cursor.getLong(idCol),
                        sender = cursor.getString(addrCol) ?: "?",
                        body = body,
                        dateMillis = cursor.getLong(dateCol),
                    )
                }
            }
        }
        result
    }

    /** حداقل دو کلمه‌ی کلیدی بانکی + یک عدد چندرقمی. */
    private fun looksLikeBankSms(body: String): Boolean {
        val hits = bankKeywords.count { body.contains(it) }
        val hasNumber = Regex("[0-9۰-۹][0-9۰-۹,٬،]{3,}").containsMatchIn(body)
        return hits >= 2 && hasNumber
    }
}
