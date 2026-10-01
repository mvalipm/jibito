package ir.jibito.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * دسته‌بندی تراکنش. طبق سند معماری بخش ۴.
 * از نسخه‌ی ۴ دیتابیس هر دسته نوع دارد: دسته‌ی «خرج» (برای برداشت‌ها) یا «درآمد» (برای واریزها).
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val colorHex: String? = null,
    val isArchived: Boolean = false,
    /** ۲ = دسته‌ی خرج (برداشت)، ۱ = دسته‌ی درآمد (واریز) — همان کدهای FlowType */
    @ColumnInfo(defaultValue = "2") val flowType: Int = 2,
)
