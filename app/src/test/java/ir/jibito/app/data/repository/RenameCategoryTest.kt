package ir.jibito.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.category.CreateCategoryResult
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.sms.SmsReader
import ir.jibito.app.data.sms.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** عوض کردن اسم دسته‌ی شخصی از تنظیمات: همان قانون‌های ساختن، و فقط برای دسته‌های خود کاربر */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RenameCategoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepositoryImpl

    @Before
    fun setUp() {
        context.deleteDatabase(AppDatabase.NAME)
        db = AppDatabase.build(context)
        repo = TransactionRepositoryImpl(db, SmsReader(context), SyncState(context))
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(AppDatabase.NAME)
    }

    private suspend fun create(name: String): Long =
        (repo.createCategory(name, null, 2, "⭐") as CreateCategoryResult.Created).id

    @Test
    fun renamesCustomCategoryWithCleanedName() = runBlocking {
        val id = create("باشگاه شنا")
        val result = repo.renameCustomCategory(id, "  استخر   محله ")
        assertEquals(CreateCategoryResult.Created(id), result)
        assertEquals("استخر محله", db.categoryDao().byId(id)!!.name)
    }

    @Test
    fun rejectsEmptyAndDuplicateNames() = runBlocking {
        val id = create("باشگاه شنا")
        create("سفر شمال")
        assertEquals(
            CreateCategoryResult.Invalid(CreateCategoryResult.Reason.DUPLICATE),
            repo.renameCustomCategory(id, "سفر  شمال"),
        )
        assertEquals(
            CreateCategoryResult.Invalid(CreateCategoryResult.Reason.EMPTY),
            repo.renameCustomCategory(id, "   "),
        )
        assertEquals("باشگاه شنا", db.categoryDao().byId(id)!!.name)
    }

    @Test
    fun subcategoryKeepsChosenIconOrInheritsParents() = runBlocking {
        val parent = create("باشگاه شنا")
        val withIcon = (repo.createCategory("عینک شنا", parent, 2, "🎯") as CreateCategoryResult.Created).id
        val without = (repo.createCategory("بلیت استخر", parent, 2, null) as CreateCategoryResult.Created).id
        assertEquals("🎯", db.categoryDao().byId(withIcon)!!.icon)
        assertEquals(null, db.categoryDao().byId(without)!!.icon)
    }

    @Test
    fun keepingTheSameNameIsAllowed() = runBlocking {
        val id = create("باشگاه شنا")
        assertTrue(repo.renameCustomCategory(id, "باشگاه شنا") is CreateCategoryResult.Created)
    }

    @Test
    fun builtInCategoriesAreNotRenamed() = runBlocking {
        val id = db.categoryDao().insert(CategoryEntity(name = "کافه‌ی تست", icon = "☕", colorHex = "#000000", flowType = 2))
        repo.renameCustomCategory(id, "اسم دیگر")
        assertEquals("کافه‌ی تست", db.categoryDao().byId(id)!!.name)
    }
}
