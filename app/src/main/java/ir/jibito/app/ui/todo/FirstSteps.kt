package ir.jibito.app.ui.todo

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.domain.Transaction
import ir.jibito.app.ui.settings.rememberAppLock
import ir.jibito.app.ui.summary.rememberNotificationPrompt
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import kotlinx.coroutines.flow.map

/** یک فروشگاه پرتکرار در قدم «یادم بده»: لمسش برگه‌ی «مال چی بود؟» یکی از خرج‌هایش را باز می‌کند */
data class StepChip(val label: String, val count: Int, val done: Boolean, val onClick: () -> Unit)

/** پیشنهاد یک‌لمسی داخل قدم (مثلاً «آره، ۲۲ میلیون») و راه دیگرش («یه عدد دیگه») */
data class StepOffer(val accept: String, val onAccept: () -> Unit, val other: String, val onOther: () -> Unit)

/** یک قدم از «جیبت رو مرتب کنیم»: با انجام شدن کارش خودبه‌خود تیک می‌خورد */
data class FirstStep(
    val id: String,
    val label: String,
    val detail: String,
    val done: Boolean,
    /** پیشرفت داخل خود قدم («۲ از ۳»)؛ null یعنی ندارد */
    val progress: String? = null,
    val chips: List<StepChip> = emptyList(),
    val offer: StepOffer? = null,
    val onClick: () -> Unit,
)

/**
 * کارت «جیبت رو مرتب کنیم»؛ [onHide] یعنی کاربر دیگر نمی‌خواهدش.
 * [celebrating]: همه‌ی قدم‌ها انجام شده و کارت فقط «جیبت آماده‌ست» را نشان می‌دهد تا کاربر «باشه» بزند.
 */
data class FirstStepsUi(val steps: List<FirstStep>, val onHide: () -> Unit, val celebrating: Boolean = false)

/**
 * راهنمای کاربر تازه، به شکل چند کار انجام‌دادنی (نه چند صفحه متن):
 * یاد دادن دسته‌ی پرتکرارترین فروشگاه‌ها، بودجه (با پیشنهاد از میانگین خرج خود کاربر) و قفل اپ؛
 * و اگر نوتیف خاموش است (کاربر در صفحه‌ی نوتیف «بعداً» زده)، روشن کردنش.
 * هر قدم از روی خود داده‌ها تیک می‌خورد. وقتی همه انجام شد، یک بار «جیبت آماده‌ست» و بعد برای همیشه می‌رود؛ ✕ هم همین‌طور.
 * کارت اول بالای «خلاصه» است و بعد از [FirstStepsPlacement.SUMMARY_DAYS] روزِ باز کردن اپ به «کارها» می‌رود ([rememberFirstStepsOnSummary]).
 *
 * @param averageSpendRial میانگین خرج ماه‌های تمام‌شده؛ null یعنی هنوز ماه کاملی نیست و پیشنهاد بودجه‌ای در کار نیست
 * @param onOpenTransaction برگه‌ی «مال چی بود؟» یک تراکنش (در «تراکنش‌ها»)
 * @return null یعنی کارت نشان داده نشود
 */
@Composable
fun rememberFirstSteps(
    s: MonthSummary?,
    averageSpendRial: Long?,
    onOpenUncategorized: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onEditBudget: () -> Unit,
    onSetBudget: (Long) -> Unit,
): FirstStepsUi? {
    val context = LocalContext.current
    val app = context.applicationContext as JibitoApplication
    val prefs = remember { FirstStepsPrefs(app) }
    var finished by remember { mutableStateOf(prefs.finished) }
    val progressFlow = remember {
        app.container.transactionRepository.observeTransactions().map { list ->
            val expenses = list.filter { it.transaction.type == FlowType.WITHDRAWAL && !it.isSelfTransfer && !it.isFailedPurchase }
            CategoryProgress(
                userCategorized = expenses.any { it.categoryId != null && !it.isAutoCategorized },
                hasUncategorized = expenses.any { it.categoryId == null },
                merchants = merchantCandidates(expenses),
            )
        }
    }
    // تا داده نیامده، قدم‌ها «انجام‌نشده» به نظر می‌رسند؛ پس کارت هم تا آن موقع پنهان است
    val progress by progressFlow.collectAsState(initial = null)
    val notification = rememberNotificationPrompt()
    val lock = rememberAppLock()
    // قدم نوتیف فقط وقتی هست که نوتیف خاموش بوده؛ ولی وقتی یک بار آمد، با روشن شدن نوتیف تیک می‌خورد و نمی‌رود
    val showNotification = !notification.enabled || prefs.notificationStepShown
    LaunchedEffect(showNotification) {
        if (showNotification) prefs.notificationStepShown = true
    }
    if (finished) return null
    val p = progress ?: return null
    // خلاصه هنوز نیامده ← معلوم نیست بودجه گذاشته شده یا نه
    val summary = s ?: return null

    val steps = buildList {
        add(merchantStep(p, onOpenUncategorized, onOpenTransactions, onOpenTransaction))
        if (showNotification) {
            add(
                FirstStep(
                    "notif",
                    stringResource(R.string.steps_notif),
                    stringResource(R.string.steps_notif_detail),
                    done = notification.enabled,
                    onClick = notification.fix,
                )
            )
        }
        val budgetSet = summary.overallBudgetRial != null || summary.categories.any { it.budgetRial != null }
        val suggestion = averageSpendRial?.let { suggestedBudgetRial(it) }
        add(
            FirstStep(
                "budget",
                stringResource(R.string.steps_budget),
                if (suggestion == null) stringResource(R.string.steps_budget_detail)
                else Jalali.toPersianDigits(stringResource(R.string.steps_budget_offer, Money.compact(suggestion))),
                done = budgetSet,
                offer = suggestion?.let {
                    StepOffer(
                        accept = Jalali.toPersianDigits(stringResource(R.string.steps_budget_accept, Money.compact(it))),
                        onAccept = { onSetBudget(it) },
                        other = stringResource(R.string.steps_budget_other),
                        onOther = onEditBudget,
                    )
                },
                onClick = onEditBudget,
            )
        )
        // گوشی بدون قفل صفحه، قفل اپ هم ندارد
        if (lock.available) {
            add(
                FirstStep(
                    "lock",
                    stringResource(R.string.steps_lock),
                    stringResource(R.string.steps_lock_detail),
                    done = lock.enabled,
                    onClick = { lock.set(true) },
                )
            )
        }
    }
    val hide = {
        prefs.finished = true
        finished = true
    }
    return FirstStepsUi(steps, hide, celebrating = steps.all { it.done })
}

/**
 * «۳ تا از فروشگاه‌های پرتکرارت رو بهم یاد بده»؛ اگر فروشگاهی برای یاد دادن نیست (مثلاً فقط ثبت دستی)،
 * همان قدم قدیمی «به یه خرج دسته بده».
 */
@Composable
private fun merchantStep(
    p: CategoryProgress,
    onOpenUncategorized: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
): FirstStep {
    val merchants = p.merchants
    if (merchants.isEmpty()) {
        return FirstStep(
            "cat",
            stringResource(R.string.steps_cat),
            stringResource(R.string.steps_cat_detail),
            done = p.userCategorized,
            onClick = if (p.hasUncategorized) onOpenUncategorized else onOpenTransactions,
        )
    }
    val taught = merchants.count { it.taught }
    val next = merchants.firstOrNull { !it.taught } ?: merchants.first()
    return FirstStep(
        "merchants",
        if (merchants.size == 1) stringResource(R.string.steps_merchants_one)
        else Jalali.toPersianDigits(stringResource(R.string.steps_merchants, merchants.size)),
        stringResource(R.string.steps_merchants_detail),
        done = taught == merchants.size,
        progress = if (taught > 0) Jalali.toPersianDigits(stringResource(R.string.steps_progress, taught, merchants.size)) else null,
        chips = merchants.map { m -> StepChip(m.name, m.uncategorized, m.taught) { onOpenTransaction(m.openId) } },
        onClick = { onOpenTransaction(next.openId) },
    )
}

private data class CategoryProgress(
    val userCategorized: Boolean,
    val hasUncategorized: Boolean,
    val merchants: List<MerchantCandidate>,
)

/**
 * یک فروشگاه برای قدم «یادم بده».
 * @param uncategorized چند خرج بی‌دسته دارد
 * @param taught کاربر خودش (نه خودکار) به دست‌کم یکی از خرج‌هایش دسته داده
 * @param openId خرجی که با لمس باز می‌شود: تازه‌ترین بی‌دسته، وگرنه تازه‌ترین
 */
internal data class MerchantCandidate(val name: String, val uncategorized: Int, val taught: Boolean, val openId: Long)

/**
 * پرتکرارترین فروشگاه‌ها (بیشترین تعداد خرج اول) که هنوز خرج بی‌دسته دارند یا کاربر یادشان داده؛
 * یادداده‌ها می‌مانند تا تیکشان دیده شود و فهرست زیر دست کاربر جابه‌جا نشود.
 * @param expenses فقط خرج‌های واقعی (نه انتقال به خود یا خرید ناموفق)
 */
internal fun merchantCandidates(expenses: List<Transaction>, limit: Int = 3): List<MerchantCandidate> =
    expenses
        .filter { it.merchant != null }
        .groupBy { it.merchant!! }
        .mapNotNull { (name, list) ->
            val uncategorized = list.filter { it.categoryId == null }
            val taught = list.any { it.categoryId != null && !it.isAutoCategorized }
            if (uncategorized.isEmpty() && !taught) return@mapNotNull null
            val open = (uncategorized.ifEmpty { list }).maxBy { it.dateMillis }
            list.size to MerchantCandidate(name, uncategorized.size, taught, open.id)
        }
        .sortedWith(compareByDescending<Pair<Int, MerchantCandidate>> { it.first }.thenBy { it.second.name })
        .take(limit)
        .map { it.second }

/**
 * بودجه‌ی پیشنهادی از میانگین خرج: به تومان تا دو رقم اول گرد می‌شود (۲۲٫۴ میلیون ← ۲۲ میلیون، ۹۵۶ هزار ← ۹۶۰ هزار)
 * تا عددی باشد که آدم خودش هم می‌گوید. null اگر میانگینی نیست.
 */
internal fun suggestedBudgetRial(averageRial: Long): Long? {
    val toman = averageRial / 10
    if (toman <= 0) return null
    var unit = 1L
    while (toman / unit >= 100) unit *= 10
    return (toman + unit / 2) / unit * unit * 10
}

/** کارت کجا باشد: چند روزِ اول باز کردن اپ بالای «خلاصه»، بعد در «کارها» */
internal object FirstStepsPlacement {
    const val SUMMARY_DAYS = 7

    fun onSummary(openDays: Int) = openDays <= SUMMARY_DAYS

    /** با باز شدن اپ در روز [today] (شماره‌ی روز): روز تازه یکی به شمار اضافه می‌کند، همان روز نه */
    fun recordOpen(lastDay: Long, openDays: Int, today: Long): Pair<Long, Int> =
        if (today == lastDay) lastDay to openDays else today to openDays + 1
}

/** کارت این بار بالای «خلاصه» است (وگرنه در «کارها»)؛ در طول نمایش صفحه عوض نمی‌شود */
@Composable
fun rememberFirstStepsOnSummary(): Boolean {
    val context = LocalContext.current
    return remember { FirstStepsPlacement.onSummary(FirstStepsPrefs(context.applicationContext).openDays) }
}

/** اپ امروز باز شد: برای شمردن روزهایی که کارت بالای «خلاصه» می‌ماند */
fun recordFirstStepsOpenDay(context: Context, today: Long = java.time.LocalDate.now().toEpochDay()) {
    FirstStepsPrefs(context.applicationContext).recordOpen(today)
}

/** «جیبت رو مرتب کنیم» تمام شده یا بسته شده، و روزهای باز کردن اپ. (اسم فایل از «قدم‌های اول» قبلی مانده تا بسته‌شده‌ها دوباره باز نشوند) */
private class FirstStepsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("first_steps", Context.MODE_PRIVATE)

    var finished: Boolean
        get() = prefs.getBoolean(KEY_FINISHED, false)
        set(value) = prefs.edit { putBoolean(KEY_FINISHED, value) }

    var notificationStepShown: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIF_SHOWN, value) }

    val openDays: Int get() = prefs.getInt(KEY_OPEN_DAYS, 0)

    fun recordOpen(today: Long) {
        val (day, days) = FirstStepsPlacement.recordOpen(prefs.getLong(KEY_LAST_DAY, Long.MIN_VALUE), openDays, today)
        prefs.edit {
            putLong(KEY_LAST_DAY, day)
            putInt(KEY_OPEN_DAYS, days)
        }
    }

    private companion object {
        const val KEY_FINISHED = "finished"
        const val KEY_NOTIF_SHOWN = "notif_step_shown"
        const val KEY_LAST_DAY = "last_open_day"
        const val KEY_OPEN_DAYS = "open_days"
    }
}
