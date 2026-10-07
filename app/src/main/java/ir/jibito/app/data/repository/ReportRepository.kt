package ir.jibito.app.data.repository

import ir.jibito.app.data.category.NatureBreakdown
import ir.jibito.app.data.category.NatureReport
import ir.jibito.app.data.category.SpendRollup
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.util.JalaliMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** داده‌ی تب «گزارش‌ها» برای یک ماه */
data class MonthReport(val curve: SpendCurve, val insights: List<Insight>)

/** داده‌ی تب «گزارش‌ها»: منحنی خرج ماه و نکته‌های «جیبی چی فهمید؟» */
class ReportRepository(private val db: AppDatabase) {

    private val dao = db.summaryDao()

    fun observeMonth(month: JalaliMonth): Flow<MonthReport> {
        // ماه قبل (برای مقایسه و الگوی پیش‌بینی) و ۱۲ هفته‌ی اخیر (برای الگوی روزهای هفته)
        val from = minOf(
            month.plus(-1).startMillis(),
            month.endMillis() - ReportInsights.WEEKDAY_WINDOW_DAYS * 24L * 60 * 60 * 1000,
        )
        return combine(
            dao.observeAmounts(FlowType.WITHDRAWAL.code, from, month.endMillis()),
            db.categoryDao().observeAll(),
            dao.observeOverallBudget(),
        ) { rows, categories, overall ->
            val now = System.currentTimeMillis()
            val (oneOffs, spends) = SpendRollup.spendsOnly(rows, categories).partition { it.isOneOff }
            MonthReport(
                curve = SpendCurve.compute(
                    spends.map { it.dateEpoch to it.amount }, month, now, overall?.monthlyLimitRial,
                    oneOffs = oneOffs.map { it.dateEpoch to it.amount },
                ),
                insights = ReportInsights.compute(rows, categories, month, now),
            )
        }.flowOn(Dispatchers.Default)
    }

    /**
     * «خرجت چه‌جور بود؟» (NatureReport): برای این ماه تا امروز در برابر همین موقعِ ماه قبل؛
     * برای [months] > ۱، همان تعداد ماهِ تمام‌شده‌ی قبل از [month] (بدون مقایسه).
     */
    fun observeNature(month: JalaliMonth, months: Int): Flow<NatureBreakdown> {
        val current = months <= 1
        val from = if (current) month.plus(-1).startMillis() else month.plus(-months).startMillis()
        val to = if (current) month.endMillis() else month.startMillis()
        return combine(
            dao.observeAmounts(FlowType.WITHDRAWAL.code, from, to),
            db.categoryDao().observeAll(),
        ) { rows, categories ->
            if (!current) return@combine NatureReport.compute(rows, categories, from, to)
            val now = System.currentTimeMillis()
            val start = month.startMillis()
            val end = now.coerceIn(start, month.endMillis())
            val previous = month.plus(-1)
            val prevEnd = (previous.startMillis() + (end - start)).coerceAtMost(previous.endMillis())
            NatureReport.compute(rows, categories, start, month.endMillis(), previous.startMillis(), prevEnd)
        }.flowOn(Dispatchers.Default)
    }
}
