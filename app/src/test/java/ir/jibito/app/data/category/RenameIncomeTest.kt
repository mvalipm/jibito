package ir.jibito.app.data.category

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** اسم‌های بلند قبلیِ دسته‌های درآمد («حاصل فروش محصول» و …) برای کاربرهای قبلی کوتاه می‌شوند */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RenameIncomeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(AppDatabase.NAME)
        db = AppDatabase.build(context)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(AppDatabase.NAME)
    }

    @Test
    fun renamesOldDefaultIncomeNamesOnly() = runBlocking {
        val dao = db.categoryDao()
        // مثل کاربری که دسته‌های درآمد را از Migration_3_4 با اسم‌های قدیمی دارد
        val sales = dao.insert(CategoryEntity(name = "حاصل فروش محصول", icon = "🏷", flowType = 1))
        val repaid = dao.insert(CategoryEntity(name = "طلبم رو گرفتم", icon = "↩", flowType = 1))
        val other = dao.insert(CategoryEntity(name = "سایر درآمد", icon = "•", flowType = 1))
        // دسته‌ی شخصیِ کاربر با همان اسم قدیمی دست نمی‌خورد
        val custom = dao.insert(CategoryEntity(name = "طلبم رو گرفتم", flowType = 2, isCustom = true))

        CategorySeeder(db).ensure()

        assertEquals("فروش", dao.byId(sales)!!.name)
        assertEquals("طلبم رسید", dao.byId(repaid)!!.name)
        assertEquals("سایر", dao.byId(other)!!.name)
        assertEquals("طلبم رو گرفتم", dao.byId(custom)!!.name)
        val active = dao.all().filter { !it.isArchived }
        assertTrue(active.none { it.flowType == 1 && it.name in Taxonomy.RENAMED_INCOME })
    }

    @Test
    fun freshInstallUsesShortNames() = runBlocking {
        CategorySeeder(db).ensure()
        val income = db.categoryDao().all().filter { it.flowType == 1 }.map { it.name }
        assertTrue("فروش" in income)
        assertTrue(income.none { it in Taxonomy.RENAMED_INCOME })
    }
}
