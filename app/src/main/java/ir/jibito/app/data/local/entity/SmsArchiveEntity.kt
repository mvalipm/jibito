package ir.jibito.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * کپیِ خودِ پیامک‌های بانکی که تراکنش خوانده شده‌اند (از نسخه‌ی ۱۲ دیتابیس).
 *
 * چرا؟ صندوق پیامک گوشی منبع مطمئنی نیست: کاربر پیامک‌های بانک را پاک می‌کند، اپ پیامک پیامک‌های قدیمی را
 * خودش پاک می‌کند، و در گوشی تازه (یا بعد از بازگردانی پشتیبان) پیامک‌های قبلی اصلاً نیستند.
 * با این جدول، تاریخچه‌ی تراکنش‌ها به صندوق پیامک وابسته نیست، و پارسرهای بهترشده‌ی نسخه‌های بعد
 * باز هم روی پیامک‌های قدیمی اجرا می‌شوند — حتی اگر از گوشی پاک شده باشند.
 *
 * فقط پیامکی که تراکنش (واریز/برداشت) خوانده شده نگه داشته می‌شود؛ رمز یک‌بارمصرف، کد و تبلیغ هرگز.
 * یک پیامک = یک ردیف: (زمان، متن) یکتاست.
 */
@Entity(
    tableName = "sms_archive",
    indices = [
        Index(value = ["dateEpoch", "body"], unique = true),
    ],
)
data class SmsArchiveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** سرشماره‌ی فرستنده، همان‌طور که در گوشی بود؛ برای ردیف‌هایی که از نسخه‌ی ۱۱ منتقل شده‌اند null است */
    val sender: String?,
    /** بانکی که پیامک موقع خواندن به آن نسبت داده شد */
    val bankId: Int?,
    val body: String,
    /** زمان رسیدن پیامک (همان dateEpoch تراکنش) */
    val dateEpoch: Long,
    val archivedAt: Long,
)
