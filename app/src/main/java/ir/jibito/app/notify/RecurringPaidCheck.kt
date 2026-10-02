package ir.jibito.app.notify

/**
 * «این ماه پرداختش کرده؟» برای یادآوری پرداخت ماهانه (بدون اندروید، قابل تست).
 * اگر از ۱۰ روز قبل از موعد تا الان برداشتی با همان مبلغ (±۳٪، برای کارمزد کارت‌به‌کارت یا گرد کردن)
 * از پیامک بانک ثبت شده باشد، یادآوری لازم نیست.
 */
object RecurringPaidCheck {

    /** چند روز قبل از موعد هم پرداخت حساب می‌شود (مثلاً اجاره‌ای که زودتر واریز شده) */
    const val EARLY_DAYS = 10

    private const val TOLERANCE = 0.03

    fun windowStart(dueDayStartMillis: Long): Long = dueDayStartMillis - EARLY_DAYS * 24L * 60 * 60 * 1000

    /** @param withdrawals مبلغ برداشت‌های پنجره (ریال) */
    fun alreadyPaid(withdrawals: List<Long>, amountRial: Long): Boolean {
        if (amountRial <= 0) return false
        val margin = (amountRial * TOLERANCE).toLong()
        return withdrawals.any { kotlin.math.abs(it - amountRial) <= margin }
    }
}
