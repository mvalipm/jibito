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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.ui.common.EmptyStart
import ir.jibito.app.ui.common.rememberNoTransactions
import ir.jibito.app.ui.common.rememberSmsAccess
import ir.jibito.app.ui.theme.JibitoText
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.ui.common.CategoryIconTile
import ir.jibito.app.ui.common.StatusBarOnColor
import ir.jibito.app.ui.common.amount
import ir.jibito.app.data.parser.FlowType
import kotlinx.coroutines.delay
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
 * ۱) سرصفحه‌ی رنگی: خرج این ماه، بودجه و حال جیب. ۲) «کجا رفت؟» با نوار سهم دسته‌ها. ۳) روند ۶ ماه اخیر.
 * «کارهای لازم» اینجا نیست (تب خودش را دارد) تا خلاصه خلوت بماند.
 * فهرست کامل دسته‌ها، بودجه‌ها و درآمدها یک لمس دورتر است («همه‌ی دسته‌ها و بودجه‌ها»).
 */
/** اولین باز شدن «خلاصه»: حداکثر این‌قدر منتظر پیش‌بینی موجودی می‌ماند */
private const val FORECAST_WAIT_MS = 1_500L

@Composable
fun SummaryScreen(
    /** دکمه‌ی تنظیمات بالای سرصفحه */
    onOpenSettings: () -> Unit = {},
    /** دسته‌ای که باید جزئیاتش باز شود (لمس کارت بودجه در تب «کارها»)؛ ماه جاری نشان داده می‌شود */
    openCategoryId: Long? = null,
    /** بعد از باز کردن openCategoryId صدا زده می‌شود تا دوباره باز نشود */
    onCategoryOpened: () -> Unit = {},
    /** «ثبت اولین خرج» وقتی هنوز هیچ تراکنشی نیست */
    onAddManual: () -> Unit = {},
    /** لمس «پولت تا حقوق می‌رسه؟» ← کارت موجودی در «گزارش‌ها» */
    onOpenForecast: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val forecastState by app.container.forecastRepository.state.collectAsState()
    // پیش‌بینی موجودی رنگ سرصفحه را عوض می‌کند؛ اولین بار کمی صبر کن تا برسد (نه بیشتر از FORECAST_WAIT_MS)،
    // تا سرصفحه اول «آروم» و بعد رنگ دیگر نشود. دفعه‌های بعد پیش‌بینی قبلی همان اول هست.
    var forecastWaitOver by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(FORECAST_WAIT_MS)
        forecastWaitOver = true
    }
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
    val themeSettings = app.container.themeSettings
    val hidden by themeSettings.hideAmounts.collectAsState()
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    BackHandler(enabled = showAll) { showAll = false }
    val noTransactions = rememberNoTransactions()
    val smsAccess = rememberSmsAccess()
    LaunchedEffect(openCategoryId) {
        val id = openCategoryId ?: return@LaunchedEffect
        viewModel.setMonth(JalaliMonth.current())
        showAll = false
        detailId = id
        onCategoryOpened()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val s = summary
        if (s == null || s.month != month || (forecastState == null && !forecastWaitOver)) {
            SummarySkeleton()
        } else if (!showAll) {
            // سرصفحه‌ی رنگی زیر نوار وضعیت می‌رود؛ آیکون‌های نوار وضعیت سفید
            StatusBarOnColor()
            val heroLine = moodOf(s, System.currentTimeMillis(), forecastState?.forecast)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp + LocalBottomBarSpace.current),
            ) {
                item(key = "hero") {
                    SummaryHero(
                        s,
                        onPickMonth = viewModel::setMonth,
                        onEditBudget = { editingOverall = true },
                        onToggleHidden = { themeSettings.setHideAmounts(!hidden) },
                        onOpenSettings = onOpenSettings,
                        forecast = forecastState?.forecast,
                        onOpenForecast = onOpenForecast,
                        compact = noTransactions == true,
                    )
                }
                // هنوز هیچ تراکنشی نیست: به‌جای عددهای صفر، چرا خالی است و قدم بعدی
                if (noTransactions == true) {
                    item(key = "start") {
                        EmptyStart(
                            smsAccess.granted,
                            onAddManual = onAddManual,
                            onAllowSms = smsAccess.allow,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
                            card = true,
                        )
                    }
                }
                // «کجا رفت؟» بی هیچ تراکنشی فقط یک «خرجی نیومده»ی تکراری است
                if (noTransactions != true) {
                    item(key = "where") {
                        WhereSection(s, onOpenCategory = { detailId = it }, onShowAll = { showAll = true })
                    }
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
                    style = JibitoText.screenTitle,
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
                            style = JibitoText.sectionTitle,
                            fontWeight = FontWeight.Black,
                            color = colors.onBackground,
                        )
                        Text(
                            stringResource(R.string.summary_tap_for_detail),
                            style = JibitoText.body,
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
                            style = JibitoText.action,
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
                            style = JibitoText.sectionTitle,
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
    // خرج یک‌باره از بودجه کم نمی‌شود
    val level = if (budget != null) BudgetLevel.of(c.budgetSpentRial, budget) else 0
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
                            level == 100 -> stringResource(R.string.summary_over_budget, amount(Money.toman(c.budgetSpentRial - budget)))
                            else -> stringResource(R.string.summary_remaining, amount(Money.toman(budget - c.budgetSpentRial)))
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
                val fraction = (c.budgetSpentRial.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
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
                    val percent = (c.budgetSpentRial * 100 / budget).coerceAtMost(999)
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

/**
 * تا عددهای ماه برسند: شکل سرصفحه و دو ردیف کم‌رنگ، به‌جای چرخنده‌ی وسط صفحه‌ی خالی
 * (صفحه سر جایش می‌ماند و با رسیدن عددها فقط پر می‌شود). برای TalkBack یک «در حال آماده شدن».
 */
@Composable
private fun SummarySkeleton() {
    val t = JibitoTheme.colors
    val loading = stringResource(R.string.summary_loading)
    Column(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics { contentDescription = loading },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(t.chip, RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
        )
        Spacer(Modifier.height(28.dp))
        Box(Modifier.padding(horizontal = 20.dp).size(width = 110.dp, height = 20.dp).background(t.chip, RoundedCornerShape(10.dp)))
        Spacer(Modifier.height(16.dp))
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(34.dp).background(t.chip, RoundedCornerShape(10.dp)))
    }
}
