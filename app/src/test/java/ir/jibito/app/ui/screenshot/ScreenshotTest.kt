package ir.jibito.app.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.domain.Transaction
import ir.jibito.app.ui.permission.SmsPermissionScreen
import ir.jibito.app.ui.smslist.DayHeader
import ir.jibito.app.ui.smslist.TransactionRow
import ir.jibito.app.ui.smslist.groupByDay
import ir.jibito.app.ui.summary.SummaryHero
import ir.jibito.app.ui.summary.TodoSection
import ir.jibito.app.ui.summary.TodoStory
import ir.jibito.app.ui.summary.WhereSection
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.data.category.CategoryPalette
import ir.jibito.app.data.repository.CategorySpend
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.data.repository.SubSpend
import ir.jibito.app.ui.theme.AppThemeStyle
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.welcome.WelcomeScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ir.jibito.app.data.repository.MonthSpend
import ir.jibito.app.data.repository.SpendTrend
import ir.jibito.app.ui.summary.TrendCard
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.ui.welcome.FirstRunReveal
import ir.jibito.app.ui.welcome.RevealStats
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziOptions
import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Before

/**
 * اسکرین‌شات بخش‌های اصلی ظاهر اپ، در هر سه پوسته و حالت روشن/تیره، و با فونت بزرگ.
 * تصویرهای مرجع در app/src/test/screenshots/ هستند:
 * - -Proborazzi.test.verify=true (پیش‌فرض CI): هر تفاوتی با مرجع، ساخت را می‌شکند (تصویر تفاوت در Artifact)
 * - -Proborazzi.test.record=true: مرجع‌ها از نو ساخته می‌شوند (CI: اجرای دستی با record_screenshots)
 * زمان و منطقه‌ی زمانی ثابت‌اند تا تصویرها هر بار یکی باشند.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {

    private companion object {
        const val ZONE = "Asia/Tehran"
    }

    /** پنجشنبه ۹ مهر ۱۴۰۵، ساعت ۲۰ تهران */
    private val now = Calendar.getInstance(TimeZone.getTimeZone(ZONE)).apply { clear(); set(2026, Calendar.OCTOBER, 1, 20, 0) }.timeInMillis
    private val hour = 60 * 60 * 1000L
    private lateinit var savedZone: TimeZone

    @Before
    fun fixZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE))
    }

    @After
    fun restoreZone() = TimeZone.setDefault(savedZone)

    private fun tx(
        id: Long,
        hoursAgo: Long,
        amountRial: Long,
        merchant: String? = null,
        type: FlowType = FlowType.WITHDRAWAL,
        bankId: Int = 11,
        categoryName: String? = null,
        categoryIcon: String? = null,
        auto: Boolean = false,
        suggested: String? = null,
        failed: Boolean = false,
        selfTransfer: Boolean = false,
        balanceRial: Long? = null,
    ) = Transaction(
        id = id, bank = BankDirectory.byId(bankId), body = "", dateMillis = now - hoursAgo * hour,
        transaction = ParsedTransaction(type, amountRial, balanceRial), merchant = merchant, suggestedCategory = suggested,
        isFailedPurchase = failed, categoryId = if (categoryName != null) id else null, categoryName = categoryName,
        categoryIcon = categoryIcon, isAutoCategorized = auto, isSelfTransfer = selfTransfer,
    )

    private val sample = listOf(
        tx(1, 0, 1_850_000, merchant = "کافه لمیز", categoryName = "کافه", categoryIcon = "☕", balanceRial = 412_300_000),
        tx(2, 1, 4_200_000, merchant = "اسنپ", categoryName = "تاکسی اینترنتی", categoryIcon = "🚕", auto = true),
        tx(3, 2, 250_000_000, type = FlowType.DEPOSIT, bankId = 15, balanceRial = 662_300_000),
        tx(4, 26, 12_750_000, merchant = "فروشگاه افق کوروش", suggested = "سوپرمارکت"),
        tx(5, 27, 3_000_000, merchant = "دیجی‌کالا", failed = true),
        tx(6, 28, 50_000_000, merchant = "کارت ۶۰۳۷", selfTransfer = true),
        tx(7, 29, 900_000),
    ).sortedByDescending { it.dateMillis }

    /** «کارهای لازم»: رنگ‌ها از پوسته‌ی فعلی، پس داخل خود تصویر ساخته می‌شوند */
    @Composable
    private fun stories(): List<TodoStory> {
        val t = JibitoTheme.colors
        val cafe = categoryTint(CategoryPalette.LIGHT[1], "🍽")
        return listOf(
            TodoStory("uncat", t.coral, t.uncatBg, t.uncatFg, null, "۳", "خرج بی‌دسته") {},
            TodoStory("budget", t.alert, cafe.bg, cafe.fg, cafe.icon, null, "رستوران ۱۱۲٪") {},
            TodoStory("rec", t.teal, t.tealTint, t.tealTintFg, DesignIcons.Repeat, null, "شارژ ساختمون ماهانه؟") {},
            TodoStory("notif", t.amber, t.amberTint, t.amberTintFg, DesignIcons.Bell, null, "نوتیف خاموشه") {},
        )
    }

    private fun cat(id: Long, name: String, icon: String, palette: Int, spentToman: Long, budgetToman: Long? = null, vararg subs: Pair<String, Long>) =
        CategorySpend(
            categoryId = id, name = name, icon = icon, colorHex = CategoryPalette.LIGHT[palette],
            spentRial = spentToman * 10, budgetRial = budgetToman?.let { it * 10 },
            children = subs.map { SubSpend(it.first, it.second * 10) },
        )

    /** مهر ۱۴۰۵ با بودجه‌ی ۳۰ میلیونی؛ [spentToman] حال جیب را عوض می‌کند */
    private fun summary(spentToman: Long) = MonthSummary(
        month = JalaliMonth(1405, 7),
        totalSpentRial = spentToman * 10,
        totalIncomeRial = 250_000_000,
        uncategorizedRial = 8_500_000,
        categories = listOf(
            cat(1, "خوراک", "🛒", 0, 5_200_000, null, "سوپرمارکت" to 4_000_000L, "نان" to 600_000L),
            cat(2, "رستوران و کافه", "🍽", 1, 3_400_000, 3_000_000),
            cat(3, "حمل‌ونقل", "🚗", 2, 2_900_000, null, "تاکسی اینترنتی" to 2_500_000L),
            cat(4, "خانه و خانواده", "🏠", 3, 2_600_000),
            cat(5, "پوشاک", "👕", 4, 2_100_000),
            cat(6, "سلامت", "💊", 5, 1_200_000),
        ),
        incomeCategories = emptyList(),
        uncategorizedIncomeRial = 0,
        overallBudgetRial = 300_000_000,
    )

    private val trendSample = JalaliMonth(1405, 7).let { end ->
        val values = listOf(38_000_000L, 52_500_000L, 41_200_000L, 66_000_000L, 47_800_000L, 29_300_000L)
        SpendTrend(values.mapIndexed { i, v -> MonthSpend(end.plus(i - 5), v * 10) }, lastMonthSameTimeRial = null)
    }

    /** ۱٪ تفاوت پیکسل‌ها (لبه‌های نرم فونت) قبول است؛ بیشتر از آن یعنی ظاهر عوض شده */
    private val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))

    private val variants = listOf(
        AppThemeStyle.DEFAULT to false, AppThemeStyle.DEFAULT to true,
        AppThemeStyle.WARM to false, AppThemeStyle.WARM to true,
        AppThemeStyle.COOL to false, AppThemeStyle.COOL to true,
    )

    private fun shot(
        name: String,
        style: AppThemeStyle,
        dark: Boolean,
        padded: Boolean = true,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        val suffix = if (fontScale != 1f) "_font${(fontScale * 100).toInt()}" else ""
        val file = "src/test/screenshots/${name}_${style.name.lowercase()}_${if (dark) "dark" else "light"}$suffix.png"
        captureRoboImage(file, roborazziOptions = options) {
            JibitoTheme(style = style, darkTheme = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalDensity provides Density(density.density, fontScale),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(if (padded) 16.dp else 0.dp)
                    ) { content() }
                }
            }
        }
    }

    @Test
    fun transactionsByDay() {
        val groups = groupByDay(sample)
        for ((style, dark) in variants) {
            shot("transactions", style, dark) {
                groups.forEach { group ->
                    DayHeader(group, now)
                    group.items.forEach { t -> TxRow(t) }
                }
            }
        }
    }

    /** بزرگ‌ترین اندازه‌ی فونت گوشی: متن‌ها نباید بریده شوند یا روی هم بیفتند */
    @Test
    fun largeFont() {
        val groups = groupByDay(sample)
        shot("transactions", AppThemeStyle.DEFAULT, dark = false, fontScale = 2f) {
            groups.take(1).forEach { group ->
                DayHeader(group, now)
                group.items.forEach { t -> TxRow(t) }
            }
        }
        shot("todo", AppThemeStyle.DEFAULT, dark = false, padded = false, fontScale = 2f) { TodoSection(stories()) }
        shot("hero", AppThemeStyle.DEFAULT, dark = false, padded = false, fontScale = 2f) { Hero(7_200_000) }
        shot("trend", AppThemeStyle.DEFAULT, dark = false, padded = false, fontScale = 2f) { TrendCard(trendSample) }
    }

    @Composable
    private fun TxRow(t: Transaction) {
        TransactionRow(t, categoryTint(null, t.categoryIcon), onClick = {}, onAcceptSuggestion = if (t.suggestedCategory != null) ({}) else null)
    }

    /** سرصفحه‌ی «خلاصه»، ۹ روز از مهر گذشته */
    @Composable
    private fun Hero(spentToman: Long) {
        SummaryHero(summary(spentToman), onPickMonth = {}, onEditBudget = {}, dark = false, onToggleDark = {}, onToggleHidden = {}, nowMillis = now)
    }

    @Test
    fun todo() {
        for ((style, dark) in variants) shot("todo", style, dark, padded = false) { TodoSection(stories()) }
    }

    /** سه حال جیب: آروم، یواش‌تر، بیرون زد */
    @Test
    fun hero() {
        for ((style, dark) in variants) shot("hero", style, dark, padded = false) { Hero(7_200_000) }
        shot("hero_warn", AppThemeStyle.DEFAULT, dark = false, padded = false) { Hero(26_400_000) }
        shot("hero_over", AppThemeStyle.DEFAULT, dark = false, padded = false) { Hero(33_600_000) }
    }

    @Test
    fun where() {
        for ((style, dark) in variants) shot("where", style, dark, padded = false) {
            WhereSection(summary(17_400_000), onOpenCategory = {}, onShowAll = {})
        }
    }

    @Test
    fun trend() {
        for ((style, dark) in variants) shot("trend", style, dark, padded = false) { TrendCard(trendSample) }
    }

    @Test
    fun onboarding() {
        for (dark in listOf(false, true)) {
            shot("welcome", AppThemeStyle.DEFAULT, dark, padded = false) { WelcomeScreen(onStart = {}) }
            shot("permission", AppThemeStyle.DEFAULT, dark, padded = false) { SmsPermissionScreen(wasDenied = false, onAllowClick = {}) }
        }
        for ((style, dark) in variants) {
            shot("reveal", style, dark, padded = false) { FirstRunReveal(RevealStats(count = 342, months = 6, banks = 3, topCategory = "سوپرمارکت"), onDone = {}) }
        }
    }
}
