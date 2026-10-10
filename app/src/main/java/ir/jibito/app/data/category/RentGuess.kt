package ir.jibito.app.data.category

import ir.jibito.app.data.recurring.MonthlyPatterns
import ir.jibito.app.util.JalaliMonth

/**
 * حدس دسته‌ی انتقال به کارت یک شخص که هنوز دسته‌ای برایش انتخاب نشده:
 * اگر مبلغ بزرگ و ثابت هر ماه در یک روز می‌رود، به احتمال زیاد «اجاره» است (قرض و کمک معمولاً منظم نیستند).
 * فقط پیشنهاد است، نه خودکار؛ و بعد از اولین انتخاب خود کاربر، همان انتخاب جای این را می‌گیرد.
 */
object RentGuess {

    /** هر ماه کمتر از این (۵ میلیون تومان) اجاره حساب نمی‌شود: کمک و قسط دوستانه زیاد با اجاره قاطی می‌شود */
    const val MIN_AMOUNT_RIAL = 50_000_000L

    private const val LOOKBACK_MONTHS = 6

    /** شروع پنجره‌ی ۶ ماه اخیر (برای خواندن سابقه‌ی کارت از دیتابیس) */
    fun windowStart(now: Long): Long = JalaliMonth.of(now).plus(-(LOOKBACK_MONTHS - 1)).startMillis()

    /** @param payments جفت‌های (مبلغ به ریال، زمان) برداشت‌های عادی به همین کارت */
    fun looksLikeRent(payments: List<Pair<Long, Long>>, now: Long): Boolean {
        val start = windowStart(now)
        val recent = payments.filter { it.second in start..now }
        val pattern = MonthlyPatterns.detect(recent, now) ?: return false
        return pattern.amountRial >= MIN_AMOUNT_RIAL
    }
}
