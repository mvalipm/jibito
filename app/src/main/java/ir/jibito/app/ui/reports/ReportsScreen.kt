package ir.jibito.app.ui.reports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.ui.common.Tip
import ir.jibito.app.ui.common.TipCard
import ir.jibito.app.ui.common.rememberTip
import ir.jibito.app.data.repository.MonthReport
import ir.jibito.app.data.repository.SpendTrend
import ir.jibito.app.data.wallet.AccountRef
import ir.jibito.app.data.wallet.BalanceOverview
import ir.jibito.app.ui.common.LocalHideAmounts
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * تب «گزارش‌ها»: «در طول زمان چه الگویی دارم؟» — هر گزارش یک دکمه است و هر بار فقط کارت یکی باز است:
 * ۱) «خرج»: این ماه (تجمعی، در برابر ماه قبل، با بودجه و پیش‌بینی آخر ماه) یا ۶/۱۲ ماه اخیر.
 * ۲) «موجودی حساب‌ها»: روند موجودی از روی مانده‌ی پیامک‌ها، در همان بازه (BalanceCard).
 * ۳) «جیبی چی فهمید؟»: نکته‌های کوتاه از الگوی خرج (دسته‌های بیشتر/کمتر، پرخرج‌ترین روز، الگوی هفته).
 * انتخاب بازه داخل کارت‌هایی است که بازه دارند و بین آن‌ها مشترک است؛ دکمه‌ی بسته خلاصه‌ی یک‌خطی همان بازه را دارد.
 * کارت باز موقع ورود: گزارشی که کاربر خیلی بیشتر از بقیه باز می‌کند (ReportSections.favorite)، وگرنه «خرج».
 * «خلاصه» به «الان وضعم چطوره؟» جواب می‌دهد؛ این تب به روند و مقایسه.
 */
@Composable
fun ReportsScreen(onOpenAccount: (AccountRef?) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as JibitoApplication
    val viewModel: ReportsViewModel = viewModel(
        factory = ReportsViewModel.factory(app.container.reportRepository, app.container.budgetRepository, app.container.balanceRepository)
    )
    val range by viewModel.range.collectAsState()
    val report by viewModel.report.collectAsState()
    val months by viewModel.months.collectAsState()
    val balances by viewModel.balances.collectAsState()
    val prefs = remember { ReportSectionPrefs(context) }
    var open by rememberSaveable { mutableStateOf<ReportSection?>(prefs.favorite()) }
    var seen by remember { mutableStateOf(prefs.seenInsights) }
    val insights = report?.insights.orEmpty()
    // نکته‌ها وقتی دیده شده‌اند که کارتشان باز است (با لمس یا چون موقع ورود باز بود)
    LaunchedEffect(open, insights) {
        if (open == ReportSection.INSIGHTS && insights.isNotEmpty()) {
            prefs.markSeen(insights)
            seen = prefs.seenInsights
        }
    }
    ReportsContent(
        month = viewModel.month,
        range = range,
        report = report,
        months = months,
        onRange = viewModel::setRange,
        balances = balances,
        onOpenAccount = onOpenAccount,
        open = open,
        onToggle = { section ->
            open = if (open == section) null else section.also(prefs::recordOpen)
        },
        newInsights = ReportSections.hasNew(insights, seen),
        thinTip = rememberTip(Tip.REPORTS_THIN).let { if (it.visible) it.dismiss else null },
    )
}

/** محتوای تب، جدا از ViewModel (برای اسکرین‌شات‌ها) */
@Composable
fun ReportsContent(
    month: JalaliMonth,
    range: ReportRange,
    report: MonthReport?,
    months: SpendTrend?,
    onRange: (ReportRange) -> Unit,
    modifier: Modifier = Modifier,
    /** «روند موجودی» در همین بازه؛ null یعنی «هنوز در حال بارگذاری» */
    balances: BalanceOverview? = null,
    /** جزئیات یک حساب (null = همه‌ی حساب‌ها) */
    onOpenAccount: (AccountRef?) -> Unit = {},
    /** کارت باز؛ null یعنی همه بسته‌اند */
    open: ReportSection? = ReportSection.SPEND,
    onToggle: (ReportSection) -> Unit = {},
    /** «جیبی چی فهمید؟» نکته‌ای دارد که هنوز دیده نشده */
    newInsights: Boolean = false,
    /** نکته‌ی «ماه اول» هنوز بسته نشده؛ بستنش. null یعنی نشان داده نشود */
    thinTip: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val t = JibitoTheme.colors
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val bottomSpace = LocalBottomBarSpace.current
    val topInset = WindowInsets.statusBars.getTop(density)
    // کارتی که تازه باز شد اگر از پایین صفحه بیرون زده، بالا بیاید تا کامل دیده شود
    val toggle: (ReportSection) -> Unit = { section ->
        val opening = open != section
        onToggle(section)
        if (opening) scope.launch {
            delay(EXPAND_MILLIS.toLong())
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == section.name }
            if (item == null) {
                listState.animateScrollToItem(1 + section.ordinal)
            } else {
                val visibleEnd = info.viewportEndOffset - with(density) { bottomSpace.roundToPx() }
                val gap = with(density) { 8.dp.roundToPx() }
                if (item.offset < topInset || item.offset + item.size > visibleEnd) {
                    listState.animateScrollBy((item.offset - topInset - gap).toFloat())
                }
            }
        }
    }
    val insights = report?.insights.orEmpty()
    LazyColumn(
        modifier
            .fillMaxSize()
            .background(colors.background),
        state = listState,
        contentPadding = PaddingValues(bottom = 16.dp + bottomSpace),
    ) {
        item(key = "header") { Header(month) }
        item(key = ReportSection.SPEND.name) {
            SectionCard(
                title = stringResource(R.string.reports_section_spend),
                icon = DesignIcons.NAV_REPORTS,
                iconBg = t.tealTint,
                iconFg = t.tealTintFg,
                summary = spendSummary(range, report, months),
                expanded = open == ReportSection.SPEND,
                onToggle = { toggle(ReportSection.SPEND) },
            ) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp)) {
                    RangeSelector(range, onRange)
                    when {
                        range == ReportRange.MONTH && report != null -> MonthChart(report.curve)
                        range != ReportRange.MONTH && months != null -> MonthsChart(months)
                        else -> Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    // ماه اول: هنوز ماه قبلی برای مقایسه و پیش‌بینی نیست
                    if (thinTip != null && range == ReportRange.MONTH && report != null && report.curve.isCurrent &&
                        report.curve.previous.all { it == 0L }
                    ) {
                        TipCard(stringResource(R.string.tip_reports_thin), onDismiss = thinTip, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
        item(key = ReportSection.BALANCE.name) {
            SectionCard(
                title = stringResource(R.string.balance_title),
                icon = DesignIcons.BANK,
                iconBg = t.navInd,
                iconFg = t.navOn,
                summary = balanceSummary(balances),
                expanded = open == ReportSection.BALANCE,
                onToggle = { toggle(ReportSection.BALANCE) },
            ) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp)) {
                    RangeSelector(range, onRange)
                    BalanceContent(balances, onOpenAccount, showTitle = false)
                }
            }
        }
        item(key = ReportSection.INSIGHTS.name) {
            SectionCard(
                title = stringResource(R.string.reports_insights_title),
                icon = DesignIcons.SPARKLE,
                iconBg = t.amberTint,
                iconFg = t.amberTintFg,
                summary = when {
                    report == null -> null
                    insights.isEmpty() -> stringResource(R.string.reports_insights_none)
                    else -> stringResource(R.string.reports_insights_count, Jalali.toPersianDigits(insights.size.toString()))
                },
                expanded = open == ReportSection.INSIGHTS,
                isNew = newInsights,
                onToggle = { toggle(ReportSection.INSIGHTS) },
            ) {
                when {
                    report == null -> Box(Modifier.fillMaxWidth().height(172.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    insights.isEmpty() -> Box(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp)) {
                        EmptyNote(stringResource(R.string.reports_insights_none))
                    }
                    else -> InsightsRow(insights, month, PaddingValues(start = 18.dp, end = 18.dp, bottom = 16.dp))
                }
            }
        }
    }
}

/** مدت باز/بسته شدن کارت‌ها */
private const val EXPAND_MILLIS = 250

/**
 * دکمه‌ی یک گزارش: آیکون، عنوان و (وقتی بسته است) خلاصه‌ی یک‌خطی؛ لمسش کارت زیرش را باز یا بسته می‌کند.
 * @param isNew نقطه‌ی رنگی کنار عنوان: چیز تازه‌ای هست که هنوز دیده نشده
 */
@Composable
private fun SectionCard(
    title: String,
    icon: String,
    iconBg: Color,
    iconFg: Color,
    summary: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    isNew: Boolean = false,
    content: @Composable () -> Unit,
) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val chevron = remember { DesignIcons.svg("chevron", DesignIcons.CHEVRON_DOWN) }
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(EXPAND_MILLIS), label = "chevron")
    val state = stringResource(if (expanded) R.string.state_section_open else R.string.state_section_closed)
    val newLabel = stringResource(R.string.cd_reports_new)
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, t.border, shape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(
                    onClickLabel = stringResource(if (expanded) R.string.cd_section_close else R.string.cd_section_open),
                    role = Role.Button,
                    onClick = onToggle,
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .clearAndSetSemantics {
                    contentDescription = listOfNotNull(title, if (isNew && !expanded) newLabel else null, if (expanded) null else summary).joinToString("، ")
                    stateDescription = state
                    heading()
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon, iconBg, iconFg, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1)
                    if (isNew && !expanded) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(t.amber))
                    }
                }
                AnimatedVisibility(visible = !expanded && summary != null) {
                    Text(
                        summary.orEmpty(),
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = t.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                chevron,
                contentDescription = null,
                tint = t.muted,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp)
                    .rotate(turn),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(EXPAND_MILLIS)) + fadeIn(tween(EXPAND_MILLIS)),
            exit = shrinkVertically(tween(EXPAND_MILLIS)) + fadeOut(tween(EXPAND_MILLIS)),
        ) {
            Column { content() }
        }
    }
}

/** «خرج مهر تا امروز: ۱۲٫۴ میلیون تومان» یا «میانگین خرج ماهانه: …»؛ null تا داده نیامده */
@Composable
private fun spendSummary(range: ReportRange, report: MonthReport?, months: SpendTrend?): String? {
    if (range == ReportRange.MONTH) {
        val curve = report?.curve ?: return null
        val name = monthName(curve.month)
        val label = stringResource(if (curve.isCurrent) R.string.reports_spent_so_far else R.string.reports_spent_month, name)
        return stringResource(R.string.reports_summary_amount, label, amount(Money.compact(curve.spentRial)))
    }
    val shown = months?.months?.dropWhile { it.spentRial == 0L } ?: return null
    val average = months.averageRial
    if (shown.size < 2 || average == null) return stringResource(R.string.reports_months_empty)
    return stringResource(R.string.reports_summary_amount, stringResource(R.string.reports_monthly_average), amount(Money.compact(average)))
}

/** «امروز ۴۵ میلیون تومان؛ ۲ میلیون بیشتر از ۱ مهر»؛ null وقتی جمعی برای گفتن نیست */
@Composable
private fun balanceSummary(overview: BalanceOverview?): String? {
    if (overview == null) return null
    if (LocalHideAmounts.current) return stringResource(R.string.balance_hidden_short)
    val stats = overview.total.stats() ?: return null
    val total = amount(Money.compact(stats.endRial))
    if (!overview.total.hasTrend) return stringResource(R.string.reports_summary_balance, total)
    val start = dateLabel(stats.startDay)
    val change = stats.endRial - stats.startRial
    val note = when {
        change > 0 -> stringResource(R.string.balance_up, Money.compact(change), start)
        change < 0 -> stringResource(R.string.balance_down, Money.compact(-change), start)
        else -> stringResource(R.string.balance_same, start)
    }
    return stringResource(R.string.reports_summary_balance_change, total, note)
}

@Composable
private fun Header(month: JalaliMonth) {
    val t = JibitoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.reports_title),
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            month.title,
            modifier = Modifier
                .clip(CircleShape)
                .background(t.chip)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun RangeSelector(range: ReportRange, onRange: (ReportRange) -> Unit) {
    SegmentedTabs(
        labels = ReportRange.entries.map {
            stringResource(
                when (it) {
                    ReportRange.MONTH -> R.string.reports_range_month
                    ReportRange.HALF_YEAR -> R.string.reports_range_half
                    ReportRange.YEAR -> R.string.reports_range_year
                }
            )
        },
        selected = range.ordinal,
        onSelect = { onRange(ReportRange.entries[it]) },
        outerPadding = PaddingValues(bottom = 12.dp),
    )
}
