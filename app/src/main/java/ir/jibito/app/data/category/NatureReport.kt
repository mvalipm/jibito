package ir.jibito.app.data.category

import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.data.local.entity.DatedAmount
import kotlin.math.roundToInt

/** خرج یک دسته (همان دسته‌ای که به تراکنش داده شده، نه دسته‌ی اصلی‌اش) در یک ماهیت */
data class NatureItem(val categoryId: Long, val amountRial: Long)

/**
 * «خرجت چه‌جور بود؟» در یک بازه. کلید null یعنی «هنوز معلوم نیست» (بی‌دسته، یا دسته‌ی شخصیِ بی‌ماهیت).
 * @param previous همان سهم‌ها در همین موقعِ ماه قبل؛ null برای بازه‌های چندماهه
 */
data class NatureBreakdown(
    val totals: Map<Nature?, Long>,
    /** دسته‌های هر ماهیت، پرخرج‌ترین اول */
    val items: Map<Nature?, List<NatureItem>>,
    val previous: Map<Nature?, Long>?,
) {
    val totalRial: Long get() = totals.values.sum()

    fun amount(nature: Nature?): Long = totals[nature] ?: 0L

    /** درصد گردشده از کل (بدون یک‌باره‌ها و بیرون از خرج) */
    fun percent(nature: Nature?): Int = if (totalRial <= 0) 0 else (amount(nature) * 100.0 / totalRial).roundToInt()

    /**
     * تغییر یک ماهیت نسبت به همین موقعِ ماه قبل (درصد، مثبت = بیشتر)؛
     * null وقتی پایه‌ی مقایسه کوچک است یا تغییر ارزش گفتن ندارد.
     */
    fun change(nature: Nature): Int? {
        val base = previous?.get(nature) ?: return null
        if (base < NatureReport.MIN_BASE_RIAL) return null
        val delta = amount(nature) - base
        val percent = (delta * 100.0 / base).roundToInt()
        return percent.takeIf { kotlin.math.abs(delta) >= NatureReport.MIN_DELTA_RIAL && kotlin.math.abs(percent) >= NatureReport.MIN_PERCENT }
    }
}

object NatureReport {

    /** همان آستانه‌های «جیبی چی فهمید؟»: کمتر از ۱۰۰ هزار تومان پایه نیست، کمتر از ۲۰۰ هزار تومان یا ۱۵٪ گفتن ندارد */
    const val MIN_BASE_RIAL = 1_000_000L
    const val MIN_DELTA_RIAL = 2_000_000L
    const val MIN_PERCENT = 15

    /**
     * @param rows برداشت‌ها (هر تراکنش جدا)
     * @param from شروع بازه؛ [to] پایانش (خودش جزو بازه نیست)
     * @param previousFrom شروع همان بازه در ماه قبل برای مقایسه؛ null یعنی مقایسه‌ای نیست
     */
    fun compute(
        rows: List<DatedAmount>,
        categories: List<CategoryEntity>,
        from: Long,
        to: Long,
        previousFrom: Long? = null,
        previousTo: Long? = null,
    ): NatureBreakdown {
        val byId = categories.associateBy { it.id }
        // بیرون از خرج (پس‌انداز، قرض دادن) و خرج یک‌باره (خانه، ماشین) ماهیت روزمره ندارند
        val spends = SpendRollup.spendsOnly(rows, categories).filter { !it.isOneOff }
        fun natureOf(row: DatedAmount): Nature? = row.categoryId?.let { byId[it] }?.let { SpendNature.of(it, byId) }

        val current = spends.filter { it.dateEpoch in from until to }
        val totals = current.groupBy(::natureOf).mapValues { (_, list) -> list.sumOf { it.amount } }
        val items = current.groupBy(::natureOf).mapValues { (_, list) ->
            list.filter { it.categoryId != null }
                .groupBy { it.categoryId!! }
                .map { (id, rows) -> NatureItem(id, rows.sumOf { it.amount }) }
                .sortedByDescending { it.amountRial }
        }
        val previous = if (previousFrom != null && previousTo != null) {
            spends.filter { it.dateEpoch in previousFrom until previousTo }
                .groupBy(::natureOf)
                .mapValues { (_, list) -> list.sumOf { it.amount } }
        } else null
        return NatureBreakdown(totals, items, previous)
    }
}
