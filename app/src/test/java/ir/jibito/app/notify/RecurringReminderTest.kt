package ir.jibito.app.notify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.RecurringPaymentEntity
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.util.Jalali
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * یادآوری پرداخت ماهانه وقتی همان مبلغ از قبل برداشت شده، نمی‌آید (و آن ماه علامت می‌خورد).
 * در Robolectric اجازه‌ی نوتیفیکیشن (اندروید ۱۳+) داده نشده، پس یادآوری‌ای که باید بیاید
 * نشان داده نمی‌شود و علامت هم نمی‌خورد (دفعه‌ی بعد دوباره امتحان می‌شود).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecurringReminderTest {

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

    /** تاریخ شمسی ← میلی‌ثانیه به وقت گوشی */
    private fun j(y: Int, m: Int, d: Int, h: Int): Long {
        val (gy, gm, gd) = Jalali.toGregorian(y, m, d)
        return Calendar.getInstance().apply { clear(); set(gy, gm - 1, gd, h, 0) }.timeInMillis
    }

    private fun payment(amount: Long) = RecurringPaymentEntity(
        title = "شارژ ساختمون", amountRial = amount, dayOfMonth = 11, lastRemindedMonthKey = null, createdAt = 0,
    )

    private fun withdrawal(amount: Long, date: Long) = TransactionFlowEntity(
        smsId = date, bankId = 12, flowType = 2, amount = amount, remainAfter = null, dateEpoch = date,
        merchant = null, suggestedCategory = null, isFailedPurchase = false, categoryId = null,
        description = null, smsContent = "sms", source = "SMS_AUTO",
    )

    @Test
    fun alreadyPaidSkipsTheReminder() = runBlocking {
        db.recurringDao().insert(payment(5_000_000))
        // سه روز زودتر، با کارمزد کارت‌به‌کارت
        db.transactionFlowDao().insert(withdrawal(5_025_000, j(1405, 7, 8, 14)))

        RecurringReminder(context, db).check(now = j(1405, 7, 11, 10))

        assertEquals(140507, db.recurringDao().all().single().lastRemindedMonthKey)
    }

    @Test
    fun notPaidStillNeedsTheReminder() = runBlocking {
        db.recurringDao().insert(payment(5_000_000))
        // مبلغ دیگر، و برداشت خیلی قدیمی‌تر از پنجره
        db.transactionFlowDao().insert(withdrawal(1_200_000, j(1405, 7, 9, 14)))
        db.transactionFlowDao().insert(withdrawal(5_000_000, j(1405, 6, 15, 14)))

        RecurringReminder(context, db).check(now = j(1405, 7, 11, 10))

        assertNull(db.recurringDao().all().single().lastRemindedMonthKey)
    }
}
