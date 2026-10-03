package ir.jibito.app.ui.summary

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.notify.BudgetLevel
import ir.jibito.app.ui.common.HIDDEN_AMOUNT
import ir.jibito.app.ui.common.LocalHideAmounts
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
import kotlinx.coroutines.flow.map

/**
 * همه‌ی «کارهای لازم» در یک جا: هم استوری‌های «خلاصه» و هم فهرست تب «کارها» از همین ساخته می‌شوند.
 * @param s خلاصه‌ی ماه (برای بودجه‌های نزدیک سقف)؛ null یعنی هنوز بارگذاری نشده
 */
@Composable
fun rememberTodoStories(
    s: MonthSummary?,
    pendingReview: Int,
    onOpenReview: () -> Unit,
    onOpenUncategorized: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCategory: (Long) -> Unit,
): List<TodoStory> {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val t = JibitoTheme.colors
    val hidden = LocalHideAmounts.current
    val transfersFlow = remember { app.container.transactionRepository.observeTransferSuggestions() }
    val transferSuggestions by transfersFlow.collectAsState(initial = emptyList())
    val recurringFlow = remember { app.container.recurringSuggestions.observe() }
    val recurringSuggestions by recurringFlow.collectAsState(initial = emptyList())
    val uncategorizedFlow = remember { uncategorizedThisMonth(app) }
    val uncategorizedCount by uncategorizedFlow.collectAsState(initial = 0)
    val notificationPrompt = rememberNotificationPrompt()

    return buildList {
        if (notificationPrompt.visible) {
            add(
                TodoStory(
                    "notif", t.amber, t.amberTint, t.amberTintFg, DesignIcons.Bell, null,
                    label = stringResource(R.string.todo_notif_off),
                    detail = stringResource(R.string.todo_notif_detail),
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
            val level = BudgetLevel.of(c.spentRial, budget)
            if (level >= 80) {
                val tint = categoryTint(c.colorHex, c.icon)
                add(
                    TodoStory(
                        "budget-${c.categoryId}", if (level >= 100) t.alert else t.amber, tint.bg, tint.fg, tint.icon, tint.glyph,
                        label = Jalali.toPersianDigits("${c.name} ${c.spentRial * 100 / budget}٪"),
                        detail = Jalali.toPersianDigits(
                            stringResource(
                                R.string.todo_budget_detail,
                                if (hidden) HIDDEN_AMOUNT else Money.compact(c.spentRial),
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
            add(
                TodoStory(
                    "transfer", t.teal, t.transferBg, t.transferFg, DesignIcons.Transfer, null,
                    label = stringResource(R.string.todo_transfer),
                    detail = stringResource(R.string.todo_transfer_detail),
                    onClick = onOpenTransactions,
                )
            )
        }
        recurringSuggestions.firstOrNull()?.let { r ->
            add(
                TodoStory(
                    "rec", t.teal, t.tealTint, t.tealTintFg, DesignIcons.Repeat, null,
                    label = stringResource(R.string.todo_recurring, r.title),
                    detail = stringResource(R.string.todo_recurring_detail),
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
    id == "rec" -> 4
    else -> 5
}

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
