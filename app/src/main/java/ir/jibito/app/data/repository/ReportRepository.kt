package ir.jibito.app.data.repository

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
            val excluded = BudgetRepositoryImpl.excludedFromSpend(categories)
            val spends = rows.filter { it.categoryId == null || it.categoryId !in excluded }.map { it.dateEpoch to it.amount }
            MonthReport(
                curve = SpendCurve.compute(spends, month, now, overall?.monthlyLimitRial),
                insights = ReportInsights.compute(rows, categories, month, now),
            )
        }.flowOn(Dispatchers.Default)
    }
}
