package ir.jibito.app.data.wallet

import ir.jibito.app.data.repository.SpendCurve
import ir.jibito.app.util.JalaliMonth
import java.util.Calendar
import kotlin.math.abs

/*
 * «این واریز حقوقته؟»: واریز ماهانه‌ای که بزرگ و منظم است (همان قاعده‌ی پرداخت‌های تکراری، برای واریزها).
 * اپ فقط حدس می‌زند و یک بار می‌پرسد؛ تا کاربر «آره» نگفته، پیش‌بینی موجودی حقوقی در نظر نمی‌گیرد.
 *
 * این فایل به اندروید وابسته نیست (تست روی JVM).
 */

/** یک تراکنش بانکی برای پیش‌بینی موجودی و تشخیص حقوق (بدون انتقال به خودم و خرید ناموفق) */
data class FlowRow(
    val id: Long,
    val bankId: Int,
    val account: String?,
    /** ۱ = واریز، ۲ = برداشت */
    val flowType: Int,
    val amount: Long,
    val dateEpoch: Long,
    val merchant: String?,
    /** خرج یک‌باره (خانه، ماشین…) که تکرار نمی‌شود */
    val isOneOff: Boolean,
)

/** حقوقی که اپ حدس زده یا کاربر تأیید کرده */
data class Salary(
    /** «بانک|واریزکننده»؛ ثابت از ماهی به ماه دیگر (برای «نه، حقوقم نیست» و پیدا کردن واریز همین ماه) */
    val key: String,
    val bankId: Int,
    val amountRial: Long,
    /** روز معمول واریز در ماه شمسی */
    val dayOfMonth: Int,
    /** چند ماه دیده شده */
    val months: Int = 0,
)

object SalaryDetector {

    const val MIN_MONTHS = 3
    private const val LOOKBACK_MONTHS = 6
    private const val AMOUNT_TOLERANCE = 0.15

    /** پراکندگی روز واریز (دوری: ۳۰ام و ۱ام دو روز فاصله دارند، چون خیلی‌ها آخر یا اول ماه حقوق می‌گیرند) */
    private const val MAX_DAY_SPREAD = 4

    /** کمتر از ۲ میلیون تومان حقوق حساب نمی‌شود (یارانه و برگشت‌های کوچک) */
    const val MIN_AMOUNT_RIAL = 20_000_000L

    /** واریز همین ماه: تا ۲۵٪ با مبلغ معمول فرق، از ۱۰ روز قبل از روز معمول */
    private const val RECEIVED_TOLERANCE = 0.25
    private const val EARLY_DAYS = 10

    private const val DEPOSIT = 1

    /** همان نرمال‌سازی RecurringDetector (ی/ک عربی، نیم‌فاصله، فاصله) */
    fun keyOf(bankId: Int, merchant: String?): String =
        "$bankId|" + merchant.orEmpty().replace('ي', 'ی').replace('ك', 'ک').replace("\u200C", "").replace(" ", "").lowercase().trim()

    /**
     * واریزهای ماهانه‌ی منظم، بزرگ‌ترین اول. هر واریزکننده (در هر بانک) حداکثر یک پیشنهاد دارد.
     * شرط‌ها: دست‌کم ۳ ماه شمسی از ۶ ماه اخیر، مبلغ نزدیک (±۱۵٪)، تقریباً یک بار در ماه،
     * روز تقریباً ثابت، و هنوز فعال (این ماه یا ماه قبل هم آمده).
     */
    fun detect(rows: List<FlowRow>, dismissedKeys: Set<String>, nowMillis: Long): List<Salary> {
        val current = JalaliMonth.of(nowMillis)
        val oldest = current.plus(-(LOOKBACK_MONTHS - 1)).startMillis()
        val deposits = rows.filter { it.flowType == DEPOSIT && it.amount >= MIN_AMOUNT_RIAL && it.dateEpoch in oldest..nowMillis }
        return deposits.groupBy { keyOf(it.bankId, it.merchant) }
            .filterKeys { it !in dismissedKeys }
            .mapNotNull { (key, group) -> bestCluster(key, group, current) }
            .sortedByDescending { it.amountRial }
    }

    private fun bestCluster(key: String, group: List<FlowRow>, current: JalaliMonth): Salary? =
        group.mapNotNull { seed ->
            val similar = group.filter { abs(it.amount - seed.amount) <= seed.amount * AMOUNT_TOLERANCE }
            val byMonth = similar.groupBy { JalaliMonth.of(it.dateEpoch).key }
            if (byMonth.size < MIN_MONTHS) return@mapNotNull null
            if (similar.size > byMonth.size * 4 / 3) return@mapNotNull null
            if (current.key !in byMonth && current.plus(-1).key !in byMonth) return@mapNotNull null
            val days = byMonth.values.map { SpendCurve.dayOfMonth(it.maxBy { r -> r.amount }.dateEpoch) }
            val typical = days.minBy { d -> days.maxOf { circular(it, d) } }
            if (days.any { circular(it, typical) > MAX_DAY_SPREAD }) return@mapNotNull null
            Salary(key, group.first().bankId, median(similar.map { it.amount }), typical, byMonth.size)
        }.maxWithOrNull(compareBy({ it.months }, { it.amountRial }))

    /** فاصله‌ی دو روز ماه، دوری (۳۰ و ۱ دو روز فاصله دارند) */
    internal fun circular(a: Int, b: Int): Int {
        val d = abs(a - b)
        return minOf(d, 30 - d).coerceAtLeast(0)
    }

    /**
     * روز واریز حقوق بعدی (شروع آن روز). اگر حقوقِ دوره‌ی جاری آمده (از ۱۰ روز قبل از روز معمول تا الان)
     * یا روزش گذشته و نیامده (دیر کرده)، حقوق ماه بعد؛ تا خودش نیامده روی آن حساب نمی‌کنیم.
     */
    fun nextPayday(salary: Salary, rows: List<FlowRow>, nowMillis: Long): Long {
        val today = startOfDay(nowMillis)
        val month = JalaliMonth.of(nowMillis)
        // دوره‌ی جاری: آخرین روز معمولی که «۱۰ روز قبلش» رسیده (حقوق ۱ آبان ممکن است ۲۸ مهر بیاید)
        val dues = (-1..1).map { dayIn(month.plus(it), salary.dayOfMonth) }
        val period = dues.indices.last { dues[it] - EARLY_DAYS * DAY <= today }
        val received = rows.any {
            it.flowType == DEPOSIT && keyOf(it.bankId, it.merchant) == salary.key &&
                abs(it.amount - salary.amountRial) <= salary.amountRial * RECEIVED_TOLERANCE &&
                it.dateEpoch >= dues[period] - EARLY_DAYS * DAY && it.dateEpoch <= nowMillis
        }
        return if (!received && dues[period] >= today) dues[period] else dayIn(month.plus(period), salary.dayOfMonth)
    }

    /** شروع روز [day] ماه [month] (ماه کوتاه‌تر: روز آخرش) */
    fun dayIn(month: JalaliMonth, day: Int): Long {
        val d = day.coerceIn(1, SpendCurve.daysIn(month))
        return Calendar.getInstance().apply {
            timeInMillis = month.startMillis()
            add(Calendar.DAY_OF_MONTH, d - 1)
        }.timeInMillis
    }

    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]

    private const val DAY = 24L * 60 * 60 * 1000
}
