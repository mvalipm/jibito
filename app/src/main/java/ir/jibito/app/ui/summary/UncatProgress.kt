package ir.jibito.app.ui.summary

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Transaction
import ir.jibito.app.ui.smslist.uncategorizedBadgeCount
import ir.jibito.app.util.JalaliMonth

/**
 * خرج‌های همین ماه (بدون انتقال به خودم و خرید ناموفق) و چندتایشان هنوز دسته ندارند.
 * @param unseen خرج‌های بی‌دسته‌ی «تازه»: بعد از «فعلاً نه» یا بعد از باز کردن فهرست آمده‌اند (UncategorizedBadgeSettings)؛
 * فقط وقتی صفر نباشد، کارت «خرج بی‌دسته» فوری است.
 */
data class UncatProgress(val open: Int, val total: Int, val unseen: Int) {
    val done: Int get() = total - open

    /** کسری از خرج‌های ماه که دسته گرفته‌اند (۰ تا ۱) */
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

fun uncatProgress(all: List<Transaction>, month: JalaliMonth, badgeEnabled: Boolean, seenUntil: Long): UncatProgress {
    val from = month.startMillis()
    val to = month.endMillis()
    val spends = all.filter {
        it.transaction.type == FlowType.WITHDRAWAL && !it.isSelfTransfer && !it.isFailedPurchase && it.dateMillis in from until to
    }
    return UncatProgress(
        open = spends.count { it.categoryId == null },
        total = spends.size,
        unseen = uncategorizedBadgeCount(all, badgeEnabled, seenUntil),
    )
}
