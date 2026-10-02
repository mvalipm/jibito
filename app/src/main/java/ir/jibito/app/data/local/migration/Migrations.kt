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

    /** نسخه‌ی ۵ ← ۶: «صندوق بررسی» برای پیامک‌های خوانده‌نشده + قانون‌های فرستنده. (فقط جدول تازه) */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `review_sms` (" +
                    "`smsId` INTEGER NOT NULL, `sender` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                    "`dateEpoch` INTEGER NOT NULL, `bankId` INTEGER, `status` INTEGER NOT NULL DEFAULT 0, " +
                    "`autoShownAt` INTEGER, `resolvedAt` INTEGER, PRIMARY KEY(`smsId`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sender_rules` (" +
                    "`sender` TEXT NOT NULL, `action` INTEGER NOT NULL, `bankId` INTEGER, " +
                    "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`sender`))"
            )
        }
    }

    /** نسخه‌ی ۶ ← ۷: قالب‌های یادگرفته‌شده‌ی پیامک. (فقط جدول تازه) */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sms_templates` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sender` TEXT NOT NULL, " +
                    "`skeleton` TEXT NOT NULL, `numberCount` INTEGER NOT NULL, `amountPos` INTEGER NOT NULL, " +
                    "`balancePos` INTEGER, `typeMode` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sms_templates_sender` ON `sms_templates` (`sender`)")
        }
    }

    /** نسخه‌ی ۷ ← ۸: انتقال بین حساب‌های خود کاربر (دو ستون تازه + جدول کارت‌های خودم) */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transaction_flows` ADD COLUMN `transferState` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `transaction_flows` ADD COLUMN `transferPairId` INTEGER DEFAULT NULL")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `own_accounts` (" +
                    "`merchant` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`merchant`))"
            )
        }
    }

    /** نسخه‌ی ۸ ← ۹: بودجه‌ی کل ماه (فقط جدول تازه) */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `overall_budget` (" +
                    "`id` INTEGER NOT NULL, `monthlyLimitRial` INTEGER NOT NULL, `alertedMonthKey` INTEGER, " +
                    "`alertedLevel` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))"
            )
        }
    }

    /**
     * نسخه‌ی ۹ ← ۱۰: دسته‌بندی درختی (دسته‌ی اصلی ← زیردسته ← جزئیات).
     * فقط ستون‌ها این‌جا اضافه می‌شوند؛ ساختن دسته‌های جدید و انتقال دسته‌های قبلی
     * یک بار در CategorySeeder (با کد کاتلین، داخل یک تراکنش) انجام می‌شود.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `parentId` INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `isCustom` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `countsAsSpend` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `code` TEXT DEFAULT NULL")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_parentId` ON `categories` (`parentId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_code` ON `categories` (`code`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
        MIGRATION_8_9, MIGRATION_9_10,
    )
}
