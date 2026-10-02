package ir.jibito.app.data.repository

import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.BudgetEntity
import ir.jibito.app.data.local.entity.CategorySpendRow
import ir.jibito.app.data.local.entity.OverallBudgetEntity
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.util.JalaliMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** خلاصه‌ی یک ماه، آماده‌ی نمایش. همه‌ی مبلغ‌ها ریال. */
data class MonthSummary(
    val month: JalaliMonth,
    val totalSpentRial: Long,
    val totalIncomeRial: Long,
    /** خرجی که هنوز دسته ندارد */
    val uncategorizedRial: Long,
    /** دسته‌های خرج (با بودجه) */
    val categories: List<CategorySpend>,
    /** دسته‌های درآمد؛ فقط آن‌هایی که این ماه مبلغی دارند */
    val incomeCategories: List<CategorySpend>,
    /** درآمدی که هنوز دسته ندارد */
    val uncategorizedIncomeRial: Long,
    /** بودجه‌ی کل ماه؛ null یعنی تعیین نشده */
    val overallBudgetRial: Long? = null,
) {
    /** جمع بودجه‌ی دسته‌ها (برای پیشنهاد بودجه‌ی کل) */
    val categoryBudgetsSumRial: Long get() = categories.sumOf { it.budgetRial ?: 0L }

    /** باقی‌مانده از بودجه‌ی کل (منفی یعنی بیشتر از بودجه خرج شده) */
    val overallRemainingRial: Long? get() = overallBudgetRial?.let { it - totalSpentRial }

    /**
     * «روزی چقدر می‌تونی خرج کنی تا آخر ماه»: باقی‌مانده تقسیم بر روزهای باقی‌مانده (با امروز).
     * فقط برای ماه جاری و وقتی هنوز چیزی مانده.
     */
    fun dailyAllowanceRial(nowMillis: Long = System.currentTimeMillis()): Long? {
        val remaining = overallRemainingRial ?: return null
        if (remaining <= 0) return null
        val end = month.endMillis()
        if (nowMillis < month.startMillis() || nowMillis >= end) return null
        val dayMillis = 24L * 60 * 60 * 1000
        val daysLeft = ((end - nowMillis + dayMillis - 1) / dayMillis).coerceAtLeast(1)
        return remaining / daysLeft
    }
}

data class CategorySpend(
    val categoryId: Long,
    val name: String,
    val icon: String?,
    val colorHex: String?,
    val spentRial: Long,
    val budgetRial: Long?,
)

interface BudgetRepository {
    fun observeMonth(month: JalaliMonth): Flow<MonthSummary>

    /** بودجه‌ی ماهانه‌ی یک دسته؛ null یا صفر = حذف بودجه. */
    suspend fun setBudget(categoryId: Long, monthlyLimitRial: Long?)

    /** بودجه‌ی کل ماه؛ null یا صفر = حذف. */
    suspend fun setOverallBudget(monthlyLimitRial: Long?)
}

class BudgetRepositoryImpl(
    db: AppDatabase,
    /** بعد از تغییر بودجه صدا زده می‌شود (برای بررسی هشدار) */
    private val onBudgetsChanged: suspend () -> Unit,
) : BudgetRepository {

    private val dao = db.summaryDao()

    override fun observeMonth(month: JalaliMonth): Flow<MonthSummary> {
        val from = month.startMillis()
        val to = month.endMillis()
        return combine(
            dao.observeTotal(FlowType.WITHDRAWAL.code, from, to),
            dao.observeTotal(FlowType.DEPOSIT.code, from, to),
            dao.observeCategorySpend(FlowType.WITHDRAWAL.code, from, to),
            dao.observeCategorySpend(FlowType.DEPOSIT.code, from, to),
            dao.observeOverallBudget(),
        ) { spent, income, expenseRows, incomeRows, overall ->
            MonthSummary(
                month = month,
                totalSpentRial = spent,
                totalIncomeRial = income,
                uncategorizedRial = (spent - expenseRows.sumOf { it.spentRial }).coerceAtLeast(0),
                categories = expenseRows.map { it.toSpend() },
                incomeCategories = incomeRows.filter { it.spentRial > 0 }.map { it.toSpend() },
                uncategorizedIncomeRial = (income - incomeRows.sumOf { it.spentRial }).coerceAtLeast(0),
                overallBudgetRial = overall?.monthlyLimitRial,
            )
        }
    }

    private fun CategorySpendRow.toSpend() =
        CategorySpend(categoryId, name, icon, colorHex, spentRial, budgetRial)

    override suspend fun setBudget(categoryId: Long, monthlyLimitRial: Long?) {
        if (monthlyLimitRial == null || monthlyLimitRial <= 0) {
            dao.deleteBudget(categoryId)
        } else {
            // هشدارهای قبلی پاک می‌شوند تا با سقف جدید دوباره بررسی شود
            dao.upsertBudget(BudgetEntity(categoryId = categoryId, monthlyLimitRial = monthlyLimitRial))
        }
        onBudgetsChanged()
    }

    override suspend fun setOverallBudget(monthlyLimitRial: Long?) {
        if (monthlyLimitRial == null || monthlyLimitRial <= 0) {
            dao.deleteOverallBudget()
        } else {
            // هشدار قبلی پاک می‌شود تا با سقف جدید دوباره بررسی شود
            dao.upsertOverallBudget(OverallBudgetEntity(monthlyLimitRial = monthlyLimitRial))
        }
        onBudgetsChanged()
    }
}
