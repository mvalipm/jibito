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
}
