package ir.jibito.app.notify

import java.util.Calendar

/** قانون خلاصه‌ی هفتگی (بدون اندروید، قابل تست). */
object WeeklyDigestRule {

    /** جمعه‌ها از این ساعت */
    const val HOUR = 19
    const val DAY_MS = 24L * 60 * 60 * 1000

    /** آخرین «جمعه ساعت ۱۹» که تا الان رسیده */
    fun lastSlot(now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY || cal.timeInMillis > now) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return cal.timeInMillis
    }

    /**
     * وقت خلاصه است؟ از جمعه ساعت ۱۹ تا ۲۴ ساعت بعد (اگر گوشی خاموش بود، فردا صبح هم می‌آید)، هفته‌ای یک بار.
     * @param lastSentSlot زمان lastSlot هفته‌ای که خلاصه‌اش فرستاده شد
     */
    fun isDue(now: Long, lastSentSlot: Long): Boolean {
        val slot = lastSlot(now)
        return slot != lastSentSlot && now - slot < DAY_MS
    }

    /** (زمان، مبلغ، دسته‌ی اصلی) ← خرج ۷ روز اخیر، ۷ روز قبلش و پرخرج‌ترین دسته‌ی این هفته */
    fun summarize(spends: List<Triple<Long, Long, Long?>>, now: Long): Digest {
        val weekStart = now - 7 * DAY_MS
        val prevStart = now - 14 * DAY_MS
        val thisWeek = spends.filter { it.first in weekStart until now }
        val byRoot = thisWeek.filter { it.third != null }.groupBy { it.third!! }.mapValues { (_, v) -> v.sumOf { it.second } }
        return Digest(
            thisWeekRial = thisWeek.sumOf { it.second },
            lastWeekRial = spends.filter { it.first in prevStart until weekStart }.sumOf { it.second },
            topRootId = byRoot.maxByOrNull { it.value }?.key,
        )
    }

    data class Digest(val thisWeekRial: Long, val lastWeekRial: Long, val topRootId: Long?) {
        /** درصد تغییر نسبت به هفته‌ی قبل؛ null اگر هفته‌ی قبل تقریباً خرجی نبود */
        val changePercent: Int?
            get() = if (lastWeekRial < 1_000_000L) null else Math.round((thisWeekRial - lastWeekRial) * 100.0 / lastWeekRial).toInt()
    }

    /** خرج هر کدام از ۷ روز اخیر (۲۴ ساعت‌های منتهی به now)، قدیمی‌ترین اول */
    fun dailyTotals(spends: List<Triple<Long, Long, Long?>>, now: Long): List<Long> {
        val totals = LongArray(7)
        for ((time, amount, _) in spends) {
            val daysAgo = ((now - 1 - time) / DAY_MS).toInt()
            if (time < now && daysAgo in 0..6) totals[6 - daysAgo] += amount
        }
        return totals.toList()
    }

    /**
     * نمودار کوچک متنی «▂▅▃▇▄█▁» برای متن نوتیفیکیشن (بدون نمای سفارشی، روی همه‌ی گوشی‌ها یکسان).
     * روز بی‌خرج پایین‌ترین پله را می‌گیرد تا جای روز خالی نماند.
     */
    fun sparkline(values: List<Long>): String {
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: return ""
        return values.joinToString("") { v ->
            val step = ((v.toDouble() / max) * (BARS.length - 1)).let { Math.round(it).toInt() }
            BARS[step.coerceIn(0, BARS.length - 1)].toString()
        }
    }

    private const val BARS = "▁▂▃▄▅▆▇█"
}
