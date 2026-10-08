package ir.jibito.app.ui.summary

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.wallet.AccountGrouping
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.domain.Transaction
import ir.jibito.app.notify.BudgetLevel
import ir.jibito.app.ui.common.HIDDEN_AMOUNT
import ir.jibito.app.ui.common.LocalHideAmounts
import ir.jibito.app.ui.common.ToastMessage
import ir.jibito.app.ui.smslist.shortBankName
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * همه‌ی «کارهای لازم» در یک جا: هم استوری‌های «خلاصه» و هم فهرست تب «کارها» از همین ساخته می‌شوند.
 * @param s خلاصه‌ی ماه (برای بودجه‌های نزدیک سقف)؛ null یعنی هنوز بارگذاری نشده
 * @param onToast پیام کوتاه بعد از جواب دادن (با «برگردون»)، مثلاً بعد از سؤال «دو حساب یکی‌اند یا جدا؟»
 */
@Composable
fun rememberTodoStories(
    s: MonthSummary?,
    pendingReview: Int,
    onOpenReview: () -> Unit,
    onOpenUncategorized: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCategory: (Long) -> Unit,
    onToast: (ToastMessage) -> Unit = {},
): List<TodoStory> {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val t = JibitoTheme.colors
    val hidden = LocalHideAmounts.current
    val transfersFlow = remember { app.container.transactionRepository.observeTransferSuggestions() }
    val transferSuggestions by transfersFlow.collectAsState(initial = emptyList())
    val oneOffFlow = remember { app.container.transactionRepository.observeOneOffSuggestions() }
    val oneOffSuggestions by oneOffFlow.collectAsState(initial = emptyList())
    val recurringFlow = remember { app.container.recurringSuggestions.observe() }
    val recurringSuggestions by recurringFlow.collectAsState(initial = emptyList())
    val uncategorizedFlow = remember { uncategorizedThisMonth(app) }
    val uncategorizedCount by uncategorizedFlow.collectAsState(initial = 0)
    val accountQuestionFlow = remember { app.container.accountRepository.observeQuestion() }
    val accountQuestion by accountQuestionFlow.collectAsState(initial = null)
    val forecastState by app.container.forecastRepository.state.collectAsState()
    val notificationPrompt = rememberNotificationPrompt()
    val scope = rememberCoroutineScope()

    return buildList {
        if (notificationPrompt.visible) {
            add(
                TodoStory(
                    "notif", t.amber, t.amberTint, t.amberTintFg, DesignIcons.Bell, null,
                    label = stringResource(R.string.todo_notif_off),
                    detail = stringResource(R.string.todo_notif_detail),
                    actions = listOf(TodoAction(stringResource(R.string.todo_turn_on), notificationPrompt.fix)),
                    onClick = notificationPrompt.fix,
                )
            )
        }
        if (uncategorizedCount > 0 && (s == null || s.month == JalaliMonth.current())) {
            add(
                TodoStory(
                    "uncat", t.coral, t.uncatBg, t.uncatFg, null,
                    Jalali.toPersianDigits(uncategorizedCount.coerceAtMost(99).toString()),
                    label = stringResource(R.string.todo_uncategorized),
                    detail = stringResource(R.string.todo_uncategorized_detail),
                    onClick = onOpenUncategorized,
                )
            )
        }
        s?.categories?.forEach { c ->
            val budget = c.budgetRial ?: return@forEach
            val level = BudgetLevel.of(c.budgetSpentRial, budget)
            if (level >= 80) {
                val tint = categoryTint(c.colorHex, c.icon)
                add(
                    TodoStory(
                        "budget-${c.categoryId}", if (level >= 100) t.alert else t.amber, tint.bg, tint.fg, tint.icon, tint.glyph,
                        label = Jalali.toPersianDigits("${c.name} ${c.budgetSpentRial * 100 / budget}٪"),
                        detail = Jalali.toPersianDigits(
                            stringResource(
                                R.string.todo_budget_detail,
                                if (hidden) HIDDEN_AMOUNT else Money.compact(c.budgetSpentRial),
                                if (hidden) HIDDEN_AMOUNT else Money.compactAdjective(budget),
                            )
                        ),
                        onClick = { onOpenCategory(c.categoryId) },
                    )
                )
            }
        }
        if (pendingReview > 0) {
            add(
                TodoStory(
                    "review", t.coral, t.uncatBg, t.uncatFg, DesignIcons.Message, null,
                    label = Jalali.toPersianDigits(stringResource(R.string.todo_review, pendingReview)),
                    detail = stringResource(R.string.review_help_me),
                    onClick = onOpenReview,
                )
            )
        }
        if (transferSuggestions.isNotEmpty()) {
            val repository = app.container.transactionRepository
            val yes = stringResource(R.string.todo_transfer_yes)
            val no = stringResource(R.string.transfer_no)
            val unknown = stringResource(R.string.bank_unknown)
            val otpLabel = stringResource(R.string.todo_sms_otp)
            // همه‌ی پیشنهادها در یک کارت ورق‌خور (تازه‌ترها اول)؛ هر کدام همان‌جا جواب داده می‌شود
            val pages = transferSuggestions.map { suggestion ->
                val w = suggestion.withdrawal
                val d = suggestion.deposit
                TodoPage(
                    key = "${w.id}-${d.id}",
                    detail = Jalali.toPersianDigits(
                        stringResource(
                            R.string.todo_transfer_page,
                            if (hidden) HIDDEN_AMOUNT else Money.compact(w.transaction.amountRial),
                            shortBankName(w.bank?.name ?: unknown),
                            shortBankName(d.bank?.name ?: unknown),
                            Jalali.dayTitle(w.dateMillis),
                        )
                    ),
                    actions = listOf(
                        TodoAction(yes) { scope.launch { repository.confirmTransfer(suggestion) } },
                        TodoAction(no) { scope.launch { repository.rejectTransfer(suggestion) } },
                    ),
                    sms = smsOf(w, otpLabel, stringResource(R.string.todo_sms_withdrawal, w.bank?.name ?: unknown)) +
                        smsOf(d, otpLabel, stringResource(R.string.todo_sms_deposit, d.bank?.name ?: unknown)),
                )
            }
            add(
                TodoStory(
                    "transfer", t.teal, t.transferBg, t.transferFg, DesignIcons.Transfer, null,
                    label = stringResource(R.string.todo_transfer),
                    pages = pages,
                    // این سؤال فقط همین‌جا پرسیده می‌شود (نه بالای «تراکنش‌ها»)؛ لمس هر مورد پیامک‌هایش را نشان می‌دهد
                    onClick = {},
                )
            )
        }
        if (oneOffSuggestions.isNotEmpty()) {
            val repository = app.container.transactionRepository
            val yes = stringResource(R.string.todo_one_off_yes)
            val no = stringResource(R.string.todo_one_off_no)
            val unknown = stringResource(R.string.bank_unknown)
            val otpLabel = stringResource(R.string.todo_sms_otp)
            // همه‌ی پیشنهادها در یک کارت؛ کاربر چپ و راست می‌کشد و هر کدام را که خواست جواب می‌دهد (بزرگ‌ترین اول)
            val pages = oneOffSuggestions.map { tx ->
                val amountText = if (hidden) HIDDEN_AMOUNT else Money.compact(tx.transaction.amountRial)
                val name = tx.merchant ?: tx.categoryName ?: tx.note
                val date = Jalali.dayTitle(tx.dateMillis)
                TodoPage(
                    key = tx.id.toString(),
                    detail = Jalali.toPersianDigits(
                        if (name != null) stringResource(R.string.todo_one_off_detail_named, amountText, name, date)
                        else stringResource(R.string.todo_one_off_detail, amountText, date)
                    ),
                    // «نه» یعنی دیگر درباره‌ی همین خرید پرسیده نمی‌شود
                    actions = listOf(
                        TodoAction(yes) { scope.launch { repository.setOneOff(tx.id, true) } },
                        TodoAction(no) { scope.launch { repository.setOneOff(tx.id, false) } },
                    ),
                    sms = smsOf(tx, otpLabel, stringResource(R.string.todo_sms_withdrawal, tx.bank?.name ?: unknown)),
                )
            }
            add(
                TodoStory(
                    "oneoff", t.teal, t.sugBg, t.sugFg, DesignIcons.Star, null,
                    label = stringResource(R.string.todo_one_off),
                    pages = pages,
                    // لمس هر مورد پیامک‌هایش را نشان می‌دهد (نه کل فهرست تراکنش‌ها)
                    onClick = {},
                )
            )
        }
        accountQuestion?.let { q ->
            val accounts = app.container.accountRepository
            val bankName = BankDirectory.byId(q.bankId)?.name?.let(::shortBankName).orEmpty()
            val sameText = stringResource(R.string.account_answered_same)
            val separateText = stringResource(R.string.account_answered_separate)
            val undoLabel = stringResource(R.string.undo)
            fun answer(same: Boolean) {
                scope.launch {
                    val undo = accounts.answer(q, same)
                    onToast(ToastMessage(if (same) sameText else separateText, undoLabel, onAction = { scope.launch { accounts.undo(undo) } }))
                }
            }
            add(
                TodoStory(
                    "account", t.teal, t.transferBg, t.transferFg, DesignIcons.Bank, null,
                    label = stringResource(R.string.account_question_label, bankName),
                    detail = Jalali.toPersianDigits(
                        stringResource(
                            R.string.account_question_detail,
                            AccountGrouping.shortNumber(q.account),
                            AccountGrouping.shortNumber(q.other),
                        )
                    ),
                    actions = listOf(
                        TodoAction(stringResource(R.string.account_question_same)) { answer(same = true) },
                        TodoAction(stringResource(R.string.account_question_separate)) { answer(same = false) },
                    ),
                    onClick = onOpenSettings,
                )
            )
        }
        forecastState?.suggestion?.let { salary ->
            val bank = BankDirectory.byId(salary.bankId)?.name?.let(::shortBankName).orEmpty()
            add(
                TodoStory(
                    "salary", t.teal, t.tealTint, t.tealTintFg, DesignIcons.Bank, null,
                    label = stringResource(R.string.todo_salary),
                    detail = Jalali.toPersianDigits(
                        stringResource(
                            R.string.todo_salary_detail,
                            if (hidden) HIDDEN_AMOUNT else Money.compact(salary.amountRial),
                            salary.dayOfMonth,
                            bank,
                        )
                    ),
                    actions = listOf(
                        TodoAction(stringResource(R.string.salary_yes)) { app.container.forecastRepository.confirmSalary(salary) },
                        TodoAction(stringResource(R.string.salary_no)) { app.container.forecastRepository.rejectSalary(salary) },
                    ),
                    onClick = {},
                )
            )
        }
        recurringSuggestions.firstOrNull()?.let { r ->
            add(
                TodoStory(
                    "rec", t.teal, t.tealTint, t.tealTintFg, DesignIcons.Repeat, null,
                    label = stringResource(R.string.todo_recurring, r.title),
                    detail = stringResource(R.string.todo_recurring_detail),
                    actions = listOf(
                        TodoAction(stringResource(R.string.recurring_suggest_yes)) { scope.launch { app.container.recurringSuggestions.accept(r) } },
                        TodoAction(stringResource(R.string.recurring_suggest_no)) { app.container.recurringSuggestions.dismiss(r) },
                    ),
                    onClick = onOpenSettings,
                )
            )
        }
    }
}

/** ترتیب تب «کارها»: اول چیزی که بدونش عددها غلط است، آخر تنظیمات */
fun todoPriority(id: String): Int = when {
    id == "review" -> 0
    id == "uncat" -> 1
    id.startsWith("budget-") -> 2
    id == "transfer" -> 3
    id == "oneoff" -> 3
    id == "account" -> 4
    id == "rec" || id == "salary" -> 5
    else -> 6
}

/** پیامک‌های یک تراکنش برای برگه‌ی «پیامک‌ها»: اول رمز دوم (اگر به آن وصل شده)، بعد خود پیامک؛ ثبت دستی پیامک ندارد */
private fun smsOf(tx: Transaction, otpLabel: String, label: String): List<TodoSms> = listOfNotNull(
    tx.otpBody?.takeIf { it.isNotBlank() }?.let { TodoSms(otpLabel, null, it) },
    tx.body.takeIf { it.isNotBlank() && !tx.isManual }?.let { TodoSms(label, tx.dateMillis, it) },
)

/** چند خرج این ماه هنوز دسته ندارند */
private fun uncategorizedThisMonth(app: Context) =
    (app as JibitoApplication).container.transactionRepository.observeTransactions().map { list ->
        val m = JalaliMonth.current()
        val from = m.startMillis()
        val to = m.endMillis()
        list.count {
            it.categoryId == null && !it.isSelfTransfer && !it.isFailedPurchase &&
                it.transaction.type == FlowType.WITHDRAWAL && it.dateMillis in from until to
        }
    }
