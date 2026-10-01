package ir.jibito.app.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ir.jibito.app.data.local.AppDatabase

/**
 * همه‌ی Migration های دیتابیس، به ترتیب. (سند معماری بخش ۵)
 * قانون: فقط «اضافه کردن» — هیچ ستونی حذف یا تغییر نام داده نمی‌شود، تا دیتای کاربر سالم بماند.
 */
object Migrations {

    /** نسخه‌ی ۱ ← ۲: ستون notifiedAt برای اینکه نوتیفیکیشن یک تراکنش دوبار نیاید. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transaction_flows ADD COLUMN notifiedAt INTEGER DEFAULT NULL")
        }
    }

    /** نسخه‌ی ۲ ← ۳: جدول تازه‌ی بودجه‌ها. (جدول جدید = اضافه کردن؛ چیزی از قبلی‌ها عوض نمی‌شود) */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `budgets` (" +
                    "`categoryId` INTEGER NOT NULL, " +
                    "`monthlyLimitRial` INTEGER NOT NULL, " +
                    "`alertedMonthKey` INTEGER, " +
                    "`alertedLevel` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`categoryId`), " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
        }
    }

    /**
     * نسخه‌ی ۳ ← ۴: دسته‌ها نوع می‌گیرند (خرج یا درآمد).
     * همه‌ی دسته‌های قبلی «خرج» می‌مانند (پیش‌فرض ۲) و دسته‌های درآمد اضافه می‌شوند.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE categories ADD COLUMN flowType INTEGER NOT NULL DEFAULT 2")
            for (c in AppDatabase.INCOME_CATEGORIES) {
                db.execSQL(
                    "INSERT INTO categories (name, icon, colorHex, isArchived, flowType) VALUES (?, ?, ?, 0, 1)",
                    arrayOf<Any?>(c.name, c.icon, c.colorHex),
                )
            }
        }
    }

    /** نسخه‌ی ۴ ← ۵: «دسته‌ی خودکار» + ایندکس روی طرف حساب برای یادگیری سریع. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transaction_flows ADD COLUMN isAutoCategorized INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_flows_merchant` ON `transaction_flows` (`merchant`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
