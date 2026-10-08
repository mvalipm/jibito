package ir.jibito.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * «اسم فروشگاه کدومه؟» که کاربر یاد داده (از نسخه‌ی ۱۷ دیتابیس): شکل پیامک (خط‌ها با عددهای پوشیده)
 * و این‌که اسم فروشگاه در خط چندم است. پیامک‌های بعدیِ همان بانک با همین شکل، اسمشان را از همان خط می‌گیرند.
 * فقط داخل گوشی می‌ماند (و در پشتیبان، چون کل دیتابیس پشتیبان گرفته می‌شود).
 */
@Entity(tableName = "merchant_rules", indices = [Index("bankId")])
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankId: Int,
    /** MerchantRules.skeleton: خط‌ها با «#» به‌جای عددها، و خطِ اسم فروشگاه با MerchantRules.MARK */
    val skeleton: String,
    val createdAt: Long,
)
