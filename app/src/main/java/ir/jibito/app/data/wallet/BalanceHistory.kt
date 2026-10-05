package ir.jibito.app.data.wallet

import java.util.Calendar

/*
 * «روند موجودی»: مانده‌ی هر حساب در طول زمان، فقط از روی مانده‌ای که خود پیامک‌های بانک نوشته‌اند.
 *
 * مانده فقط در لحظه‌ی هر پیامک معلوم است و بین دو پیامک ثابت فرض می‌شود؛ پس برای هر روز «آخرین مانده تا آخر آن روز»
 * را می‌گیریم (نمودار پله‌ای). قبل از اولین پیامکِ مانده‌دار چیزی نمی‌دانیم و حدس هم نمی‌زنیم (null).
 * حساب‌ها همان گروه‌بندی کیف پول را دارند (AccountGrouping)، و «جمع» همان حساب‌هایی است که در کیف پول جمع می‌شوند.
 *
 * این فایل به اندروید وابسته نیست (تست روی JVM).
 */

/** یک پیامکِ مانده‌دار: مانده‌ی بعد از تراکنش، با خود تراکنش (برای «بعد از واریز بزرگ چه شد؟») */
data class BalancePointRow(
    val id: Long,
    val bankId: Int,
    val account: String?,
    val remainAfter: Long,
    val dateEpoch: Long,
    /** ۱ = واریز، ۲ = برداشت */
    val flowType: Int,
    val amount: Long,
)

/** شماره حسابِ یک تراکنش (برای پیدا کردن تراکنش‌های یک حساب) */
data class FlowAccountRow(val id: Long, val account: String?)

/** یک حساب در کیف پول: بانک + نماینده‌ی گروه (null یعنی پیامک‌های این بانک شماره حساب ندارند) */
data class AccountRef(val bankId: Int, val account: String?) {
    /** همان کلید WalletExclusion (برای مسیر صفحه‌ی جزئیات هم به کار می‌رود) */
    val key: String get() = WalletExclusion.key(bankId, account)

    companion object {
        /** برعکس [key] */
        fun parse(key: String): AccountRef? {
            val cut = key.indexOf(':')
            if (cut <= 0) return null
            val bankId = key.substring(0, cut).toIntOrNull() ?: return null
            return AccountRef(bankId, key.substring(cut + 1).ifEmpty { null })
        }
    }
}

/** روزهای پشت سر هم یک بازه (شروع هر روز به وقت گوشی) */
class DayGrid private constructor(
    /** شروع هر روز */
    val starts: List<Long>,
    /** پایان روز آخر (شروع روز بعدش) */
    val end: Long,
) {
    val size: Int get() = starts.size

    /** پایان روز [i] (شروع روز بعدش) */
    fun endOf(i: Int): Long = starts.getOrNull(i + 1) ?: end

    /** روزی که [millis] در آن است؛ null اگر بیرون از بازه است */
    fun indexOf(millis: Long): Int? {
        if (millis < starts.first() || millis >= end) return null
        val i = starts.binarySearch(millis)
        return if (i >= 0) i else -i - 2
    }

    companion object {
        /** از روزِ [fromMillis] تا روزِ [toMillis] (هر دو با خودشان) */
        fun of(fromMillis: Long, toMillis: Long): DayGrid {
            val cal = Calendar.getInstance().apply {
                timeInMillis = fromMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val starts = mutableListOf<Long>()
            do {
                starts += cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 1)
            } while (cal.timeInMillis <= toMillis)
            return DayGrid(starts, cal.timeInMillis)
        }

        /** [days] روز آخر تا [nowMillis] (امروز هم جزوش است) */
        fun lastDays(days: Int, nowMillis: Long): DayGrid {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_MONTH, -(days - 1))
            }
            return of(cal.timeInMillis, nowMillis)
        }
    }
}

/**
 * مانده‌ی روزبه‌روز یک حساب (یا جمع چند حساب) در یک بازه.
 * @param values برای هر روزِ [grid]: آخرین مانده تا آخر آن روز؛ null یعنی هنوز معلوم نیست (قبل از اولین پیامک)
 */
data class BalanceSeries(val grid: DayGrid, val values: List<Long?>) {

    /** اولین روزی که مانده معلوم است */
    val firstKnown: Int? get() = values.indexOfFirst { it != null }.takeIf { it >= 0 }

    /** مانده‌های معلوم، از [firstKnown] تا آخر (بعد از اولین مقدار، دیگر null ندارد) */
    val known: List<Long> get() = firstKnown?.let { from -> values.drop(from).map { it!! } }.orEmpty()

    /** حداقل دو روز معلوم لازم است تا «روند» معنی داشته باشد */
    val hasTrend: Boolean get() = known.size >= 2

    /** آمار بازه، فقط از روزهای معلوم */
    fun stats(): BalanceStats? {
        val from = firstKnown ?: return null
        val known = known
        val minAt = known.indices.minBy { known[it] }
        val maxAt = known.indices.maxBy { known[it] }
        return BalanceStats(
            startRial = known.first(),
            endRial = known.last(),
            minRial = known[minAt],
            minDay = grid.starts[from + minAt],
            maxRial = known[maxAt],
            maxDay = grid.starts[from + maxAt],
            startDay = grid.starts[from],
        )
    }
}

data class BalanceStats(
    /** مانده‌ی اولین روز معلوم */
    val startRial: Long,
    /** مانده‌ی امروز (آخرین روز بازه) */
    val endRial: Long,
    /** کف بازه و روزش (اولین بار) */
    val minRial: Long,
    val minDay: Long,
    val maxRial: Long,
    val maxDay: Long,
    /** شروع اولین روز معلوم («از ۱۷ تیر») */
    val startDay: Long,
) {
    val changeRial: Long get() = endRial - startRial
}

/**
 * «بعد از واریز بزرگ چه شد؟»: بزرگ‌ترین واریز بازه، و چند روز طول کشید تا نصفش خرج شود
 * (مانده به «مانده‌ی قبل از واریز + نصف واریز» برسد).
 * @param halfSpentDays null یعنی هنوز بیش از نصفش مانده
 */
data class DepositRhythm(
    val amountRial: Long,
    val dateMillis: Long,
    val halfSpentDays: Int?,
    /** چند روز از واریز گذشته (برای «۱۲ روز گذشته و هنوز بیش از نصفش مانده») */
    val daysSince: Int,
)

/** یک حساب با روندش، برای تب «گزارش‌ها» و صفحه‌ی جزئیات */
data class AccountTrend(
    val ref: AccountRef,
    /** اسمی که کاربر گذاشته */
    val name: String?,
    val series: BalanceSeries,
    /** زمان آخرین پیامکِ مانده‌دار (کل تاریخچه، نه فقط این بازه) */
    val lastSmsMillis: Long,
    /** کاربر این حساب را از جمع کیف پول بیرون گذاشته */
    val excluded: Boolean,
)

/** همه‌ی حساب‌ها + جمعشان در یک بازه */
data class BalanceOverview(
    val total: BalanceSeries,
    /** حساب‌ها، تازه‌ترین پیامک اول (مثل کیف پول) */
    val accounts: List<AccountTrend>,
)

object BalanceHistory {

    /** مانده‌ای که این‌قدر کهنه است، «ممکن است به‌روز نباشد» (مثل کیف پول) */
    const val STALE_DAYS = 30

    /** واریز کوچک‌تر از این (۱ میلیون تومان) برای «ریتم بعد از واریز» حساب نمی‌شود */
    const val MIN_DEPOSIT_RIAL = 10_000_000L

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /**
     * پیامک‌ها به تفکیک حساب، با همان قانون کیف پول (AccountGrouping.balances):
     * بانکی که هیچ پیامکش شماره حساب ندارد ← یک حساب برای کل بانک؛
     * بانکی که شماره حساب دارد ← پیامک‌های بی‌شماره کنار می‌روند (معلوم نیست مال کدام حساب‌اند).
     * هر فهرست به ترتیب زمان (و شناسه، برای پیامک‌های هم‌زمان).
     */
    fun byAccount(rows: List<BalancePointRow>, links: List<AccountLink>): Map<AccountRef, List<BalancePointRow>> =
        rows.groupBy { it.bankId }.flatMap { (bankId, bankRows) ->
            val withAccount = bankRows.filter { it.account != null }
            if (withAccount.isEmpty()) {
                listOf(AccountRef(bankId, null) to bankRows)
            } else {
                withAccount
                    .groupBy { AccountGrouping.repOf(bankId, it.account!!, links) }
                    .map { (rep, groupRows) -> AccountRef(bankId, rep) to groupRows }
            }
        }.associate { (ref, points) -> ref to points.sortedWith(compareBy({ it.dateEpoch }, { it.id })) }

    /** مانده‌ی روزبه‌روز از روی پیامک‌های یک حساب ([points] به ترتیب زمان) */
    fun daily(points: List<BalancePointRow>, grid: DayGrid): BalanceSeries {
        val values = arrayOfNulls<Long>(grid.size)
        var p = 0
        var last: Long? = null
        for (i in 0 until grid.size) {
            val end = grid.endOf(i)
            while (p < points.size && points[p].dateEpoch < end) {
                last = points[p].remainAfter
                p++
            }
            values[i] = last
        }
        return BalanceSeries(grid, values.toList())
    }

    /**
     * جمع چند حساب، روزبه‌روز. فقط از روزی که مانده‌ی «همه»شان معلوم است؛
     * وگرنه اولین پیامک یک حساب تازه مثل واریز به چشم می‌آمد.
     */
    fun total(series: List<BalanceSeries>, grid: DayGrid): BalanceSeries {
        if (series.isEmpty()) return BalanceSeries(grid, List(grid.size) { null })
        val values = (0 until grid.size).map { i ->
            var sum = 0L
            for (s in series) sum += s.values[i] ?: return@map null
            sum
        }
        return BalanceSeries(grid, values)
    }

    /** همه‌ی حساب‌ها و جمعشان در [grid]؛ [excluded] همان کلیدهای WalletExclusion کیف پول */
    fun overview(
        rows: List<BalancePointRow>,
        links: List<AccountLink>,
        excluded: Set<String>,
        grid: DayGrid,
    ): BalanceOverview {
        // اسم گروه روی ردیف نماینده است (مثل AccountGrouping.balances)
        val names = links.associate { (it.bankId to it.account) to it.name }
        val accounts = byAccount(rows, links).map { (ref, points) ->
            AccountTrend(
                ref = ref,
                name = ref.account?.let { names[ref.bankId to it] },
                series = daily(points, grid),
                lastSmsMillis = points.last().dateEpoch,
                excluded = WalletExclusion.isExcluded(ref.bankId, ref.account, excluded),
            )
        }.sortedByDescending { it.lastSmsMillis }
        val included = accounts.filterNot { it.excluded }.map { it.series }
        return BalanceOverview(total(included, grid), accounts)
    }

    /**
     * کدام تراکنش‌های یک بانک مال حساب [ref] اند ([rows] فقط از همان بانک).
     * حسابِ بی‌شماره (بانکی که پیامک‌هایش شماره حساب ندارند) ← همه؛ وگرنه فقط شماره‌های همان گروه.
     */
    fun flowIdsOf(ref: AccountRef, rows: List<FlowAccountRow>, links: List<AccountLink>): Set<Long> {
        val account = ref.account ?: return rows.map { it.id }.toSet()
        return rows.filter { it.account != null && AccountGrouping.repOf(ref.bankId, it.account, links) == account }.map { it.id }.toSet()
    }

    /** این حساب مدتی پیامک نداشته؟ */
    fun isStale(lastSmsMillis: Long, nowMillis: Long): Boolean =
        nowMillis - lastSmsMillis >= STALE_DAYS * DAY_MILLIS

    /** «بعد از واریز بزرگ چه شد؟» برای یک حساب در [grid]؛ null اگر واریز بزرگی در بازه نبود */
    fun depositRhythm(points: List<BalancePointRow>, grid: DayGrid, nowMillis: Long): DepositRhythm? {
        val inRange = points.filter { grid.indexOf(it.dateEpoch) != null }
        val deposit = inRange
            .filter { it.flowType == DEPOSIT && it.amount >= MIN_DEPOSIT_RIAL }
            .maxWithOrNull(compareBy({ it.amount }, { it.dateEpoch })) ?: return null
        val target = deposit.remainAfter - deposit.amount + deposit.amount / 2
        val after = points.filter { it.dateEpoch > deposit.dateEpoch || (it.dateEpoch == deposit.dateEpoch && it.id > deposit.id) }
        val half = after.firstOrNull { it.remainAfter <= target }
        val depositDay = grid.indexOf(deposit.dateEpoch)!!
        val today = grid.indexOf(nowMillis) ?: grid.size - 1
        return DepositRhythm(
            amountRial = deposit.amount,
            dateMillis = deposit.dateEpoch,
            halfSpentDays = half?.let { (grid.indexOf(it.dateEpoch) ?: today) - depositDay },
            daysSince = today - depositDay,
        )
    }

    private const val DEPOSIT = 1
}
