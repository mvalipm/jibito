package ir.jibito.app.ui.welcome

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.JalaliMonth

/**
 * «۳۴۲ تراکنش از ۶ ماه گذشته، توی ۳ بانک؛ بیشترین خرج: سوپرمارکت» برای صفحه‌ی اولین آشنایی.
 * @param topCategory دسته‌ای که بیشترین خرج را دارد (دسته‌ی ثبت‌شده یا حدس اپ)؛ null اگر هنوز هیچ‌کدام نیست
 */
data class RevealStats(
    val count: Int,
    val months: Int,
    val banks: Int,
    val topCategory: String? = null,
    /** سهم [topCategory] از کل خرج‌ها (درصد)؛ null اگر دسته‌ای نیست */
    val topSharePercent: Int? = null,
) {
    companion object {
        fun of(transactions: List<Transaction>): RevealStats {
            val spends = transactions
                .filter { it.transaction.type == FlowType.WITHDRAWAL && !it.isFailedPurchase && !it.isSelfTransfer }
            val byCategory = spends
                .mapNotNull { t -> (t.categoryName ?: t.suggestedCategory)?.let { it to t.transaction.amountRial } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, amounts) -> amounts.sum() }
            val top = byCategory.maxByOrNull { it.value }
            val total = spends.sumOf { it.transaction.amountRial }
            return RevealStats(
                count = transactions.size,
                months = transactions.map { JalaliMonth.of(it.dateMillis).key }.distinct().size,
                banks = transactions.mapNotNull { it.bank?.id }.distinct().size,
                topCategory = top?.key,
                topSharePercent = top?.takeIf { total > 0 }?.let { (it.value * 100 / total).toInt() },
            )
        }
    }
}
