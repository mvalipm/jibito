package ir.jibito.app.ui.reports

import android.content.Context
import androidx.core.content.edit
import ir.jibito.app.data.repository.Insight

/** بخش‌های تب «گزارش‌ها»؛ هر کدام یک دکمه که کارتش زیرش باز می‌شود (همیشه حداکثر یکی باز است) */
enum class ReportSection {
    SPEND,
    BALANCE,
    INSIGHTS,
}

/** کدام کارت موقع ورود باز باشد و کدام نکته‌ها تازه‌اند (منطق خالص، بدون Context) */
object ReportSections {
    /** تا این تعداد باز کردن، هنوز معلوم نیست کاربر بیشتر سراغ کدام گزارش می‌رود */
    const val MIN_OPENS = 20

    /**
     * کارت باز موقع ورود: بعد از [MIN_OPENS] بار باز کردن، گزارشی که بیش از دو برابرِ هر کدام از بقیه باز شده؛
     * وگرنه همان نمودار خرج. ترتیب دکمه‌ها هیچ‌وقت عوض نمی‌شود.
     */
    fun favorite(counts: Map<ReportSection, Int>): ReportSection {
        if (counts.values.sum() < MIN_OPENS) return ReportSection.SPEND
        val top = ReportSection.entries.maxBy { counts[it] ?: 0 }
        val topCount = counts[top] ?: 0
        val clear = ReportSection.entries.all { it == top || topCount > 2 * (counts[it] ?: 0) }
        return if (clear) top else ReportSection.SPEND
    }

    /**
     * شناسه‌ی یک نکته، بدون عددهایش: «رستوران بیشتر شد» با تغییر چند درصد همان نکته است،
     * ولی «رستوران کمتر شد» یا پرخرج‌ترین روزِ دیگری نکته‌ی تازه است.
     */
    fun key(insight: Insight): String = when (insight) {
        is Insight.CategoryChange -> "cat:${insight.categoryId}:${if (insight.deltaRial > 0) "up" else "down"}"
        is Insight.BusiestDay -> "day:${insight.month.year}-${insight.month.month}-${insight.day}"
        is Insight.WeekdayPeak -> "weekday:${insight.weekday}"
    }

    fun hasNew(insights: List<Insight>, seen: Set<String>): Boolean = insights.any { key(it) !in seen }
}

/** شمارش باز کردن هر کارت و نکته‌های دیده‌شده، فقط روی همین گوشی */
class ReportSectionPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("report_sections", Context.MODE_PRIVATE)

    fun counts(): Map<ReportSection, Int> = ReportSection.entries.associateWith { prefs.getInt(countKey(it), 0) }

    fun favorite(): ReportSection = ReportSections.favorite(counts())

    fun recordOpen(section: ReportSection) = prefs.edit { putInt(countKey(section), prefs.getInt(countKey(section), 0) + 1) }

    val seenInsights: Set<String> get() = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty().toSet()

    fun markSeen(insights: List<Insight>) = prefs.edit { putStringSet(KEY_SEEN, insights.map(ReportSections::key).toSet()) }

    private fun countKey(section: ReportSection) = "opens_" + section.name.lowercase()

    private companion object {
        const val KEY_SEEN = "seen_insights"
    }
}
