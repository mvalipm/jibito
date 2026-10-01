package ir.jibito.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ir.jibito.app.data.local.dao.CategoryDao
import ir.jibito.app.data.local.dao.ReviewDao
import ir.jibito.app.data.local.dao.SummaryDao
import ir.jibito.app.data.local.entity.ReviewSmsEntity
import ir.jibito.app.data.local.entity.SenderRuleEntity
import ir.jibito.app.data.local.entity.SmsTemplateEntity
import ir.jibito.app.data.local.entity.OwnAccountEntity
import ir.jibito.app.data.local.entity.BudgetEntity
import ir.jibito.app.data.local.dao.TransactionFlowDao
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.local.migration.Migrations

/**
 * دیتابیس اصلی اپ (روی خود گوشی).
 *
 * قانون‌های سند معماری بخش ۵ از همین الان:
 * - exportSchema = true ← فایل JSON هر نسخه در پوشه‌ی app/schemas ذخیره و در گیت نگه داشته می‌شود.
 * - هر تغییر ساختار جدول‌ها = بالا بردن version + یک Migration جدا و تست‌شده.
 * - هرگز fallbackToDestructiveMigration (یعنی پاک کردن دیتای کاربر).
 */
@Database(
    entities = [
        TransactionFlowEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        ReviewSmsEntity::class,
        SenderRuleEntity::class,
        SmsTemplateEntity::class,
        OwnAccountEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionFlowDao(): TransactionFlowDao
    abstract fun categoryDao(): CategoryDao
    abstract fun summaryDao(): SummaryDao
    abstract fun reviewDao(): ReviewDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "jibito.db")
                .addMigrations(*Migrations.ALL) // هر نسخه‌ی جدید فقط این‌جا اضافه می‌شود
                .build()

        /** دسته‌های پیش‌فرض؛ اسم‌ها با پیشنهادهای CategorySuggester یکی‌اند. */
        // «get()» یعنی هر بار ساخته می‌شود؛ تا ترتیب تعریف با INCOME_CATEGORIES (پایین‌تر) مشکلی نسازد
        val DEFAULT_CATEGORIES: List<CategoryEntity>
            get() = listOf(
            CategoryEntity(name = "غذا", icon = "🍔", colorHex = "#E4572E"),
            CategoryEntity(name = "سوپرمارکت", icon = "🛒", colorHex = "#F2A541"),
            CategoryEntity(name = "رفت‌وآمد", icon = "🚕", colorHex = "#17BEBB"),
            CategoryEntity(name = "سوخت", icon = "⛽", colorHex = "#8D6A9F"),
            CategoryEntity(name = "خرید", icon = "🛍", colorHex = "#C73E8B"),
            CategoryEntity(name = "قبض و شارژ", icon = "💡", colorHex = "#3F88C5"),
            CategoryEntity(name = "سرگرمی", icon = "🎬", colorHex = "#7FB069"),
            CategoryEntity(name = "سفر", icon = "✈", colorHex = "#2E86AB"),
            CategoryEntity(name = "درمان", icon = "💊", colorHex = "#D1495B"),
            CategoryEntity(name = "سایر", icon = "•", colorHex = "#8C8C8C"),
        ) + INCOME_CATEGORIES

        /** دسته‌های واریز (درآمد). در Migration_3_4 هم همین‌ها برای کاربرهای قبلی اضافه می‌شوند. */
        val INCOME_CATEGORIES = listOf(
            CategoryEntity(name = "حقوق", icon = "💼", colorHex = "#1E9E6A", flowType = 1),
            CategoryEntity(name = "حاصل فروش محصول", icon = "🏷", colorHex = "#2E86AB", flowType = 1),
            CategoryEntity(name = "قرض گرفتم", icon = "🤝", colorHex = "#8D6A9F", flowType = 1),
            CategoryEntity(name = "طلبم رو گرفتم", icon = "↩", colorHex = "#17BEBB", flowType = 1),
            CategoryEntity(name = "سود بانکی", icon = "🏦", colorHex = "#F2A541", flowType = 1),
            CategoryEntity(name = "هدیه", icon = "🎁", colorHex = "#C73E8B", flowType = 1),
            CategoryEntity(name = "سایر درآمد", icon = "•", colorHex = "#8C8C8C", flowType = 1),
        )
    }
}
