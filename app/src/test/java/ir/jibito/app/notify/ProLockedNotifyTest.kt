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
import ir.jibito.app.data.pro.ProSettings
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
 * بعد از ماه رایگان (و بدون خرید): پیامک بانکی نه «مال چی بود؟» می‌آورد نه «✓ خودکار»؛
 * به‌جایش حداکثر روزی یک نوتیف «ثبتش کردم؛ جیبیتوی کامل…». در ماه رایگان همه‌چیز مثل قبل است.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProLockedNotifyTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val context: Context get() = app
    private lateinit var db: AppDatabase
    private val dao get() = db.transactionFlowDao()
    private val manager get() = shadowOf(context.getSystemService(NotificationManager::class.java))
    private val day = 24 * 60 * 60 * 1000L

    @Before
    fun setUp() {
        context.deleteDatabase(AppDatabase.NAME)
        db = AppDatabase.build(context)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences(ProSettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(AppDatabase.NAME)
        context.getSharedPreferences(ProSettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** ماه رایگان [daysAgo] روز پیش شروع شده */
    private fun trialStarted(daysAgo: Int) {
        context.getSharedPreferences(ProSettings.PREFS, Context.MODE_PRIVATE).edit()
            .putLong(ProSettings.KEY_TRIAL_START, System.currentTimeMillis() - daysAgo * day)
            .commit()
    }

    private fun withdrawal(smsId: Long, categoryId: Long? = null, auto: Boolean = false) = TransactionFlowEntity(
        smsId = smsId,
        bankId = 1,
        flowType = FlowType.WITHDRAWAL.code,
        amount = 1_850_000,
        remainAfter = null,
        dateEpoch = System.currentTimeMillis(),
        merchant = "کافه آزمایشی",
        suggestedCategory = null,
        isFailedPurchase = false,
        categoryId = categoryId,
        description = null,
        smsContent = "test",
        source = SOURCE_SMS_AUTO,
        isAutoCategorized = auto,
    )

    @Test
    fun lockedSendsInviteInsteadOfQuestion() = runBlocking {
        trialStarted(daysAgo = 40)
        val id = dao.insert(withdrawal(1))
        TransactionNotifier(context, db).processRecent()

        assertNull("بعد از ماه رایگان «مال چی بود؟» نمی‌آید", manager.getNotification(TransactionNotifier.notificationId(id)))
        assertNotNull(manager.getNotification(TransactionNotifier.INVITE_NOTIFICATION_ID))
        assertNotNull("دوباره بررسی نشود", dao.byId(id)?.notifiedAt)
    }

    @Test
    fun inviteAtMostOncePerDay() = runBlocking {
        trialStarted(daysAgo = 40)
        dao.insert(withdrawal(1))
        TransactionNotifier(context, db).processRecent()
        context.getSystemService(NotificationManager::class.java).cancel(TransactionNotifier.INVITE_NOTIFICATION_ID)

        val second = dao.insert(withdrawal(2))
        TransactionNotifier(context, db).processRecent()
        assertNull("همان روز دوباره دعوت نمی‌شود", manager.getNotification(TransactionNotifier.INVITE_NOTIFICATION_ID))
        assertNull(manager.getNotification(TransactionNotifier.notificationId(second)))
        assertNotNull(dao.byId(second)?.notifiedAt)
    }

    @Test
    fun lockedAutoCategorizedGetsNoConfirmation() = runBlocking {
        trialStarted(daysAgo = 40)
        CategorySeeder(db).ensure()
        val category = db.categoryDao().all().first().id
        val id = dao.insert(withdrawal(1, categoryId = category, auto = true))
        TransactionNotifier(context, db).processRecent()

        assertNull("«✓ خودکار» هم بعد از ماه رایگان نمی‌آید", manager.getNotification(TransactionNotifier.notificationId(id)))
        assertNotNull(manager.getNotification(TransactionNotifier.INVITE_NOTIFICATION_ID))
    }

    @Test
    fun trialStillAsks() = runBlocking {
        trialStarted(daysAgo = 3)
        val id = dao.insert(withdrawal(1))
        TransactionNotifier(context, db).processRecent()

        assertNotNull(manager.getNotification(TransactionNotifier.notificationId(id)))
        assertNull(manager.getNotification(TransactionNotifier.INVITE_NOTIFICATION_ID))
        assertEquals(1, manager.allNotifications.size)
    }
}
