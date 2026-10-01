package ir.jibito.app.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
