package ir.jibito.app.ui.summary

import ir.jibito.app.R
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.data.wallet.BalanceForecast
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
import kotlin.math.roundToInt

/** حال جیب این ماه: رنگ سرصفحه و جمله‌ی زیرش از همین می‌آید */
enum class Mood { CALM, WARN, OVER }

/** حال جیب و جمله‌اش (منطق جدا از ظاهر، برای تست) */
data class MoodLine(val mood: Mood, val sentence: Int, val args: List<Any> = emptyList())

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** روزهای مانده تا آخر ماه (با امروز)؛ برای ماه‌های گذشته ۰ */
fun daysLeft(s: MonthSummary, nowMillis: Long): Int {
    val end = s.month.endMillis()
    if (nowMillis < s.month.startMillis() || nowMillis >= end) return 0
    return ((end - nowMillis + DAY_MILLIS - 1) / DAY_MILLIS).toInt().coerceAtLeast(1)
}

/**
 * - بیشتر از بودجه ← «بیرون زد» (قرمز)
 * - ۸۰٪ بودجه رفته، یا با همین ریتم آخر ماه بیرون می‌زند ← «یواش‌تر» (کهربایی)؛
 *   هرچه پیش‌بینی از بودجه دورتر، جمله تندتر (تا ۱۲۰٪ «یه کم»، تا ۱۷۵٪ «تند»، بیشتر «خیلی تند… N برابر»)
 * - موجودی حساب‌ها تا حقوق (یا آخر ماه) نمی‌رسد ← دست‌کم «یواش‌تر»، حتی اگر بودجه جا دارد؛
 *   تا سرصفحه «آرومه» نگوید و کارت پیش‌بینی زیرش «تموم می‌شه»
 * - بقیه ← «آروم» (فیروزه‌ای)؛ بدون بودجه هم آروم است و پیشنهاد سقف می‌دهد.
 */
fun moodOf(s: MonthSummary, nowMillis: Long, forecast: BalanceForecast? = null): MoodLine {
    val line = budgetMood(s, nowMillis)
    if (line.mood != Mood.CALM) return line
    val short = forecast != null && !forecast.enough && s.month == JalaliMonth.of(nowMillis)
    if (!short) return line
    // بی‌بودجه، همان دعوت به گذاشتن سقف می‌ماند؛ فقط رنگ با کارت پیش‌بینی هم‌نظر می‌شود
    if (s.overallBudgetRial?.takeIf { it > 0 } == null) return line.copy(mood = Mood.WARN)
    return MoodLine(
        Mood.WARN,
        if (forecast?.paydayMillis != null) R.string.hero_balance_short_salary else R.string.hero_balance_short_month,
    )
}

private fun budgetMood(s: MonthSummary, nowMillis: Long): MoodLine {
    val budget = s.overallBudgetRial?.takeIf { it > 0 }
    // خرج یک‌باره (خرید خانه…) از بودجه کم نمی‌شود
    val spent = if (budget == null) s.totalSpentRial else s.budgetSpentRial
    if (budget == null) {
        return MoodLine(Mood.CALM, if (spent == 0L) R.string.hero_no_spend else R.string.hero_no_budget)
    }
    if (spent > budget) return MoodLine(Mood.OVER, R.string.hero_over, listOf(Money.compact(spent - budget)))
    val time = s.timeFraction(nowMillis)
        ?: return MoodLine(Mood.CALM, R.string.hero_past_calm, listOf(Money.compact(budget - spent)))
    val percent = (spent * 100 / budget).toInt()
    val days = daysLeft(s, nowMillis)
    if (percent >= 80) {
        return if (days <= 1) MoodLine(Mood.WARN, R.string.hero_warn_last_day, listOf(percent))
        else MoodLine(Mood.WARN, R.string.hero_warn, listOf(percent, days))
    }
    // چند روز اول ماه، پیش‌بینی معنی ندارد
    if (time < 0.1f) return MoodLine(Mood.CALM, R.string.hero_calm_start)
    val projected = (spent / time.toDouble()).toLong()
    val over = Money.compact(projected - budget)
    return when {
        projected >= budget * 175 / 100 ->
            MoodLine(Mood.WARN, R.string.hero_warn_pace_far, listOf(over, (projected.toDouble() / budget).roundToInt()))
        projected > budget * 120 / 100 -> MoodLine(Mood.WARN, R.string.hero_warn_pace_fast, listOf(over))
        projected > budget * 105 / 100 -> MoodLine(Mood.WARN, R.string.hero_warn_pace, listOf(over))
        budget - projected >= budget * 3 / 100 -> MoodLine(Mood.CALM, R.string.hero_calm, listOf(Money.compact(budget - projected)))
        else -> MoodLine(Mood.CALM, R.string.hero_calm_on_track)
    }
}
