package ir.jibito.app.data.wallet

import ir.jibito.app.data.repository.SpendCurve
import ir.jibito.app.notify.RecurringPaidCheck
import ir.jibito.app.util.JalaliMonth
import java.util.Calendar

/*
 * «پولم تا حقوق بعدی می‌رسه؟»: موجودی امروز − خرج روزمره‌ی پیش رو − پرداخت‌های ثابتِ هنوز پرداخت‌نشده،
 * روزبه‌روز تا روز حقوق بعدی (اگر حقوق تأیید شده)، وگرنه تا آخر ماه.
 *
 * - خرج روزمره: برداشت‌های ماه قبل، روزبه‌روز (همان روزِ ماه). پرداخت‌های ثابت و خرج‌های یک‌باره از این الگو بیرون‌اند
 *   (اولی جدا با روز موعدش کم می‌شود، دومی تکرار نمی‌شود). ماه قبل کامل نیست ← ریتم همین ماه (بعد از سه روز).
 * - همه‌ی برداشت‌ها حساب می‌شوند (نه فقط «خرج»): پس‌انداز و قرض دادن هم موجودی را کم می‌کنند.
 * - فقط حساب‌هایی که در کیف پول جمع می‌شوند؛ واریزهای دیگر (غیر از حقوق) حساب نمی‌شوند.
 *
 * این فایل به اندروید وابسته نیست (تست روی JVM).
 */

/** یک پرداخت ثابت (یادآوری پرداخت ماهانه) */
data class PlannedPayment(val title: String, val amountRial: Long, val dayOfMonth: Int)

/** یک پرداخت ثابتِ پیش رو، با روزش */
data class UpcomingPayment(val title: String, val amountRial: Long, val dayMillis: Long)

enum class ForecastBasis {
    /** الگوی برداشت‌های ماه قبل */
    PATTERN,
    /** ریتم برداشت‌های همین ماه تا امروز */
    RHYTHM,
}

data class BalanceForecast(
    /** شروع هر روز؛ اولی امروز */
    val days: List<Long>,
    /** موجودی آخر هر روز؛ اولی موجودی الان */
    val values: List<Long>,
    /** خرج روزمره‌ی پیش رو (بدون پرداخت‌های ثابت) */
    val routineRial: Long,
    val payments: List<UpcomingPayment>,
    /** حقوق بعدی؛ null یعنی حقوقی تأیید نشده و افق، آخر ماه است */
    val salary: Salary?,
    /** روز حقوق بعدی (وقتی salary هست) */
    val paydayMillis: Long?,
    val basis: ForecastBasis,
) {
    val startRial: Long get() = values.first()

    /** موجودی آخر افق (شب قبل از حقوق، یا آخر ماه) */
    val endRial: Long get() = values.last()

    val lowRial: Long get() = values.min()

    /** روزِ کف (اولین بار) */
    val lowDay: Long get() = days[values.indexOf(lowRial)]

    /** اولین روزی که موجودی منفی می‌شود؛ null یعنی می‌رسد */
    val zeroDay: Long? get() = values.indexOfFirst { it < 0 }.takeIf { it >= 0 }?.let { days[it] }

    val enough: Boolean get() = zeroDay == null
}

object BalanceForecastCalc {

    /** ماه قبل کمتر از این (۱۰۰ هزار تومان) برداشت داشت ← الگویش پایه‌ی پیش‌بینی نیست */
    private const val MIN_PATTERN_RIAL = 1_000_000L

    /** ریتم همین ماه فقط بعد از سه روز اول */
    private const val MIN_RHYTHM_DAYS = 3

    /** اولین تراکنشِ ثبت‌شده این‌قدر بعد از اول ماه قبل باشد، ماه قبل کامل نیست */
    private const val PATTERN_GRACE_DAYS = 3

    private const val TOLERANCE = 0.03
    private const val WITHDRAWAL = 2
    private const val DAY = 24L * 60 * 60 * 1000

    /**
     * @param todayRial موجودی الان (جمع کیف پول)
     * @param rows تراکنش‌های حساب‌های کیف پول، دست‌کم از اول ماه قبل
     * @param salary حقوق تأییدشده؛ null یعنی تا آخر ماه
     * @return null وقتی پایه‌ای برای پیش‌بینی نیست (ماه قبل نیست و هنوز سه روز از این ماه نگذشته)
     */
    fun compute(
        todayRial: Long,
        rows: List<FlowRow>,
        planned: List<PlannedPayment>,
        salary: Salary?,
        nowMillis: Long,
    ): BalanceForecast? {
        val today = SalaryDetector.startOfDay(nowMillis)
        val month = JalaliMonth.of(nowMillis)
        val payday = salary?.let { SalaryDetector.nextPayday(it, rows, nowMillis) }
        // آخرین روز افق: شب قبل از حقوق، یا آخر ماه
        val last = if (payday != null) payday - DAY else month.endMillis() - DAY
        val routine = rows.filter { it.flowType == WITHDRAWAL && !it.isOneOff && planned.none { p -> matches(it.amount, p.amountRial) } }

        val previous = month.plus(-1)
        val prevDays = SpendCurve.daysIn(previous)
        val prevRows = routine.filter { it.dateEpoch >= previous.startMillis() && it.dateEpoch < previous.endMillis() }
        val firstSeen = rows.minOfOrNull { it.dateEpoch }
        val patternOk = prevRows.sumOf { it.amount } >= MIN_PATTERN_RIAL &&
            firstSeen != null && firstSeen <= previous.startMillis() + PATTERN_GRACE_DAYS * DAY
        val pattern = LongArray(prevDays).also { daily ->
            prevRows.forEach { daily[SpendCurve.dayOfMonth(it.dateEpoch) - 1] += it.amount }
        }
        val elapsed = SpendCurve.dayOfMonth(nowMillis)
        val rhythm = routine.filter { it.dateEpoch >= month.startMillis() && it.dateEpoch <= nowMillis }.sumOf { it.amount } / elapsed
        val basis = when {
            patternOk -> ForecastBasis.PATTERN
            elapsed >= MIN_RHYTHM_DAYS -> ForecastBasis.RHYTHM
            else -> return null
        }
        val average = pattern.sum() / prevDays

        val days = mutableListOf(today)
        val values = mutableListOf(todayRial)
        var balance = todayRial
        var routineSum = 0L
        val upcoming = mutableListOf<UpcomingPayment>()
        var day = next(today)
        while (day <= last) {
            val dom = SpendCurve.dayOfMonth(day)
            val spend = if (basis == ForecastBasis.PATTERN) pattern.getOrElse(dom - 1) { average } else rhythm
            balance -= spend
            routineSum += spend
            planned.forEach { p ->
                if (SalaryDetector.dayIn(JalaliMonth.of(day), p.dayOfMonth) == day && !paid(p, day, rows, nowMillis)) {
                    balance -= p.amountRial
                    upcoming += UpcomingPayment(p.title, p.amountRial, day)
                }
            }
            days += day
            values += balance
            day = next(day)
        }
        return BalanceForecast(days, values, routineSum, upcoming, salary?.takeIf { payday != null }, payday, basis)
    }

    /**
     * فقط تراکنش‌های حساب‌هایی که در جمع کیف پول‌اند (همان گروه‌بندی [overview])؛
     * موجودی امروز هم فقط جمع همین‌هاست، پس برداشت حساب‌های دیگر نباید از آن کم شود.
     */
    fun walletRows(rows: List<FlowRow>, overview: BalanceOverview, links: List<AccountLink>): List<FlowRow> {
        val included = overview.accounts.filterNot { it.excluded }.map { it.ref }.toSet()
        val wholeBank = overview.accounts.filter { it.ref.account == null }.map { it.ref.bankId }.toSet()
        return rows.filter { r ->
            val ref = when {
                r.bankId in wholeBank -> AccountRef(r.bankId, null)
                r.account != null -> AccountRef(r.bankId, AccountGrouping.repOf(r.bankId, r.account, links))
                else -> return@filter false
            }
            ref in included
        }
    }

    /** پرداخت ثابتی که موعدش [dueDay] است، از ۱۰ روز قبلش تا الان پرداخت شده؟ (فقط برای موعدهای نزدیک معنی دارد) */
    private fun paid(p: PlannedPayment, dueDay: Long, rows: List<FlowRow>, nowMillis: Long): Boolean {
        val from = RecurringPaidCheck.windowStart(dueDay)
        if (from > nowMillis) return false
        val window = rows.filter { it.flowType == WITHDRAWAL && it.dateEpoch in from..nowMillis }.map { it.amount }
        return RecurringPaidCheck.alreadyPaid(window, p.amountRial)
    }

    private fun matches(amount: Long, planned: Long): Boolean =
        planned > 0 && kotlin.math.abs(amount - planned) <= (planned * TOLERANCE).toLong()

    private fun next(day: Long): Long = Calendar.getInstance().apply {
        timeInMillis = day
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
}
