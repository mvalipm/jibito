package ir.jibito.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * دسته‌بندی تراکنش. طبق سند معماری بخش ۴.
 * - از نسخه‌ی ۴ دیتابیس: نوع دارد — «خرج» (برای برداشت‌ها) یا «درآمد» (برای واریزها).
 * - از نسخه‌ی ۱۰: درختی است (دسته‌ی اصلی ← زیردسته ← جزئیات)، با parentId.
 *   بودجه فقط روی دسته‌های اصلی (parentId = null) است.
 */
@Entity(
    tableName = "categories",
    indices = [
        Index("parentId"),
        Index(value = ["code"], unique = true),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val colorHex: String? = null,
    val isArchived: Boolean = false,
    /** ۲ = دسته‌ی خرج (برداشت)، ۱ = دسته‌ی درآمد (واریز) — همان کدهای FlowType */
    @ColumnInfo(defaultValue = "2") val flowType: Int = 2,
    /** دسته‌ی بالاتر؛ null یعنی دسته‌ی اصلی (از نسخه‌ی ۱۰) */
    @ColumnInfo(defaultValue = "NULL") val parentId: Long? = null,
    /** ترتیب نمایش (از نسخه‌ی ۱۰) */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    /** دسته‌ای که خود کاربر ساخته (از نسخه‌ی ۱۰) */
    @ColumnInfo(defaultValue = "0") val isCustom: Boolean = false,
    /** false یعنی خرج حساب نمی‌شود (پس‌انداز، قرض دادن) — از نسخه‌ی ۱۰ */
    @ColumnInfo(defaultValue = "1") val countsAsSpend: Boolean = true,
    /** شناسه‌ی ثابت دسته‌های پیش‌فرض، مثلاً «food.market»؛ برای دسته‌های کاربر null (از نسخه‌ی ۱۰) */
    @ColumnInfo(defaultValue = "NULL") val code: String? = null,
    /**
     * ماهیت خرج که خود کاربر انتخاب کرده (از نسخه‌ی ۱۶): ۱ = اجباری، ۲ = ضروری، ۳ = دلخواه؛
     * ۰ یعنی پیش‌فرض (از روی code یا دسته‌ی بالاتر، SpendNature).
     */
    @ColumnInfo(defaultValue = "0") val nature: Int = 0,
)
