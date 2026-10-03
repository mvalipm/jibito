package ir.jibito.app.data.sms

import android.content.Context
import android.provider.Telephony

/** سرشماره‌ی یک پیامک (برای گزارش «اشتباه خوانده شده»)؛ اگر پیامک پاک شده یا اجازه نیست، null */
object SmsSenderLookup {

    fun sender(context: Context, smsId: Long?): String? {
        if (smsId == null) return null
        return runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms._ID} = ?",
                arrayOf(smsId.toString()),
                null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }
}
