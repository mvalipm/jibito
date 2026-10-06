package ir.jibito.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * بودجه‌ی ماهانه‌ی یک دسته. (از نسخه‌ی ۳ دیتابیس)
 * هر دسته حداکثر یک بودجه دارد؛ مبلغ به ریال.
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BudgetEntity(
    @PrimaryKey val categoryId: Long,
    val monthlyLimitRial: Long,
    /** ماهی که آخرین هشدار برایش داده شد، مثلاً 140507 */
    val alertedMonthKey: Int? = null,
    /** آخرین سطح هشدار داده‌شده در آن ماه: ۰ = هیچ، ۸۰ = هشتاد درصد، ۱۰۰ = تمام شد */
    @ColumnInfo(defaultValue = "0") val alertedLevel: Int = 0,
)

/**
 * بودجه‌ی کل ماه (همه‌ی خرج‌ها با هم). (از نسخه‌ی ۹ دیتابیس)
 * فقط یک ردیف دارد (id = 1)؛ مبلغ به ریال.
 */
@Entity(tableName = "overall_budget")
data class OverallBudgetEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val monthlyLimitRial: Long,
    /** ماهی که آخرین هشدار برایش داده شد، مثلاً 140507 */
    val alertedMonthKey: Int? = null,
    /** آخرین سطح هشدار داده‌شده در آن ماه: ۰، ۸۰ یا ۱۰۰ */
    @ColumnInfo(defaultValue = "0") val alertedLevel: Int = 0,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}

/** جمع مبلغ یک دسته در یک بازه (null = بی‌دسته). */
data class CategorySum(
    val categoryId: Long?,
    val totalRial: Long,
    /** چه مقدار از totalRial خرج یک‌باره است (خرید خانه…): جزو جمع هست، ولی از بودجه کم نمی‌شود */
    val oneOffRial: Long = 0,
)

/** مبلغ یک تراکنش با دسته و زمانش (برای روند چندماهه و خلاصه‌ی هفتگی) */
data class DatedAmount(
    val categoryId: Long?,
    val amount: Long,
    val dateEpoch: Long,
    /** خرج یک‌باره (خرید خانه…): در جمع‌ها هست، ولی پایه‌ی میانگین، مقایسه و پیش‌بینی نیست */
    val isOneOff: Boolean = false,
)
