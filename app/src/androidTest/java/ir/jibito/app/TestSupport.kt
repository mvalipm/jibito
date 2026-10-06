package ir.jibito.app

import android.Manifest
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.repository.SOURCE_SMS_AUTO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.Calendar

/** ابزارهای مشترک تست‌های روی امولاتور */
object TestSupport {

    val app: JibitoApplication get() = ApplicationProvider.getApplicationContext()

    /** اجازه‌های پیامک و نوتیفیکیشن (نوتیفیکیشن از اندروید ۱۳ اجازه‌ی جدا دارد) */
    fun permissions(vararg extra: String): GrantPermissionRule {
        val list = buildList {
            addAll(extra)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return GrantPermissionRule.grant(*list.toTypedArray())
    }

    /** تا [timeoutMillis] صبر می‌کند تا [check] چیزی غیر null بدهد */
    suspend fun <T : Any> waitFor(timeoutMillis: Long = 15_000, check: suspend () -> T?): T? =
        withTimeoutOrNull(timeoutMillis) {
            var value = check()
            while (value == null) {
                delay(200)
                value = check()
            }
            value
        }

    /** برداشت پیامکیِ تازه و بی‌دسته، مثل چیزی که همگام‌سازی پیامک می‌سازد */
    fun smsWithdrawal(smsId: Long, amountRial: Long, merchant: String?) = TransactionFlowEntity(
        smsId = smsId,
        bankId = 1,
        flowType = FlowType.WITHDRAWAL.code,
        amount = amountRial,
        remainAfter = null,
        dateEpoch = System.currentTimeMillis(),
        merchant = merchant,
        suggestedCategory = null,
        isFailedPurchase = false,
        categoryId = null,
        description = null,
        smsContent = "test",
        source = SOURCE_SMS_AUTO,
    )

    /**
     * PDU پیامک دریافتی (SMS-DELIVER) با متن UCS-2، همان شکلی که مودم به اندروید می‌دهد؛
     * برای صدا زدن گیرنده‌ی پیامک با Intent واقعی SMS_RECEIVED. متن حداکثر ۷۰ حرف.
     */
    fun deliverPdu(sender: String, body: String, timeMillis: Long = System.currentTimeMillis()): ByteArray {
        val digits = sender.filter(Char::isDigit)
        val text = body.toByteArray(Charsets.UTF_16BE)
        require(text.size <= 140) { "body too long for one SMS" }
        val out = ByteArrayOutputStream()
        out.write(0x00) // بدون شماره‌ی مرکز پیام
        out.write(0x04) // SMS-DELIVER
        out.write(digits.length)
        out.write(0x81) // نوع شماره: نامعلوم/ملی
        out.write(semiOctets(digits))
        out.write(0x00) // PID
        out.write(0x08) // DCS: UCS-2
        out.write(timestamp(timeMillis))
        out.write(text.size)
        out.write(text)
        return out.toByteArray()
    }

    /** رقم‌ها دوتا دوتا و برعکس (BCD)، با F برای رقم فرد آخر */
    private fun semiOctets(digits: String): ByteArray {
        val padded = if (digits.length % 2 == 1) digits + "F" else digits
        return ByteArray(padded.length / 2) { i ->
            val low = padded[2 * i].digitToInt(16)
            val high = padded[2 * i + 1].digitToInt(16)
            ((high shl 4) or low).toByte()
        }
    }

    private fun timestamp(millis: Long): ByteArray {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        val parts = intArrayOf(
            c.get(Calendar.YEAR) % 100, c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND), 0,
        )
        return semiOctets(parts.joinToString("") { "%02d".format(it) })
    }
}
