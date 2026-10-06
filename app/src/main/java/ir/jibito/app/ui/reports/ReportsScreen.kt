package ir.jibito.app.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.repository.MonthReport
import ir.jibito.app.data.repository.SpendTrend
import ir.jibito.app.data.wallet.AccountRef
import ir.jibito.app.data.wallet.BalanceOverview
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.JalaliMonth

/**
 * تب «گزارش‌ها»: «در طول زمان چه الگویی دارم؟»
 * ۱) نمودار خرج: این ماه (تجمعی، در برابر ماه قبل، با بودجه و پیش‌بینی آخر ماه) یا ۶/۱۲ ماه اخیر.
 * ۲) «موجودی حساب‌ها»: روند موجودی از روی مانده‌ی پیامک‌ها، در همان بازه (BalanceCard).
 * ۳) «جیبی چی فهمید؟»: نکته‌های کوتاه از الگوی خرج (دسته‌های بیشتر/کمتر، پرخرج‌ترین روز، الگوی هفته).
 * «خلاصه» به «الان وضعم چطوره؟» جواب می‌دهد؛ این تب به روند و مقایسه.
 */
@Composable
fun ReportsScreen(onOpenAccount: (AccountRef?) -> Unit = {}) {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val viewModel: ReportsViewModel = viewModel(
        factory = ReportsViewModel.factory(app.container.reportRepository, app.container.budgetRepository, app.container.balanceRepository)
    )
    val range by viewModel.range.collectAsState()
    val report by viewModel.report.collectAsState()
    val months by viewModel.months.collectAsState()
    val balances by viewModel.balances.collectAsState()
    ReportsContent(
        month = viewModel.month,
        range = range,
        report = report,
        months = months,
        onRange = viewModel::setRange,
        balances = balances,
        onOpenAccount = onOpenAccount,
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
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 16.dp + LocalBottomBarSpace.current),
    ) {
        item(key = "header") { Header(month) }
        item(key = "range") { RangeSelector(range, onRange) }
        item(key = "chart") {
            ChartCard {
                when {
                    range == ReportRange.MONTH && report != null -> MonthChart(report.curve)
                    range != ReportRange.MONTH && months != null -> MonthsChart(months)
                    else -> Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
        item(key = "balance") { BalanceCard(balances, onOpenAccount) }
        val insights = report?.insights.orEmpty()
        if (insights.isNotEmpty()) {
            item(key = "insights") { InsightsSection(insights, month) }
        }
    }
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
    )
}
