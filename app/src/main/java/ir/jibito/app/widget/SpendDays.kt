package ir.jibito.app.widget

import ir.jibito.app.data.local.entity.DatedAmount
import java.util.Calendar

/**
 * روزهای ویجت «خرج امروز»: خرج هر روز برای نمودار هفت روز، و «یک روز معمولی» برای مقایسه با امروز.
 * به اندروید وابسته نیست (تست روی JVM).
 */
internal object SpendDays {

    /** نمودار ویجت: شش روز پیش تا امروز */
    const val WEEK = 7

    /** «یک روز معمولی» میانگین این چند روزِ پیش از امروز است */
    const val TYPICAL_DAYS = 30

    /** با سابقه‌ی کوتاه‌تر از این، «روز معمولی» معنا ندارد و مقایسه نشان داده نمی‌شود */
    private const val MIN_HISTORY_DAYS = 7

    /** امروز تا این‌قدر بیشتر یا کمتر از روز معمولی، هنوز «حدود یک روز معمولی» است */
    private const val ABOUT_MARGIN = 0.15

    /** شروع [count] روزِ آخر تا امروز (قدیمی‌ترین اول، آخری خود امروز)، به وقت گوشی */
    fun dayStarts(todayStart: Long, count: Int): List<Long> = (count - 1 downTo 0).map { back ->
        Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_MONTH, -back)
        }.timeInMillis
    }

    /** شروع فردا (پایان امروز) */
    fun nextDay(dayStart: Long): Long = Calendar.getInstance().apply {
        timeInMillis = dayStart
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    /**
     * خرج هر روز از [starts] ([rows] فقط خرج‌ها: SpendRollup.spendsOnly).
     * خرج یک‌باره هم حساب است، مثل عدد بزرگ «امروز».
     */
    fun perDay(rows: List<DatedAmount>, starts: List<Long>, end: Long): List<Long> =
        starts.indices.map { i ->
            val from = starts[i]
            val to = starts.getOrNull(i + 1) ?: end
            rows.filter { it.dateEpoch in from until to }.sumOf { it.amount }
        }

    /**
     * «یک روز معمولی»: میانگین خرج روزانه در [previous] (روزهای پیش از امروز)، بدون خرج‌های یک‌باره.
     * اگر اولین خرجِ ثبت‌شده ([firstSpend]) وسط این روزهاست، فقط روزهای بعد از آن شمرده می‌شوند.
     * null: کمتر از یک هفته سابقه، یا هیچ خرجی در این روزها.
     */
    fun typical(rows: List<DatedAmount>, previous: List<Long>, todayStart: Long, firstSpend: Long?): Long? {
        firstSpend ?: return null
        val firstDay = previous.lastOrNull { it <= firstSpend } ?: previous.firstOrNull() ?: return null
        val days = previous.count { it >= firstDay }
        if (days < MIN_HISTORY_DAYS) return null
        val total = rows.filter { !it.isOneOff && it.dateEpoch in firstDay until todayStart }.sumOf { it.amount }
        return if (total > 0) total / days else null
    }

    /** امروز در برابر یک روز معمولی */
    sealed interface Versus {
        /** مثلاً ۱۷ یعنی «۱٫۷ برابر» */
        data class More(val tenths: Long) : Versus

        /** مثلاً ۴۰ یعنی «۴۰٪ کمتر» */
        data class Less(val percent: Int) : Versus

        data object About : Versus
    }

    fun versus(todayRial: Long, typicalRial: Long): Versus {
        val ratio = todayRial.toDouble() / typicalRial
        return when {
            ratio >= 1 + ABOUT_MARGIN -> Versus.More(todayRial * 10 / typicalRial)
            ratio <= 1 - ABOUT_MARGIN -> Versus.Less(((typicalRial - todayRial) * 100 / typicalRial).toInt())
            else -> Versus.About
        }
    }
}
