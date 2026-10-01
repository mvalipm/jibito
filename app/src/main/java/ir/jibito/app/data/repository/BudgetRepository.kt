package ir.jibito.app.data.repository

import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.BudgetEntity
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
    val categories: List<CategorySpend>,
)

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
            dao.observeCategorySpend(from, to),
        ) { spent, income, rows ->
            val categorized = rows.sumOf { it.spentRial }
            MonthSummary(
                month = month,
                totalSpentRial = spent,
                totalIncomeRial = income,
                uncategorizedRial = (spent - categorized).coerceAtLeast(0),
                categories = rows.map {
                    CategorySpend(it.categoryId, it.name, it.icon, it.colorHex, it.spentRial, it.budgetRial)
                },
            )
        }
    }

    override suspend fun setBudget(categoryId: Long, monthlyLimitRial: Long?) {
        if (monthlyLimitRial == null || monthlyLimitRial <= 0) {
            dao.deleteBudget(categoryId)
        } else {
            // هشدارهای قبلی پاک می‌شوند تا با سقف جدید دوباره بررسی شود
            dao.upsertBudget(BudgetEntity(categoryId = categoryId, monthlyLimitRial = monthlyLimitRial))
        }
        onBudgetsChanged()
    }
}
