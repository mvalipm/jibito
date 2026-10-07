package ir.jibito.app

import android.app.Notification
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.jibito.app.TestSupport.app
import ir.jibito.app.data.category.CategorySeeder
import ir.jibito.app.notify.NotificationStyle
import ir.jibito.app.notify.NotificationStyleSettings
import ir.jibito.app.notify.TransactionNotifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * دکمه‌های نوتیفیکیشن «این خرج مال چی بود؟»: لمس دکمه‌ی دسته (همان PendingIntent واقعی) دسته را ثبت می‌کند
 * و «برگردون» روی نوتیفیکیشن بعدی آن را برمی‌دارد.
 */
@RunWith(AndroidJUnit4::class)
class NotificationActionTest {

    @get:Rule
    val permissions = TestSupport.permissions()

    private val db get() = app.container.database
    private val manager get() = app.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() = runBlocking {
        TestSupport.stopBackgroundWork()
        CategorySeeder(db).ensure()
        NotificationStyleSettings(app).set(NotificationStyle.BUTTONS)
    }

    private fun notificationFor(transactionId: Long): Notification? =
        manager.activeNotifications.firstOrNull { it.id == TransactionNotifier.notificationId(transactionId) }?.notification

    @Test
    fun categoryButtonSetsCategoryAndUndoClearsIt() = runBlocking {
        val id = db.transactionFlowDao().insert(TestSupport.smsWithdrawal(smsId = 900_001, amountRial = 1_850_000, merchant = "کافه آزمایشی"))
        TransactionNotifier(app, db).processRecent()

        val question = TestSupport.waitFor { notificationFor(id) }
        assertNotNull("برای برداشت بی‌دسته باید نوتیفیکیشن آمده باشد", question)
        val actions = question!!.actions.orEmpty()
        assertEquals("حالت «دکمه‌ها»: سه دکمه‌ی دسته", NotificationStyle.BUTTONS.categoryButtons, actions.size)

        // لمس اولین دکمه، با مکثی مثل کاربر واقعی: اندروید به‌روزرسانیِ خیلی سریعِ پشت‌سرهمِ نوتیفیکیشن
        // را دور می‌ریزد («Shedding events»)، و «رفت تو …» بی‌صدا گم می‌شد
        delay(HUMAN_PAUSE_MILLIS)
        val picked = actions.first()
        picked.actionIntent.send()
        val categoryId = TestSupport.waitFor { db.transactionFlowDao().byId(id)?.categoryId }
        assertNotNull("لمس دکمه باید دسته ثبت کند", categoryId)
        assertEquals("دسته‌ی همان دکمه باید ثبت شود", picked.title.toString(), db.categoryDao().byId(categoryId!!)?.name)

        // نوتیفیکیشن «رفت تو …» با «برگردون»
        val undoLabel = app.getString(R.string.undo)
        val pickedNotice = TestSupport.waitFor {
            notificationFor(id)?.takeIf { n -> n.actions.orEmpty().any { it.title.toString() == undoLabel } }
        }
        assertNotNull("بعد از انتخاب، نوتیفیکیشن «رفت تو …» با «برگردون» باید بیاید", pickedNotice)
        delay(HUMAN_PAUSE_MILLIS)
        pickedNotice!!.actions.first { it.title.toString() == undoLabel }.actionIntent.send()

        val cleared = TestSupport.waitFor { db.transactionFlowDao().byId(id)?.takeIf { it.categoryId == null } }
        assertNotNull("«برگردون» باید دسته را بردارد", cleared)
        assertNull(cleared!!.categoryId)
    }

    private companion object {
        /** فاصله‌ی دو به‌روزرسانی نوتیفیکیشن، کمتر از آن را اندروید ممکن است دور بریزد (سقف حدود ۵ در ثانیه) */
        const val HUMAN_PAUSE_MILLIS = 1_500L
    }
}
