package ir.jibito.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.local.migration.Migrations
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * همه‌ی Migration ها، از هر نسخه‌ی قدیمی تا نسخه‌ی فعلی (سند معماری بخش ۵).
 *
 * دیتابیس نسخه‌ی قدیمی دقیقاً از روی فایل JSON همان نسخه در app/schemas ساخته می‌شود، چند ردیف داده در آن
 * گذاشته می‌شود، بعد Room با Migration ها بازش می‌کند. Room خودش ساختار نهایی را با نسخه‌ی فعلی مقایسه می‌کند
 * و اگر یک ستون یا ایندکس جا افتاده باشد، خطا می‌دهد. در آخر بررسی می‌شود که داده‌ی کاربر سالم مانده باشد.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val schemaDir = File(System.getProperty("room.schemaDir") ?: "schemas", AppDatabase::class.java.name)

    @Before
    fun clean() {
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun everySchemaFileExists() {
        for (v in 1..AppDatabase.VERSION) {
            assertTrue("schema $v.json is missing", File(schemaDir, "$v.json").exists())
        }
    }

    @Test fun migrateFrom1() = migrateFrom(1)
    @Test fun migrateFrom2() = migrateFrom(2)
    @Test fun migrateFrom3() = migrateFrom(3)
    @Test fun migrateFrom4() = migrateFrom(4)
    @Test fun migrateFrom5() = migrateFrom(5)
    @Test fun migrateFrom6() = migrateFrom(6)
    @Test fun migrateFrom7() = migrateFrom(7)
    @Test fun migrateFrom8() = migrateFrom(8)
    @Test fun migrateFrom9() = migrateFrom(9)
    @Test fun migrateFrom10() = migrateFrom(10)

    @Test
    fun recurringPaymentsTableUsableAfter10To11() {
        createAt(10)
        openWithRoom().use { db ->
            kotlinx.coroutines.runBlocking {
                db.recurringDao().insert(
                    ir.jibito.app.data.local.entity.RecurringPaymentEntity(
                        title = "اجاره", amountRial = 150_000_000, dayOfMonth = 5, lastRemindedMonthKey = null, createdAt = 1,
                    )
                )
                assertEquals(listOf("اجاره"), db.recurringDao().all().map { it.title })
            }
        }
    }

    @Test
    fun incomeCategoriesAddedFrom3To4() {
        createAt(3)
        openWithRoom().use { db ->
            val count = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM categories WHERE flowType = 1").use {
                it.moveToFirst(); it.getInt(0)
            }
            assertEquals(AppDatabase.INCOME_CATEGORIES.size, count)
            // دسته‌ی قبلی کاربر «خرج» مانده است
            val oldType = db.openHelper.readableDatabase.query("SELECT flowType FROM categories WHERE name = 'غذای تست'").use {
                it.moveToFirst(); it.getInt(0)
            }
            assertEquals(2, oldType)
        }
    }

    private fun migrateFrom(version: Int) {
        createAt(version)
        openWithRoom().use { db ->
            val sql = db.openHelper.readableDatabase
            sql.query("SELECT amount, merchant, categoryId, smsContent, source FROM transaction_flows WHERE smsId = 77").use {
                assertTrue("transaction lost when migrating from $version", it.moveToFirst())
                assertEquals(1_250_000L, it.getLong(0))
                assertEquals("اسنپ", it.getString(1))
                assertEquals(CATEGORY_ID, it.getLong(2))
                assertEquals("برداشت ۱۲۵۰۰۰۰", it.getString(3))
                assertEquals("SMS_AUTO", it.getString(4))
            }
            sql.query("SELECT name FROM categories WHERE id = $CATEGORY_ID").use {
                assertTrue("category lost when migrating from $version", it.moveToFirst())
                assertEquals("غذای تست", it.getString(0))
            }
            if (version >= 3) {
                sql.query("SELECT monthlyLimitRial FROM budgets WHERE categoryId = $CATEGORY_ID").use {
                    assertTrue("budget lost when migrating from $version", it.moveToFirst())
                    assertEquals(5_000_000L, it.getLong(0))
                }
            }
            assertEquals(AppDatabase.VERSION, sql.version)
        }
    }

    /** دیتابیس را دقیقاً به شکل نسخه‌ی [version] می‌سازد (مثل گوشی کاربری که هنوز آن نسخه را دارد) و داده می‌گذارد. */
    private fun createAt(version: Int) {
        val schema = JSONObject(File(schemaDir, "$version.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))

            db.execSQL("INSERT INTO categories (id, name, icon, colorHex, isArchived) VALUES ($CATEGORY_ID, 'غذای تست', '🍔', '#E4572E', 0)")
            db.execSQL(
                "INSERT INTO transaction_flows (smsId, bankId, flowType, amount, remainAfter, dateEpoch, merchant, " +
                    "suggestedCategory, isFailedPurchase, categoryId, description, smsContent, source, isDeleted, updatedAt) " +
                    "VALUES (77, 1, 2, 1250000, 9000000, 1700000000000, 'اسنپ', NULL, 0, $CATEGORY_ID, NULL, " +
                    "'برداشت ۱۲۵۰۰۰۰', 'SMS_AUTO', 0, 1700000000000)"
            )
            if (version >= 3) {
                db.execSQL("INSERT INTO budgets (categoryId, monthlyLimitRial, alertedMonthKey, alertedLevel) VALUES ($CATEGORY_ID, 5000000, NULL, 0)")
            }
            db.version = version
        } finally {
            db.close()
        }
    }

    /** همان کاری که اپ موقع باز شدن می‌کند: Room + همه‌ی Migration ها (و اعتبارسنجی ساختار توسط خود Room) */
    private fun openWithRoom(): AppDatabase {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        db.openHelper.writableDatabase // باز کردن واقعی ← اجرای Migration ها و مقایسه‌ی ساختار
        return db
    }

    private inline fun <T> AppDatabase.use(block: (AppDatabase) -> T): T =
        try { block(this) } finally { close() }

    private companion object {
        const val DB_NAME = "migration-test.db"
        const val CATEGORY_ID = 500L
    }
}
