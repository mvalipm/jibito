package ir.jibito.app.ui.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.jibito.app.data.repository.MonthReport
import ir.jibito.app.data.repository.SpendCurve
import ir.jibito.app.data.wallet.BalanceForecast
import ir.jibito.app.data.wallet.BalanceHistory
import ir.jibito.app.data.wallet.DayGrid
import ir.jibito.app.data.wallet.ForecastBasis
import ir.jibito.app.data.wallet.Salary
import ir.jibito.app.data.wallet.SalaryDetector
import ir.jibito.app.data.wallet.UpcomingPayment
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.ui.reports.ChartCard
import ir.jibito.app.ui.reports.ForecastBreakdown
import ir.jibito.app.ui.reports.ReportRange
import ir.jibito.app.ui.reports.ReportSection
import ir.jibito.app.ui.reports.ReportsContent
import ir.jibito.app.ui.summary.SummaryHero
import ir.jibito.app.ui.theme.AppThemeStyle
import ir.jibito.app.util.JalaliMonth
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/** «پولم تا حقوق بعدی می‌رسه؟»: ادامه‌ی نمودار موجودی در «گزارش‌ها»، توضیح محاسبه و خط سرصفحه‌ی «خلاصه» */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ForecastScreenshotTest : ScreenshotTestBase() {

    private val mehr = JalaliMonth(1405, 7)

    /** از امروز (۹ مهر) تا ۳۰ مهر: روزی ۶۰۰ هزار تومان خرج روزمره و قسط ۲۰ مهر؛ حقوق ۱ آبان */
    private fun forecast(todayRial: Long): BalanceForecast {
        val loanDay = SalaryDetector.dayIn(mehr, 20)
        val last = SalaryDetector.dayIn(mehr, 30)
        val days = mutableListOf(SalaryDetector.startOfDay(now))
        val values = mutableListOf(todayRial)
        while (days.last() < last) {
            val next = Calendar.getInstance().apply { timeInMillis = days.last(); add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
            days += next
            values += values.last() - 6_000_000 - if (next == loanDay) 42_000_000 else 0
        }
        return BalanceForecast(
            days = days,
            values = values,
            routineRial = 6_000_000L * (days.size - 1),
            payments = listOf(UpcomingPayment("قسط وام", 42_000_000, loanDay)),
            salary = Salary(SalaryDetector.keyOf(11, null), 11, 480_000_000, dayOfMonth = 1, months = 5),
            paydayMillis = SalaryDetector.dayIn(mehr.plus(1), 1),
            basis = ForecastBasis.PATTERN,
        )
    }

    /** تب «گزارش‌ها» با کارت موجودی باز: نقطه‌چین تا ۱ آبان، جهش حقوق و جمله‌ی پیش‌بینی */
    @Test
    fun reportsForecast() {
        val overview = BalanceHistory.overview(balancePoints(), emptyList(), emptySet(), DayGrid.of(mehr.startMillis(), now))
        val today = overview.total.known.last()
        val curve = SpendCurve.compute(emptyList(), mehr, now, budgetRial = null)
        for (dark in listOf(false, true)) shot("reports_forecast", AppThemeStyle.DEFAULT, dark, padded = false) {
            Box(Modifier.fillMaxWidth().height(860.dp)) {
                CompositionLocalProvider(LocalBottomBarSpace provides 24.dp) {
                    ReportsContent(
                        month = mehr,
                        range = ReportRange.MONTH,
                        report = MonthReport(curve, emptyList()),
                        months = null,
                        onRange = {},
                        balances = overview,
                        open = ReportSection.BALANCE,
                        forecast = forecast(today),
                    )
                }
            }
        }
    }

    /** «چطور حساب شد؟» باز، در حالت کم آوردن */
    @Test
    fun forecastBreakdown() {
        shot("forecast_breakdown", AppThemeStyle.DEFAULT, dark = false, padded = false) {
            ChartCard { ForecastBreakdown(forecast(150_000_000), onNotSalary = {}, startOpen = true) }
        }
    }

    /** سرصفحه‌ی «خلاصه»: پولت تا حقوق می‌رسه / با همین ریتم ۲۸ مهر تموم می‌شه */
    @Test
    fun heroForecast() {
        val cases = listOf("hero_forecast_ok" to 900_000_000L, "hero_forecast_short" to 150_000_000L)
        for ((name, today) in cases) shot(name, AppThemeStyle.DEFAULT, dark = false, padded = false) {
            SummaryHero(
                summary(7_200_000),
                onPickMonth = {},
                onEditBudget = {},
                dark = false,
                onToggleDark = {},
                onToggleHidden = {},
                nowMillis = now,
                forecast = forecast(today),
            )
        }
    }
}
