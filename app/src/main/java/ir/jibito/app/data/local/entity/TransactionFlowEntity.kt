package ir.jibito.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * یک تراکنش. طبق سند معماری بخش ۴، با این تفاوت‌ها (چون هنوز «حساب» نداریم):
 * - به‌جای accountId فعلاً bankId نگه می‌داریم؛ ستون accountId بعداً با Migration اضافه می‌شود.
 * - ستون‌های merchant / suggestedCategory / isFailedPurchase برای منطق رمز دوم اضافه شده‌اند.
 *
 * مبلغ‌ها همه Long و به ریال (هرگز Double).
 */
@Entity(
    tableName = "transaction_flows",
    indices = [
        Index(value = ["smsId"], unique = true), // جلوی ثبت تکراری یک پیامک
        Index("dateEpoch"),
        Index("categoryId"),
        Index("bankId"),
        Index("merchant"), // برای یادگیری دسته از روی طرف حساب (از نسخه‌ی ۵)
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class TransactionFlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** شناسه‌ی پیامک در گوشی؛ برای تراکنش دستی null است. */
    val smsId: Long?,
    val bankId: Int?,
    /** ۱=واریز، ۲=برداشت */
    val flowType: Int,
    val amount: Long,
    val remainAfter: Long?,
    val dateEpoch: Long,
    val merchant: String?,
    val suggestedCategory: String?,
    val isFailedPurchase: Boolean,
    /** دسته‌ای که خود کاربر انتخاب کرده؛ با اسکن دوباره‌ی پیامک‌ها پاک نمی‌شود. */
    val categoryId: Long?,
    val description: String?,
    val smsContent: String?,
    /** SMS_AUTO یا MANUAL */
    val source: String,
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    /** زمان نشان دادن نوتیفیکیشن «دسته‌اش چیه؟» — تا دوبار نشان داده نشود. (از نسخه‌ی ۲ دیتابیس) */
    @ColumnInfo(defaultValue = "NULL") val notifiedAt: Long? = null,
    /** true یعنی دسته را اپ خودش از روی انتخاب‌های قبلی کاربر گذاشته (از نسخه‌ی ۵ دیتابیس) */
    @ColumnInfo(defaultValue = "0") val isAutoCategorized: Boolean = false,
    /**
     * انتقال بین حساب‌های خود کاربر (از نسخه‌ی ۸ دیتابیس):
     * ۰ = عادی، ۱ = انتقال به خودم (نه خرج حساب می‌شود نه درآمد)، ۲ = کاربر گفته «انتقال به خودم نیست» (دیگر پیشنهاد نشود)
     */
    @ColumnInfo(defaultValue = "0") val transferState: Int = TRANSFER_NONE,
    /** طرف دیگرِ انتقال (برداشت ↔ واریز)، اگر جفتش پیدا شده باشد */
    @ColumnInfo(defaultValue = "NULL") val transferPairId: Long? = null,
) {
    companion object {
        const val TRANSFER_NONE = 0
        const val TRANSFER_SELF = 1
        const val TRANSFER_REJECTED = 2
    }
}

/** تراکنش + اسم و آیکون دسته‌اش (برای نمایش). */
data class TransactionWithCategory(
    @Embedded val flow: TransactionFlowEntity,
    @ColumnInfo(name = "categoryName") val categoryName: String?,
    @ColumnInfo(name = "categoryIcon") val categoryIcon: String?,
)

/** آخرین «مانده»ی گزارش‌شده در پیامک‌های یک بانک */
data class BankBalanceRow(
    val bankId: Int,
    val remainAfter: Long,
    val dateEpoch: Long,
)

/** فقط ستون‌هایی که موقع همگام‌سازی با پیامک‌ها لازم داریم. */
data class SmsFlowKey(
    val id: Long,
    val smsId: Long?,
    val categoryId: Long?,
    val isDeleted: Boolean,
    val notifiedAt: Long?,
    val isAutoCategorized: Boolean,
    val source: String,
    val dateEpoch: Long,
    val transferState: Int,
    val transferPairId: Long?,
)

/** برای پیدا کردن ردیف قبلیِ یک پیامک از روی زمان و متنش */
data class SmsContentKey(
    val id: Long,
    val dateEpoch: Long,
    val smsContent: String,
)
