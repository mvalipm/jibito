package ir.jibito.app.ui.tour

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ir.jibito.app.R

/**
 * مرحله‌های راهنمای کاربر تازه، به ترتیب نمایش: هر مرحله یک جای اپ را روشن می‌کند و دو سه جمله می‌نویسد.
 * [padDp] فاصله‌ی روشنایی دور آن جا و [cornerDp] گردی گوشه‌اش؛ عدد خیلی بزرگ یعنی دایره/کپسول کامل.
 */
enum class TourStep(@StringRes val title: Int, @StringRes val text: Int, val padDp: Int, val cornerDp: Int) {
    /** عدد درشت خرج ماه و بودجه، بالای «خلاصه» */
    HERO(R.string.tour_hero_title, R.string.tour_hero_text, padDp = 6, cornerDp = 24),

    /** «کجا رفت؟» و نوار سهم دسته‌ها */
    WHERE(R.string.tour_where_title, R.string.tour_where_text, padDp = 4, cornerDp = 18),
    TRANSACTIONS(R.string.tour_transactions_title, R.string.tour_transactions_text, padDp = 0, cornerDp = 29),
    REPORTS(R.string.tour_reports_title, R.string.tour_reports_text, padDp = 0, cornerDp = 29),
    TODO(R.string.tour_todo_title, R.string.tour_todo_text, padDp = 0, cornerDp = 29),

    /** دکمه‌ی تنظیمات بالای «خلاصه»؛ همیشه هست، پس نبودنش یعنی «خلاصه» هنوز نیامده */
    SETTINGS(R.string.tour_settings_title, R.string.tour_settings_text, padDp = 4, cornerDp = 100),
}

/** از مرحله‌های ممکن، فقط آن‌هایی که جایشان الان روی صفحه است (مثلاً بی‌تراکنش «کجا رفت؟» نیست)، به همان ترتیب */
internal fun tourSteps(available: Set<TourStep>): List<TourStep> = TourStep.entries.filter { it in available }

/**
 * مرحله‌ی جاری از روی شماره‌ی آخرین مرحله‌ای که کاربر دیده: اگر آن مرحله دیگر روی صفحه نیست، اولین مرحله‌ی بعدی؛
 * و اگر بعدی هم نمانده، آخرین مرحله. null یعنی هیچ مرحله‌ای نیست.
 */
internal fun List<TourStep>.current(ordinal: Int): TourStep? = firstOrNull { it.ordinal >= ordinal } ?: lastOrNull()

/** جای مرحله‌ها روی صفحه (در مختصات ریشه)؛ هر صفحه جای خودش را ثبت می‌کند و با رفتنش پاک می‌شود */
class TourAnchors {
    private val parts = mutableStateMapOf<Pair<TourStep, Int>, Rect>()

    fun set(step: TourStep, part: Int, rect: Rect) {
        parts[step to part] = rect
    }

    fun remove(step: TourStep, part: Int) {
        parts.remove(step to part)
    }

    /** مرحله‌هایی که جایشان ثبت شده */
    val steps: Set<TourStep> get() = parts.keys.mapTo(HashSet()) { it.first }

    /** جای یک مرحله؛ اگر از چند تکه ثبت شده، کوچک‌ترین کادری که همه را بپوشاند */
    operator fun get(step: TourStep): Rect? =
        parts.filterKeys { it.first == step }.values.reduceOrNull { a, b ->
            Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
        }
}

/** null یعنی راهنمایی در کار نیست (مثلاً در اسکرین‌شات‌ها) و جاها ثبت نمی‌شوند */
val LocalTourAnchors = staticCompositionLocalOf<TourAnchors?> { null }

/**
 * راهنمای کاربر تازه: فقط برای نصب تازه، یک بار خودش می‌آید و بعد از تمام شدن یا «رد کردن» دیگر نمی‌آید.
 * کسی که اپ را به‌روز کرده اپ را می‌شناسد و خودکار نمی‌بیندش؛ از «راهنما» در تنظیمات هر وقت بخواهد دوباره نشان داده می‌شود ([restart]).
 */
class ProductTour(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    private val _active = MutableStateFlow(false)

    /** راهنما الان باید نشان داده شود (به شرطی که صفحه برایش آماده باشد) */
    val active: StateFlow<Boolean> = _active

    /**
     * موقع باز شدن اپ. اولین بار که این‌جا می‌رسیم، نصب تازه را از به‌روزرسانی جدا می‌کند و برای همیشه به خاطر می‌سپارد.
     * فقط روشن می‌کند، هیچ‌وقت خاموش نمی‌کند: «دوباره نشان بده»ِ نیمه‌کاره با چرخاندن گوشی از بین نرود.
     */
    fun start(freshInstall: Boolean) {
        if (!prefs.contains(KEY_SEEN)) prefs.edit { putBoolean(KEY_SEEN, !freshInstall) }
        if (!prefs.getBoolean(KEY_SEEN, false)) _active.value = true
    }

    /** تمام شد یا کاربر رد کرد */
    fun finish() {
        prefs.edit { putBoolean(KEY_SEEN, true) }
        _active.value = false
    }

    /** «دوباره نشان بده» در تنظیمات */
    fun restart() {
        _active.value = true
    }

    companion object {
        const val PREFS = "product_tour"
        private const val KEY_SEEN = "seen"
    }
}
