package ir.jibito.app.ui.welcome

import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.JalaliMonth

/** «۳۴۲ تراکنش از ۶ ماه گذشته، در ۳ بانک» برای صفحه‌ی اولین آشنایی */
data class RevealStats(val count: Int, val months: Int, val banks: Int) {
    companion object {
        fun of(transactions: List<Transaction>): RevealStats = RevealStats(
            count = transactions.size,
            months = transactions.map { JalaliMonth.of(it.dateMillis).key }.distinct().size,
            banks = transactions.mapNotNull { it.bank?.id }.distinct().size,
        )
    }
}
