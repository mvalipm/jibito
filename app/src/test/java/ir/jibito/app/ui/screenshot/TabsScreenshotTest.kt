package ir.jibito.app.ui.screenshot

import ir.jibito.app.data.repository.Insight
import ir.jibito.app.data.repository.MonthReport
import ir.jibito.app.data.repository.SpendCurve
import ir.jibito.app.ui.reports.ReportRange
import ir.jibito.app.ui.reports.ReportsContent
import ir.jibito.app.ui.reports.BalanceCard
import ir.jibito.app.ui.account.AccountContent
import ir.jibito.app.ui.account.AccountDetail
import ir.jibito.app.ui.account.AccountRange
import ir.jibito.app.ui.account.AccountTransactions
import ir.jibito.app.data.wallet.AccountRef
import ir.jibito.app.data.wallet.BalanceHistory
import ir.jibito.app.data.wallet.DayGrid
import ir.jibito.app.ui.common.LocalHideAmounts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.jibito.app.ui.summary.WhereSection
import ir.jibito.app.ui.theme.AppThemeStyle
import org.junit.Test
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ir.jibito.app.util.JalaliMonth
import java.util.Calendar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.ui.todo.TodoList

/** اسکرین‌شات‌های تب‌های اصلی، گزارش‌ها و حساب‌ها (پایه‌ی مشترک: ScreenshotTestBase) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class TabsScreenshotTest : ScreenshotTestBase() {

    /** صفحه‌ی اول کامل: سرصفحه با دکمه‌ی تنظیمات، کجا رفت و نوار پایین (کارهای لازم فقط در تب خودش) */
    @Test
    fun home() {
        for (dark in listOf(false, true)) shot("home", AppThemeStyle.DEFAULT, dark, padded = false) {
            val haze = remember { HazeState() }
            Box(Modifier.fillMaxWidth().height(860.dp)) {
                Column(Modifier.hazeSource(haze).verticalScroll(rememberScrollState())) {
                    Hero(18_400_000)
                    WhereSection(summary(18_400_000), onOpenCategory = {}, onShowAll = {})
                }
                TabBar(selected = 0, haze = haze)
            }
        }
    }

    /** تب «کارها»: پیامک‌های مبهم (بررسی) هم یکی از کارهاست */
    @Test
    fun todoTab() {
        for (dark in listOf(false, true)) shot("todotab", AppThemeStyle.DEFAULT, dark, padded = false) {
            val haze = remember { HazeState() }
            Box(Modifier.fillMaxWidth().height(860.dp)) {
                CompositionLocalProvider(LocalBottomBarSpace provides 110.dp) {
                    TodoList(stories(), Modifier.hazeSource(haze))
                }
                TabBar(selected = 3, haze = haze)
            }
        }
    }

    /** تب «گزارش‌ها»: خرج تجمعی مهر در برابر شهریور، بودجه، پیش‌بینی و نکته‌های «جیبی چی فهمید؟» */
    @Test
    fun reportsTab() {
        val mehr = JalaliMonth(1405, 7)
        val day = 24 * hour
        val shahrivarDaily = listOf(12, 6, 18, 9, 4, 25, 11, 8, 13, 20, 16, 12, 7, 5, 9, 14, 6, 3, 10, 8, 4, 6, 11, 5, 7, 3, 9, 4, 6, 5, 8)
        val mehrDaily = listOf(8, 4, 19, 3, 6, 22, 5, 10, 7)
        // ده‌دهم میلیون تومان ← ریال، ظهر هر روز
        val spends = shahrivarDaily.mapIndexed { i, v -> mehr.plus(-1).startMillis() + i * day + 12 * hour to v * 1_000_000L } +
            mehrDaily.mapIndexed { i, v -> mehr.startMillis() + i * day + 12 * hour to v * 1_000_000L }
        val curve = SpendCurve.compute(spends, mehr, now, budgetRial = 300_000_000L)
        val insights = listOf(
            Insight.CategoryChange(1, "رستوران و کافه", "🍽", null, 6_200_000L, 40),
            Insight.CategoryChange(2, "حمل‌ونقل", "🚕", null, -4_400_000L, -22),
            Insight.BusiestDay(mehr, 6, 22_000_000L, 3),
            Insight.WeekdayPeak(java.util.Calendar.FRIDAY, 2.03),
        )
        for (dark in listOf(false, true)) shot("reports", AppThemeStyle.DEFAULT, dark, padded = false) {
            val haze = remember { HazeState() }
            Box(Modifier.fillMaxWidth().height(860.dp)) {
                CompositionLocalProvider(LocalBottomBarSpace provides 110.dp) {
                    ReportsContent(
                        month = mehr,
                        range = ReportRange.MONTH,
                        report = MonthReport(curve, insights),
                        months = null,
                        onRange = {},
                        modifier = Modifier.hazeSource(haze),
                        balances = BalanceHistory.overview(balancePoints(), emptyList(), emptySet(), DayGrid.of(mehr.startMillis(), now)),
                    )
                }
                TabBar(selected = 2, haze = haze)
            }
        }
    }

    /** کارت «موجودی حساب‌ها» در تب گزارش‌ها: ۳۰ روز اخیر، و حالت «مبلغ‌ها پنهان» */
    @Test
    fun balanceCard() {
        val overview = BalanceHistory.overview(balancePoints(), emptyList(), emptySet(), DayGrid.lastDays(30, now))
        for (dark in listOf(false, true)) shot("balance", AppThemeStyle.DEFAULT, dark, padded = false) {
            BalanceCard(overview, onOpen = {}, nowMillis = now)
        }
        shot("balance_hidden", AppThemeStyle.DEFAULT, dark = false, padded = false) {
            CompositionLocalProvider(LocalHideAmounts provides true) {
                BalanceCard(overview, onOpen = {}, nowMillis = now)
            }
        }
    }

    /** صفحه‌ی جزئیات حساب ملت: ۳ ماه، کف/سقف/تغییر، جمله‌ی جیبی و تراکنش‌ها */
    @Test
    @Config(sdk = [34], qualifiers = "w400dp-h1400dp-xxhdpi")
    fun accountDetail() {
        val points = balancePoints()
        val grid = DayGrid.lastDays(AccountRange.QUARTER.days, now)
        val ref = AccountRef(11, "5678")
        val overview = BalanceHistory.overview(points, emptyList(), emptySet(), grid)
        val account = overview.accounts.first { it.ref == ref }
        val detail = AccountDetail(
            series = account.series,
            account = account,
            accounts = emptyList(),
            rhythm = BalanceHistory.depositRhythm(BalanceHistory.byAccount(points, emptyList()).getValue(ref), grid, now),
        )
        val transactions = AccountTransactions(sample.filter { it.bank?.id == 11 }, emptyList())
        for (dark in listOf(false, true)) shot("account", AppThemeStyle.DEFAULT, dark, padded = false) {
            // آن‌قدر بلند که چند روزِ فهرست تراکنش‌ها هم در تصویر بیاید
            Box(Modifier.fillMaxWidth().height(1400.dp)) {
                AccountContent(
                    ref = ref,
                    range = AccountRange.QUARTER,
                    onRange = {},
                    detail = detail,
                    transactions = transactions,
                    onBack = {},
                    onOpenTransaction = {},
                    onOpenAccount = {},
                    now = now,
                )
            }
        }
    }
}
