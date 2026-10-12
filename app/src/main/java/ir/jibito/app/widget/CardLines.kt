package ir.jibito.app.widget

import ir.jibito.app.data.wallet.AccountBalance
import ir.jibito.app.data.wallet.AccountGrouping
import ir.jibito.app.data.wallet.AccountLink
import ir.jibito.app.data.wallet.AccountRef
import ir.jibito.app.data.wallet.AccountUsageRow
import ir.jibito.app.data.wallet.BalanceHistory
import ir.jibito.app.data.wallet.BalancePointRow
import ir.jibito.app.data.wallet.WalletExclusion

/** یک ردیف ویجت «موجودی کارت‌ها» */
internal data class CardLine(
    val bankId: Int,
    /** نماینده‌ی گروه؛ null یعنی پیامک‌های این بانک شماره حساب ندارند */
    val account: String?,
    /** اسمی که کاربر گذاشته */
    val name: String?,
    val balanceRial: Long,
    /** زمان آخرین پیامکِ مانده‌دار */
    val dateMillis: Long,
    /** مانده‌ی پایان هر روزِ نمودار (قدیمی‌ترین اول) */
    val trend: List<Long>,
    /** تغییر مانده از اول امروز؛ null یعنی امروز تغییری دیده نشده */
    val todayDeltaRial: Long? = null,
) {
    /** همان کلید WalletExclusion و صفحه‌ی جزئیات حساب */
    val key: String get() = WalletExclusion.key(bankId, account)
}

/**
 * «کارت‌های پرمصرف»: کدام حساب‌ها در ویجت بیایند و روند چندروزه‌ی هرکدام.
 * به اندروید وابسته نیست (تست روی JVM).
 */
internal object CardLines {

    /** مانده‌ای که از این قدیمی‌تر باشد، کم‌رنگ نشان داده می‌شود (مثل کیف پول اپ) */
    const val STALE_DAYS = 30
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /**
     * حساب‌هایی که در جمع کیف پول‌اند ([excluded] نه)، به ترتیب تعداد تراکنش ([usage])، بعد تازگی مانده.
     * @param days شروع هر روز نمودار (قدیمی‌ترین اول، آخری امروز)
     * @param end پایان امروز
     */
    fun pick(
        balances: List<AccountBalance>,
        usage: List<AccountUsageRow>,
        points: List<BalancePointRow>,
        links: List<AccountLink>,
        excluded: Set<String>,
        days: List<Long>,
        end: Long,
        limit: Int,
    ): List<CardLine> {
        val series = BalanceHistory.byAccount(points, links)
        return balances
            .filterNot { WalletExclusion.isExcluded(it.bankId, it.account, excluded) }
            .sortedWith(compareByDescending<AccountBalance> { usageOf(it, usage, links) }.thenByDescending { it.dateMillis })
            .take(limit)
            .map { b ->
                val own = series[AccountRef(b.bankId, b.account)].orEmpty()
                CardLine(
                    bankId = b.bankId,
                    account = b.account,
                    name = b.name,
                    balanceRial = b.balanceRial,
                    dateMillis = b.dateMillis,
                    trend = trend(own, days, end, b.balanceRial),
                    todayDeltaRial = todayDelta(own, days.last()),
                )
            }
    }

    fun isStale(line: CardLine, now: Long): Boolean = now - line.dateMillis > STALE_DAYS * DAY_MILLIS

    /** تعداد تراکنش‌های یک حساب (یا کل بانک، اگر پیامک‌هایش شماره حساب ندارند) */
    private fun usageOf(b: AccountBalance, usage: List<AccountUsageRow>, links: List<AccountLink>): Int =
        usage.filter { u ->
            u.bankId == b.bankId &&
                (b.account == null || (u.account != null && AccountGrouping.repOf(u.bankId, u.account, links) == b.account))
        }.sumOf { it.count }

    /** مانده‌ی پیش از یک پیامک: واریز (۱) از مانده کم، برداشت اضافه می‌شود */
    private fun before(p: BalancePointRow): Long = if (p.flowType == 1) p.remainAfter - p.amount else p.remainAfter + p.amount

    /** مانده‌ی پایان هر روز؛ پیش از اولین پیامکِ این بازه، مانده‌ی پیش از همان پیامک؛ بدون پیامک، همان مانده‌ی فعلی */
    private fun trend(own: List<BalancePointRow>, days: List<Long>, end: Long, current: Long): List<Long> {
        if (own.isEmpty()) return days.map { current }
        return days.indices.map { i ->
            val dayEnd = days.getOrNull(i + 1) ?: end
            own.lastOrNull { it.dateEpoch < dayEnd }?.remainAfter ?: before(own.first())
        }
    }

    private fun todayDelta(own: List<BalancePointRow>, todayStart: Long): Long? {
        val today = own.filter { it.dateEpoch >= todayStart }
        if (today.isEmpty()) return null
        val start = own.lastOrNull { it.dateEpoch < todayStart }?.remainAfter ?: before(today.first())
        return (today.last().remainAfter - start).takeIf { it != 0L }
    }
}
