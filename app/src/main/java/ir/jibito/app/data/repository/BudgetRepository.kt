package ir.jibito.app.data.repository

import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.BudgetEntity
import ir.jibito.app.data.local.entity.CategorySum
import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.category.SpendRollup
import ir.jibito.app.data.local.entity.OverallBudgetEntity
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.util.JalaliMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn

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
    /** پس‌انداز و قرض دادن: از حساب رفته ولی خرج حساب نمی‌شود */
    val excludedRial: Long = 0,
) {
    /** جمع بودجه‌ی دسته‌ها (برای پیشنهاد بودجه‌ی کل) */
    val categoryBudgetsSumRial: Long get() = categories.sumOf { it.budgetRial ?: 0L }

    /** باقی‌مانده از بودجه‌ی کل (منفی یعنی بیشتر از بودجه خرج شده) */
    val overallRemainingRial: Long? get() = overallBudgetRial?.let { it - totalSpentRial }

    /**
     * «روزی چقدر می‌تونی خرج کنی تا آخر ماه»: باقی‌مانده تقسیم بر روزهای باقی‌مانده (با امروز).
     * فقط برای ماه جاری و وقتی هنوز چیزی مانده.
     */
    /** چه کسری از ماه گذشته (۰ تا ۱)؛ فقط برای ماه جاری، وگرنه null */
    fun timeFraction(nowMillis: Long = System.currentTimeMillis()): Float? {
        val start = month.startMillis()
        val end = month.endMillis()
        if (nowMillis < start || nowMillis >= end) return null
        return ((nowMillis - start).toDouble() / (end - start)).toFloat()
    }

    /** چه کسری از بودجه‌ی کل خرج شده (می‌تواند بیشتر از ۱ باشد)؛ بدون بودجه null */
    fun spentFraction(): Float? = overallBudgetRial?.takeIf { it > 0 }?.let { (totalSpentRial.toDouble() / it).toFloat() }

    /**
     * سرعت خرج نسبت به زمان: مثبت یعنی تندتر از گذشت ماه خرج شده (مثلاً ۰٫۰۸ = ۸٪ جلوتر از زمان)،
     * منفی یعنی آهسته‌تر (خوب). فقط ماه جاری با بودجه‌ی کل.
     */
    fun paceDelta(nowMillis: Long = System.currentTimeMillis()): Float? {
        val t = timeFraction(nowMillis) ?: return null
        val s = spentFraction() ?: return null
        return s - t
    }

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
    /** خرج هر زیردسته (لایه‌ی ۲، با جزئیاتش)؛ name = null یعنی مستقیم روی خود دسته‌ی اصلی. بیشترین اول. */
    val children: List<SubSpend> = emptyList(),
)

data class SubSpend(val name: String?, val spentRial: Long)

/** چند ماه در نمودار روند */
const val TREND_MONTHS = 6

interface BudgetRepository {
    fun observeMonth(month: JalaliMonth): Flow<MonthSummary>

    /** خرج چند ماهِ منتهی به month (و مقایسه با همین موقعِ ماه قبل، اگر month ماه جاری باشد) */
    fun observeTrend(month: JalaliMonth, months: Int = TREND_MONTHS): Flow<SpendTrend>

    /** بودجه‌ی ماهانه‌ی یک دسته؛ null یا صفر = حذف بودجه. */
    suspend fun setBudget(categoryId: Long, monthlyLimitRial: Long?)

    /** بودجه‌ی کل ماه؛ null یا صفر = حذف. */
    suspend fun setOverallBudget(monthlyLimitRial: Long?)
}

class BudgetRepositoryImpl(
    private val db: AppDatabase,
    /** بعد از تغییر بودجه صدا زده می‌شود (برای بررسی هشدار) */
    private val onBudgetsChanged: suspend () -> Unit,
) : BudgetRepository {

    private val dao = db.summaryDao()

    override fun observeMonth(month: JalaliMonth): Flow<MonthSummary> {
        val from = month.startMillis()
        val to = month.endMillis()
        return combine(
            dao.observeSums(FlowType.WITHDRAWAL.code, from, to),
            dao.observeSums(FlowType.DEPOSIT.code, from, to),
            db.categoryDao().observeAll(),
            dao.observeBudgets(),
            dao.observeOverallBudget(),
        ) { spendSums, incomeSums, categories, budgets, overall ->
            buildSummary(month, spendSums, incomeSums, categories, budgets.associate { it.categoryId to it.monthlyLimitRial }, overall?.monthlyLimitRial)
        }
    }

    override fun observeTrend(month: JalaliMonth, months: Int): Flow<SpendTrend> {
        val count = months.coerceAtLeast(2)
        val from = month.plus(-(count - 1)).startMillis()
        return combine(
            dao.observeAmounts(FlowType.WITHDRAWAL.code, from, month.endMillis()),
            db.categoryDao().observeAll(),
        ) { rows, categories ->
            val (oneOffs, spends) = SpendRollup.spendsOnly(rows, categories).partition { it.isOneOff }
            SpendTrend.compute(
                spends.map { it.dateEpoch to it.amount }, month, count, System.currentTimeMillis(),
                oneOffs = oneOffs.map { it.dateEpoch to it.amount },
            )
        }.flowOn(Dispatchers.Default)
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

    override suspend fun setOverallBudget(monthlyLimitRial: Long?) {
        if (monthlyLimitRial == null || monthlyLimitRial <= 0) {
            dao.deleteOverallBudget()
        } else {
            // هشدار قبلی پاک می‌شود تا با سقف جدید دوباره بررسی شود
            dao.upsertOverallBudget(OverallBudgetEntity(monthlyLimitRial = monthlyLimitRial))
        }
        onBudgetsChanged()
    }

    companion object {
        /**
         * خلاصه‌ی ماه از روی جمع هر دسته: خرج‌ها روی «دسته‌ی اصلی» جمع می‌شوند (SpendRollup).
         * درآمدها یک لایه‌اند.
         */
        fun buildSummary(
            month: JalaliMonth,
            spendSums: List<CategorySum>,
            incomeSums: List<CategorySum>,
            categories: List<CategoryEntity>,
            budgets: Map<Long, Long>,
            overallBudgetRial: Long?,
        ): MonthSummary {
            val expenseCategories = categories.filter { it.flowType == FlowType.WITHDRAWAL.code }
            val spend = SpendRollup.rollup(expenseCategories, spendSums)
            val children = SpendRollup.byChild(expenseCategories, spendSums)
            val income = SpendRollup.rollup(categories.filter { it.flowType == FlowType.DEPOSIT.code }, incomeSums)

            // دسته‌های اصلی خرج: فعال‌ها همیشه (برای تعیین بودجه)؛ به ترتیب: بیشترین خرج، بعد ترتیب پیش‌فرض
            val expenseRoots = categories
                .filter { it.flowType == FlowType.WITHDRAWAL.code && it.parentId == null && !it.isArchived && it.countsAsSpend }
                .map { root ->
                    val spendPair = root.toSpend(spend.byRoot[root.id] ?: 0L, budgets[root.id])
                    spendPair.first.copy(
                        children = children[root.id].orEmpty()
                            .map { (child, amount) -> SubSpend(child?.name, amount) }
                            .sortedByDescending { it.spentRial },
                    ) to spendPair.second
                }
                .sortedWith(compareByDescending<Pair<CategorySpend, Int>> { it.first.spentRial }.thenBy { it.second })
                .map { it.first }

            val incomeRoots = categories
                .filter { it.flowType == FlowType.DEPOSIT.code && it.parentId == null && !it.isArchived }
                .map { it.toSpend(income.byRoot[it.id] ?: 0L, null) }
                .filter { it.first.spentRial > 0 }
                .sortedByDescending { it.first.spentRial }
                .map { it.first }

            return MonthSummary(
                month = month,
                totalSpentRial = spend.total,
                totalIncomeRial = income.total + income.excluded,
                uncategorizedRial = spend.uncategorized,
                categories = expenseRoots,
                incomeCategories = incomeRoots,
                uncategorizedIncomeRial = income.uncategorized,
                overallBudgetRial = overallBudgetRial,
                excludedRial = spend.excluded,
            )
        }

        private fun CategoryEntity.toSpend(spent: Long, budget: Long?) =
            CategorySpend(id, name, icon, colorHex, spent, budget) to sortOrder
    }
}
