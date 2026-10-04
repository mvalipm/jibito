package ir.jibito.app.ui.transfers

import ir.jibito.app.data.bank.Bank
import ir.jibito.app.domain.TransferSuggestion

/**
 * یک «الگو» در صفحه‌ی مرور انتقال‌ها: همه‌ی پیشنهادهایی که از یک بانک به یک بانک‌اند (مثلاً ملی ← ملی).
 * کاربر معمولاً برای کل الگو یک جواب دارد، پس یک‌جا تأیید یا رد می‌شود.
 */
data class TransferGroup(
    val key: String,
    /** بانک برداشت؛ null یعنی نامعلوم */
    val from: Bank?,
    /** بانک واریز؛ null یعنی نامعلوم */
    val to: Bank?,
    /** تازه‌ترها اول */
    val items: List<TransferSuggestion>,
) {
    val totalRial: Long get() = items.sumOf { it.withdrawal.transaction.amountRial }
}

/** پیشنهادها بر اساس «بانک برداشت ← بانک واریز»؛ پرتعدادترین الگو اول (هم‌تعدادها: تازه‌ترین اول) */
fun groupTransfers(suggestions: List<TransferSuggestion>): List<TransferGroup> =
    suggestions
        .groupBy { "${it.withdrawal.bank?.id}>${it.deposit.bank?.id}" }
        .map { (key, items) ->
            val sorted = items.sortedByDescending { it.withdrawal.dateMillis }
            TransferGroup(key, sorted.first().withdrawal.bank, sorted.first().deposit.bank, sorted)
        }
        .sortedWith(compareByDescending<TransferGroup> { it.items.size }.thenByDescending { it.items.first().withdrawal.dateMillis })
