package ir.jibito.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * «صندوق بررسی»: پیامکی که شبیه تراکنش است ولی خودکار خوانده نشد. (از نسخه‌ی ۶ دیتابیس)
 * تا کاربر تأیید نکند، در هیچ جمع و بودجه‌ای حساب نمی‌شود.
 */
@Entity(tableName = "review_sms")
data class ReviewSmsEntity(
    @PrimaryKey val smsId: Long,
    val sender: String,
    val body: String,
    val dateEpoch: Long,
    /** اگر فرستنده بانک شناخته‌شده باشد؛ برای فرستنده‌ی ناشناس null */
    val bankId: Int?,
    /** ۰ = منتظر بررسی، ۱ = ثبت شد (تراکنش است)، ۲ = تراکنش نیست */
    @ColumnInfo(defaultValue = "0") val status: Int = STATUS_PENDING,
    /** زمانی که صفحه‌ی بررسی خودکار (موقع باز شدن اپ) برای این پیامک نشان داده شد */
    val autoShownAt: Long? = null,
    val resolvedAt: Long? = null,
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_CONFIRMED = 1
        const val STATUS_DISMISSED = 2
    }
}

/**
 * قانون‌های کاربر برای یک فرستنده. (از نسخه‌ی ۶ دیتابیس)
 * فعلاً: «این فرستنده را دیگر نشان نده». بعداً: «این سرشماره مال فلان بانک است».
 */
@Entity(tableName = "sender_rules")
data class SenderRuleEntity(
    /** سرشماره‌ی نرمال‌شده (BankDirectory.normalizeSender) */
    @PrimaryKey val sender: String,
    /** ۱ = نادیده بگیر، ۲ = این یک بانک است */
    val action: Int,
    val bankId: Int?,
    val createdAt: Long,
) {
    companion object {
        const val ACTION_IGNORE = 1
        const val ACTION_BANK = 2
    }
}
