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

    /** نسخه‌ی ۱۰ ← ۱۱: پرداخت‌های تکراری برای یادآوری (فقط جدول تازه) */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recurring_payments` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                    "`amountRial` INTEGER NOT NULL, `dayOfMonth` INTEGER NOT NULL, " +
                    "`lastRemindedMonthKey` INTEGER, `createdAt` INTEGER NOT NULL)"
            )
        }
    }

    /**
     * نسخه‌ی ۱۱ ← ۱۲: زمان انتخاب دسته توسط کاربر (categorizedAt)، تا یادگیری دسته «آخرین نظر» کاربر را
     * از روی زمان انتخاب بشناسد نه تاریخ تراکنش. برای ردیف‌های قبلی خالی می‌ماند.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transaction_flows ADD COLUMN categorizedAt INTEGER DEFAULT NULL")
        }
    }

    /**
     * نسخه‌ی ۱۲ ← ۱۳: چند حساب در یک بانک.
     * ستون account (شماره حسابِ خود پیامک؛ برای ردیف‌های قبلی خالی می‌ماند و در خواندن کامل بعدی پر می‌شود)
     * و جدول account_links (تصمیم‌های کاربر: کدام حساب‌ها یکی‌اند، اسم هر حساب).
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transaction_flows ADD COLUMN account TEXT DEFAULT NULL")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `account_links` (" +
                    "`bankId` INTEGER NOT NULL, `account` TEXT NOT NULL, `groupAccount` TEXT NOT NULL, " +
                    "`name` TEXT, `decided` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`bankId`, `account`))"
            )
        }
    }

    /** نسخه‌ی ۱۳ ← ۱۴: یادداشت خود کاربر برای هر تراکنش (مثلاً از جعبه‌ی «بنویس» نوتیفیکیشن) */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transaction_flows ADD COLUMN note TEXT DEFAULT NULL")
        }
    }

    /** نسخه‌ی ۱۴ ← ۱۵: علامت «خرج یک‌باره» (خرید خانه و مانند آن)؛ ردیف‌های قبلی عادی‌اند */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transaction_flows ADD COLUMN oneOffState INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
        MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
        MIGRATION_14_15,
    )
}
