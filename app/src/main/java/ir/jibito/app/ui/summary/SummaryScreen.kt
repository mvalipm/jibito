package ir.jibito.app.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.DarkMode
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.ui.common.CategoryIconTile
import ir.jibito.app.ui.common.StatusBarOnColor
import ir.jibito.app.ui.common.amount
import ir.jibito.app.data.parser.FlowType
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.data.repository.CategorySpend
import ir.jibito.app.notify.BudgetLevel
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
import ir.jibito.app.ui.theme.JibitoIcons

/**
 * صفحه‌ی «خلاصه» (طرح «جیبی»):
 * ۱) سرصفحه‌ی رنگی: خرج این ماه، بودجه و حال جیب. ۲) «کارهای لازم» به شکل استوری.
 * ۳) «کجا رفت؟» با نوار سهم دسته‌ها. ۴) روند ۶ ماه اخیر.
 * فهرست کامل دسته‌ها، بودجه‌ها و درآمدها یک لمس دورتر است («همه‌ی دسته‌ها و بودجه‌ها»).
 */
@Composable
fun SummaryScreen(
    /** پیامک‌های منتظر بررسی */
    pendingReview: Int = 0,
    onOpenReview: () -> Unit = {},
    /** رفتن به تراکنش‌ها با فیلتر «فقط بی‌دسته» */
    onOpenUncategorized: () -> Unit = {},
    /** رفتن به تراکنش‌ها (مثلاً برای پیشنهادهای انتقال به خودم) */
    onOpenTransactions: () -> Unit = {},
    /** رفتن به تنظیمات (مثلاً برای پیشنهاد پرداخت ماهانه) */
    onOpenSettings: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val viewModel: SummaryViewModel = viewModel(
        factory = SummaryViewModel.factory(app.container.budgetRepository)
    )
    val month by viewModel.month.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val trend by viewModel.trend.collectAsState()
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingOverall by rememberSaveable { mutableStateOf(false) }
    // دسته‌ای که جزئیاتش باز است
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showIdle by rememberSaveable { mutableStateOf(false) }
    // فهرست کامل دسته‌ها و بودجه‌ها (به‌جای نمای اصلی)
    var showAll by rememberSaveable { mutableStateOf(false) }
    val transfersFlow = remember { app.container.transactionRepository.observeTransferSuggestions() }
    val transferSuggestions by transfersFlow.collectAsState(initial = emptyList())
    val recurringFlow = remember { app.container.recurringSuggestions.observe() }
    val recurringSuggestions by recurringFlow.collectAsState(initial = emptyList())
    // چند خرج این ماه هنوز دسته ندارند (عدد استوری «خرج بی‌دسته»)
    val uncategorizedFlow = remember {
        app.container.transactionRepository.observeTransactions().map { list ->
            val m = JalaliMonth.current()
            val from = m.startMillis()
            val to = m.endMillis()
            list.count {
                it.categoryId == null && !it.isSelfTransfer && !it.isFailedPurchase &&
                    it.transaction.type == FlowType.WITHDRAWAL && it.dateMillis in from until to
            }
        }
    }
    val uncategorizedCount by uncategorizedFlow.collectAsState(initial = 0)
    val notificationPrompt = rememberNotificationPrompt()
    val themeSettings = app.container.themeSettings
    val hidden by themeSettings.hideAmounts.collectAsState()
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    BackHandler(enabled = showAll) { showAll = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val s = summary
        if (s == null || s.month != month) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (!showAll) {
            // سرصفحه‌ی رنگی زیر نوار وضعیت می‌رود؛ آیکون‌های نوار وضعیت سفید
            StatusBarOnColor()
            val stories = buildList {
                if (notificationPrompt.visible) {
                    add(TodoStory("notif", t.amber, t.amberTint, t.amberTintFg, DesignIcons.Bell, null, stringResource(R.string.todo_notif_off), notificationPrompt.fix))
                }
                if (uncategorizedCount > 0 && s.month == JalaliMonth.current()) {
                    add(
                        TodoStory(
                            "uncat", t.coral, t.uncatBg, t.uncatFg, null,
                            Jalali.toPersianDigits(uncategorizedCount.coerceAtMost(99).toString()),
                            stringResource(R.string.todo_uncategorized), onOpenUncategorized,
                        )
                    )
                }
                s.categories.forEach { c ->
                    val budget = c.budgetRial ?: return@forEach
                    val level = BudgetLevel.of(c.spentRial, budget)
                    if (level >= 80) {
                        val tint = categoryTint(c.colorHex, c.icon)
                        add(
                            TodoStory(
                                "budget-${c.categoryId}", if (level >= 100) t.alert else t.amber, tint.bg, tint.fg, tint.icon, tint.glyph,
                                Jalali.toPersianDigits("${c.name} ${c.spentRial * 100 / budget}٪"),
                            ) { detailId = c.categoryId }
                        )
                    }
                }
                if (pendingReview > 0) {
                    add(
                        TodoStory(
                            "review", t.coral, t.uncatBg, t.uncatFg, DesignIcons.Message, null,
                            Jalali.toPersianDigits(stringResource(R.string.todo_review, pendingReview)), onOpenReview,
                        )
                    )
                }
                if (transferSuggestions.isNotEmpty()) {
                    add(TodoStory("transfer", t.teal, t.transferBg, t.transferFg, DesignIcons.Transfer, null, stringResource(R.string.todo_transfer), onOpenTransactions))
                }
                recurringSuggestions.firstOrNull()?.let { r ->
                    add(TodoStory("rec", t.teal, t.tealTint, t.tealTintFg, DesignIcons.Repeat, null, stringResource(R.string.todo_recurring, r.title), onOpenSettings))
                }
            }
            val heroLine = moodOf(s, System.currentTimeMillis())
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp + LocalBottomBarSpace.current),
            ) {
                item(key = "hero") {
                    SummaryHero(
                        s,
                        onPickMonth = viewModel::setMonth,
                        onEditBudget = { editingOverall = true },
                        dark = t.dark,
                        onToggleDark = { themeSettings.setDarkMode(if (t.dark) DarkMode.LIGHT else DarkMode.DARK) },
                        onToggleHidden = { themeSettings.setHideAmounts(!hidden) },
                    )
                }
                if (stories.isNotEmpty()) item(key = "todo") { TodoSection(stories) }
                item(key = "where") {
                    WhereSection(s, onOpenCategory = { detailId = it }, onShowAll = { showAll = true })
                }
                trend?.takeIf { tr -> tr.months.lastOrNull()?.month == s.month && tr.months.any { it.spentRial > 0 } }?.let { tr ->
                    item(key = "trend") {
                        TrendCard(
                            tr,
                            highlight = when (heroLine.mood) {
                                Mood.CALM -> t.moodCalm
                                Mood.WARN -> t.moodWarn
                                Mood.OVER -> t.moodOver
                            },
                        )
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showAll = false }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(JibitoIcons.Back, contentDescription = stringResource(R.string.cd_back), tint = colors.onBackground, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.glance_all_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.onBackground,
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp + LocalBottomBarSpace.current),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Column(Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp)) {
                        Text(
                            stringResource(R.string.summary_categories) + " · " + s.month.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.onBackground,
                        )
                        Text(
                            stringResource(R.string.summary_tap_for_detail),
                            fontSize = 13.sp,
                            color = t.muted,
                        )
                    }
                }
                // دسته‌هایی که این ماه خرج یا بودجه دارند؛ بقیه در یک ردیف تاشو
                val active = s.categories.filter { it.spentRial > 0 || it.budgetRial != null }
                val idle = s.categories - active.toSet()
                items(active, key = { it.categoryId }) { c ->
                    CategoryRow(c, onClick = { detailId = c.categoryId })
                }
                if (idle.isNotEmpty()) {
                    item(key = "idle-toggle") {
                        Text(
                            Jalali.toPersianDigits(
                                stringResource(if (showIdle) R.string.summary_hide_idle else R.string.summary_show_idle, idle.size)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showIdle = !showIdle }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                        )
                    }
                    if (showIdle) {
                        items(idle, key = { it.categoryId }) { c ->
                            CategoryRow(c, onClick = { detailId = c.categoryId })
                        }
                    }
                }

                // درآمدها (بدون بودجه)
                if (s.incomeCategories.isNotEmpty() || s.uncategorizedIncomeRial > 0) {
                    item {
                        Text(
                            stringResource(R.string.summary_income_categories),
                            modifier = Modifier.padding(top = 18.dp, start = 4.dp, end = 4.dp),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.onBackground,
                        )
                    }
                    items(s.incomeCategories, key = { "in-" + it.categoryId }) { c -> IncomeRow(c.colorHex, c.icon, c.name, c.spentRial) }
                    if (s.uncategorizedIncomeRial > 0) {
                        item { IncomeRow(null, null, stringResource(R.string.summary_uncategorized), s.uncategorizedIncomeRial) }
                    }
                }
            }
            }
        }
        if (s != null && s.month == month) {
            s.categories.firstOrNull { it.categoryId == detailId }?.let { c ->
                CategoryDetailSheet(
                    c = c,
                    monthTitle = s.month.title,
                    onEditBudget = { editing = c.categoryId },
                    onDismiss = { detailId = null },
                )
            }
            s.categories.firstOrNull { it.categoryId == editing }?.let { c ->
                BudgetDialog(
                    key = "cat-${c.categoryId}",
                    title = stringResource(R.string.budget_dialog_title, c.name),
                    hint = stringResource(R.string.budget_dialog_hint),
                    initialRial = c.budgetRial,
                    suggestionRial = null,
                    onSave = { rial ->
                        viewModel.setBudget(c.categoryId, rial)
                        editing = null
                    },
                    onDismiss = { editing = null },
                )
            }
            if (editingOverall) {
                BudgetDialog(
                    key = "overall",
                    title = stringResource(R.string.overall_dialog_title),
                    hint = stringResource(R.string.overall_dialog_hint),
                    initialRial = s.overallBudgetRial,
                    suggestionRial = s.categoryBudgetsSumRial.takeIf { it > 0 && it != s.overallBudgetRial },
                    onSave = { rial ->
                        viewModel.setOverallBudget(rial)
                        editingOverall = false
                    },
                    onDismiss = { editingOverall = false },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(c: CategorySpend, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = categoryTint(c.colorHex, c.icon)
    val base = tint.fg
    val budget = c.budgetRial
    val level = if (budget != null) BudgetLevel.of(c.spentRial, budget) else 0
    val barColor = when (level) {
        100 -> JibitoTheme.colors.alert
        80 -> JibitoTheme.colors.amber
        else -> base
    }
    val idle = c.spentRial == 0L && budget == null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconTile(tint, size = 44.dp, radius = 15.dp, iconSize = 22.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        c.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (idle) colors.onSurfaceVariant else colors.onSurface,
                    )
                    Text(
                        text = when {
                            budget == null -> stringResource(R.string.summary_no_budget)
                            level == 100 -> stringResource(R.string.summary_over_budget, amount(Money.toman(c.spentRial - budget)))
                            else -> stringResource(R.string.summary_remaining, amount(Money.toman(budget - c.spentRial)))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (level == 100) colors.error else colors.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        amount(Money.toman(c.spentRial)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = if (idle) colors.onSurfaceVariant else colors.onSurface,
                    )
                    if (budget != null) {
                        Text(
                            stringResource(R.string.summary_of_budget, amount(Money.toman(budget))),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            if (budget != null && budget > 0) {
                Spacer(Modifier.height(10.dp))
                val fraction = (c.spentRial.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp),
                        color = barColor,
                        trackColor = barColor.copy(alpha = 0.15f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                    Spacer(Modifier.size(10.dp))
                    val percent = (c.spentRial * 100 / budget).coerceAtMost(999)
                    Text(
                        Jalali.toPersianDigits("$percent٪"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = barColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun IncomeRow(colorHex: String?, icon: String?, name: String, amountRial: Long) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconTile(categoryTint(colorHex, icon), size = 44.dp, radius = 15.dp, iconSize = 22.dp)
        Spacer(Modifier.size(12.dp))
        Text(
            name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        Text(
            amount("+ " + Money.toman(amountRial)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = JibitoTheme.colors.income,
        )
    }
}
