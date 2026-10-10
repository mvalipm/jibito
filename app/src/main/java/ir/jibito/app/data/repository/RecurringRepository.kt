package ir.jibito.app.data.repository

import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.RecurringPaymentEntity
import ir.jibito.app.notify.RecurringSchedule
import ir.jibito.app.util.Jalali
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/** پرداخت‌های تکراری (فقط برای یادآوری) */
class RecurringRepository(private val db: AppDatabase) {

    fun observeAll(): Flow<List<RecurringPaymentEntity>> = db.recurringDao().observeAll()

    /** شناسه‌ی پرداختِ ساخته‌شده (برای «برگردون») */
    suspend fun add(title: String, amountRial: Long, dayOfMonth: Int, now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val (y, m, d) = Jalali.fromGregorian(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        return db.recurringDao().insert(
            RecurringPaymentEntity(
                title = title.trim().take(MAX_TITLE),
                amountRial = amountRial,
                dayOfMonth = dayOfMonth.coerceIn(1, 31),
                lastRemindedMonthKey = RecurringSchedule.initialRemindedKey(dayOfMonth, y, m, d),
                createdAt = now,
            )
        )
    }

    suspend fun delete(id: Long) = db.recurringDao().delete(id)

    companion object {
        const val MAX_TITLE = 40
    }
}
