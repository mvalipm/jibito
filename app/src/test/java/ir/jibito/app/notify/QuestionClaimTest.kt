package ir.jibito.app.notify

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.category.CategorySeeder
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.repository.SOURCE_SMS_AUTO
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * «مال چی بود؟» برای هر تراکنش فقط یک بار پرسیده می‌شود، حتی اگر دو همگام‌سازی هم‌زمان (یا همگام‌سازی و لمس دکمه‌ی
 * نوتیفیکیشن) با فهرست کهنه کار کنند؛ و بعد از دسته گرفتن هرگز. اگر نوتیفیکیشن نشان داده نشد، دفعه‌ی بعد دوباره.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuestionClaimTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val context: Context get() = app
    private lateinit var db: AppDatabase
    private val dao get() = db.transactionFlowDao()

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

    private fun withdrawal(smsId: Long) = TransactionFlowEntity(
        smsId = smsId,
        bankId = 1,
        flowType = FlowType.WITHDRAWAL.code,
        amount = 1_850_000,
        remainAfter = null,
        dateEpoch = System.currentTimeMillis(),
        merchant = "کافه آزمایشی",
        suggestedCategory = null,
        isFailedPurchase = false,
        categoryId = null,
        description = null,
        smsContent = "test",
        source = SOURCE_SMS_AUTO,
    )

    @Test
    fun onlyTheFirstClaimAsks() = runBlocking {
        val id = dao.insert(withdrawal(1))
        assertEquals(1, dao.claimQuestion(id, 10))
        // همگام‌سازی دوم با فهرست کهنه: دیگر نمی‌پرسد
        assertEquals(0, dao.claimQuestion(id, 20))
        assertEquals(10L, dao.byId(id)?.notifiedAt)
    }

    @Test
    fun categorizedTransactionIsNeverAsked() = runBlocking {
        val id = dao.insert(withdrawal(2))
        // لمس دکمه‌ی نوتیفیکیشن (یا انتخاب در اپ) پیش از رسیدن همگام‌سازی کهنه
        CategorySeeder(db).ensure()
        dao.setCategory(id, db.categoryDao().all().first().id, 5)
        assertEquals(0, dao.claimQuestion(id, 10))
        assertNull(dao.byId(id)?.notifiedAt)
    }

    @Test
    fun unshownQuestionIsTriedAgainLater() = runBlocking {
        // بدون اجازه‌ی نوتیفیکیشن (اندروید ۱۳ به بعد) سؤال نشان داده نمی‌شود ← رزرو برمی‌گردد
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val id = dao.insert(withdrawal(3))
        TransactionNotifier(context, db).processRecent()
        assertNull("نشان داده نشد، پس دفعه‌ی بعد باید دوباره امتحان شود", dao.byId(id)?.notifiedAt)

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        TransactionNotifier(context, db).processRecent()
        assertNotNull(dao.byId(id)?.notifiedAt)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(shadowOf(manager).getNotification(TransactionNotifier.notificationId(id)))
    }
}
