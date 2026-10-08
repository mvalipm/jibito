package ir.jibito.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ir.jibito.app.data.local.dao.CategoryDao
import ir.jibito.app.data.local.dao.RecurringDao
import ir.jibito.app.data.local.dao.ReviewDao
import ir.jibito.app.data.local.dao.SummaryDao
import ir.jibito.app.data.local.entity.ReviewSmsEntity
import ir.jibito.app.data.local.entity.SenderRuleEntity
import ir.jibito.app.data.local.entity.SmsTemplateEntity
import ir.jibito.app.data.local.entity.OwnAccountEntity
import ir.jibito.app.data.local.entity.OverallBudgetEntity
import ir.jibito.app.data.local.entity.RecurringPaymentEntity
import ir.jibito.app.data.local.entity.BudgetEntity
import ir.jibito.app.data.local.entity.AccountLinkEntity
import ir.jibito.app.data.local.entity.MerchantRuleEntity
import ir.jibito.app.data.local.dao.AccountDao
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
        OverallBudgetEntity::class,
        RecurringPaymentEntity::class,
        AccountLinkEntity::class,
        MerchantRuleEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionFlowDao(): TransactionFlowDao
    abstract fun categoryDao(): CategoryDao
    abstract fun summaryDao(): SummaryDao
    abstract fun reviewDao(): ReviewDao
    abstract fun recurringDao(): RecurringDao
    abstract fun accountDao(): AccountDao

    companion object {
        /** نسخه‌ی فعلی ساختار دیتابیس (برای Migration ها، پشتیبان‌گیری و تست‌ها) */
        const val VERSION = 17

        /** اسم فایل دیتابیس روی گوشی */
        const val NAME = "jibito.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL) // هر نسخه‌ی جدید فقط این‌جا اضافه می‌شود
                .build()

        /** دسته‌های واریز (درآمد). در Migration_3_4 هم همین‌ها برای کاربرهای قبلی اضافه می‌شوند. */
        val INCOME_CATEGORIES = listOf(
            CategoryEntity(name = "حقوق", icon = "💼", colorHex = "#1E9E6A", flowType = 1),
            CategoryEntity(name = "فروش", icon = "🏷", colorHex = "#2E86AB", flowType = 1),
            CategoryEntity(name = "قرض گرفتم", icon = "🤝", colorHex = "#8D6A9F", flowType = 1),
            CategoryEntity(name = "طلبم رسید", icon = "↩", colorHex = "#17BEBB", flowType = 1),
            CategoryEntity(name = "سود بانکی", icon = "🏦", colorHex = "#F2A541", flowType = 1),
            CategoryEntity(name = "هدیه", icon = "🎁", colorHex = "#C73E8B", flowType = 1),
            CategoryEntity(name = "سایر", icon = "•", colorHex = "#8C8C8C", flowType = 1),
        )
    }
}
