package ir.jibito.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * پرداخت تکراری ماهانه (اجاره، قسط، شهریه...) برای یادآوری. (از نسخه‌ی ۱۱ دیتابیس)
 * فقط یادآوری است و تراکنشی ثبت نمی‌کند؛ خود پرداخت معمولاً با پیامک بانک ثبت می‌شود (وگرنه دوبار حساب می‌شد).
 */
@Entity(tableName = "recurring_payments")
data class RecurringPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountRial: Long,
    /** روز ماه شمسی (۱ تا ۳۱)؛ در ماه‌های کوتاه‌تر، روز آخر ماه */
    val dayOfMonth: Int,
    /** ماهی که یادآوری‌اش داده شد، مثلاً 140507 (تا در هر ماه فقط یک بار بیاید) */
    val lastRemindedMonthKey: Int?,
    val createdAt: Long,
)
