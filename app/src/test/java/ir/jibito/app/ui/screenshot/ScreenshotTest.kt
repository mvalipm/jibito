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
import ir.jibito.app.data.category.CategoryPalette
import ir.jibito.app.ui.permission.SmsPermissionScreen
import ir.jibito.app.ui.smslist.DayHeader
import ir.jibito.app.ui.smslist.TransactionRow
import ir.jibito.app.ui.smslist.groupByDay
import ir.jibito.app.ui.summary.AttentionCard
import ir.jibito.app.ui.summary.AttentionItem
import ir.jibito.app.ui.theme.AppThemeStyle
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.welcome.WelcomeScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import android.provider.Settings
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ir.jibito.app.data.repository.MonthSpend
import ir.jibito.app.data.repository.SpendTrend
import ir.jibito.app.ui.summary.TrendCard
import ir.jibito.app.ui.summary.GlanceHero
import ir.jibito.app.ui.summary.WhereCard
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.data.repository.CategorySpend
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.FrameLayout
import ir.jibito.app.widget.SpendWidget
import ir.jibito.app.ui.common.MascotEmptyState
import ir.jibito.app.ui.common.MascotFace
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

    /** «حذف انیمیشن‌ها»: انیمیشن‌های بی‌پایان (تپش ردیف بی‌دسته) ثابت می‌مانند تا تصویر قطعی باشد */
    @Before
    fun disableAnimations() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

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
        colorHex: String? = null,
    ) = Transaction(
        id = id, bank = BankDirectory.byId(bankId), body = "", dateMillis = now - hoursAgo * hour,
        transaction = ParsedTransaction(type, amountRial, balanceRial), merchant = merchant, suggestedCategory = suggested,
        isFailedPurchase = failed, categoryId = if (categoryName != null) id else null, categoryName = categoryName,
        categoryIcon = categoryIcon, categoryColorHex = colorHex, isAutoCategorized = auto, isSelfTransfer = selfTransfer,
    )

    private val sample = listOf(
        tx(1, 0, 1_850_000, merchant = "کافه لمیز", categoryName = "کافه", categoryIcon = "☕", balanceRial = 412_300_000, colorHex = CategoryPalette.LIGHT[1]),
        tx(2, 1, 4_200_000, merchant = "اسنپ", categoryName = "تاکسی اینترنتی", categoryIcon = "🚕", auto = true, colorHex = CategoryPalette.LIGHT[2]),
        tx(3, 2, 250_000_000, type = FlowType.DEPOSIT, bankId = 15, balanceRial = 662_300_000),
        tx(4, 26, 12_750_000, merchant = "فروشگاه افق کوروش", suggested = "سوپرمارکت"),
        tx(5, 27, 3_000_000, merchant = "دیجی‌کالا", failed = true),
        tx(6, 28, 50_000_000, merchant = "کارت ۶۰۳۷", selfTransfer = true),
        tx(7, 29, 900_000),
    ).sortedByDescending { it.dateMillis }

    private val attentionItems = listOf(
        AttentionItem(JibitoIcons.Bell, "نوتیف خاموشه", "نوتیفیکیشن‌ها خاموش‌اند", AttentionItem.Tone.WARN) {},
        AttentionItem(JibitoIcons.Warning, "کافه ۱۱۲٪", "کافه: ۱۱۲٪ بودجه", AttentionItem.Tone.DANGER) {},
        AttentionItem(JibitoIcons.Tag, "۸۵۰ هزار بی‌دسته", "۸۵۰ هزار تومان خرج بی‌دسته", AttentionItem.Tone.NORMAL) {},
        AttentionItem(JibitoIcons.Repeat, "شارژ ساختمون ماهانه؟", "«شارژ ساختمون» هر ماه پرداخت می‌شه؟ یادآوری بسازم", AttentionItem.Tone.NORMAL) {},
    )

    /** یک ماه گذشته (نوار «امروز» و سرعت ندارد، پس تصویر به ساعت اجرا بستگی ندارد) */
    private fun monthSample(budgetRial: Long?) = MonthSummary(
        month = JalaliMonth(1405, 6),
        totalSpentRial = 184_000_000,
        totalIncomeRial = 250_000_000,
        uncategorizedRial = 8_500_000,
        categories = listOf(
            CategorySpend(1, "سوپرمارکت", "🛒", CategoryPalette.LIGHT[0], 52_000_000, 60_000_000),
            CategorySpend(2, "رستوران و کافه", "🍽", CategoryPalette.LIGHT[1], 34_000_000, 30_000_000),
            CategorySpend(3, "رفت‌وآمد", "🚕", CategoryPalette.LIGHT[2], 29_000_000, null),
            CategorySpend(4, "خونه و قبض", "🏠", CategoryPalette.LIGHT[3], 26_000_000, null),
            CategorySpend(5, "پوشاک", "👕", CategoryPalette.LIGHT[4], 21_000_000, null),
            CategorySpend(6, "سلامت", "💊", CategoryPalette.LIGHT[5], 9_000_000, null),
        ),
        incomeCategories = emptyList(),
        uncategorizedIncomeRial = 0,
        overallBudgetRial = budgetRial,
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
                    group.items.forEach { t -> TransactionRow(t, onClick = {}, onAcceptSuggestion = {}) }
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
                group.items.forEach { t -> TransactionRow(t, onClick = {}, onAcceptSuggestion = {}) }
            }
        }
        shot("attention", AppThemeStyle.DEFAULT, dark = false, fontScale = 2f) { AttentionCard(attentionItems) }
        shot("trend", AppThemeStyle.DEFAULT, dark = false, fontScale = 2f) { TrendCard(trendSample) }
    }

    @Test
    fun attentionCard() {
        for ((style, dark) in variants) shot("attention", style, dark) { AttentionCard(attentionItems) }
    }

    /** صفحه‌ی خلاصه: جیب (آرام / رد شده / بدون بودجه) و «کجا رفت؟» */
    @Test
    fun summaryGlance() {
        for ((style, dark) in variants) {
            shot("summary", style, dark, padded = false) {
                GlanceHero(monthSample(300_000_000), onEditBudget = {}, vsLastMonthPercent = -22)
                Box(Modifier.padding(16.dp)) { WhereCard(monthSample(300_000_000), onOpenCategory = {}, onShowAll = {}) }
            }
        }
        for (dark in listOf(false, true)) {
            shot("summary_over", AppThemeStyle.DEFAULT, dark, padded = false) {
                GlanceHero(monthSample(150_000_000), onEditBudget = {})
            }
            shot("summary_nobudget", AppThemeStyle.DEFAULT, dark, padded = false) {
                GlanceHero(monthSample(null), onEditBudget = {})
            }
        }
        shot("summary", AppThemeStyle.DEFAULT, false, padded = false, fontScale = 2f) {
            GlanceHero(monthSample(300_000_000), onEditBudget = {}, vsLastMonthPercent = -22)
        }
    }

    /** ویجت صفحه‌ی اصلی: خود RemoteViews (همان که روی گوشی نشان داده می‌شود) در هر حال جیب، روشن و تیره */
    @Test
    fun widget() {
        val cases = listOf(
            "calm" to SpendWidget.Numbers(6_050_000, 60_000_000, 300_000_000),
            "warn" to SpendWidget.Numbers(6_050_000, 255_000_000, 300_000_000),
            "over" to SpendWidget.Numbers(6_050_000, 330_000_000, 300_000_000),
            "nobudget" to SpendWidget.Numbers(6_050_000, 184_000_000, null),
            "locked" to null,
        )
        for (dark in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
            for ((name, n) in cases) {
                val context = RuntimeEnvironment.getApplication()
                val views = SpendWidget.views(context, n, now)
                captureRoboImage(
                    "src/test/screenshots/widget_${name}_${if (dark) "dark" else "light"}.png",
                    roborazziOptions = options,
                ) {
                    Box(Modifier.padding(12.dp)) {
                        AndroidView(
                            factory = { ctx -> views.apply(ctx, FrameLayout(ctx)) },
                            modifier = Modifier.size(width = 260.dp, height = 115.dp),
                        )
                    }
                }
            }
        }
        RuntimeEnvironment.setQualifiers("+notnight")
    }

    /** حالت‌های خالی با جیبی (صندوق بررسی خالی، همه دسته دارن، فهرست خالی) */
    @Test
    fun emptyStates() {
        for (dark in listOf(false, true)) {
            shot("empty", AppThemeStyle.DEFAULT, dark) {
                MascotEmptyState(MascotFace.HAPPY, "همه‌چی مرتبه!", "صندوق بررسی خالیه. هر وقت پیامک عجیبی بیاد، اینجا نشونت می‌دم.")
                Box(Modifier.padding(top = 24.dp)) {
                    MascotEmptyState(MascotFace.CURIOUS, "هنوز تراکنشی نیست", "پیامک بانک که بیاد، خودم ثبتش می‌کنم.", mascotSize = 96.dp)
                }
            }
        }
    }

    @Test
    fun trend() {
        for ((style, dark) in variants) shot("trend", style, dark) { TrendCard(trendSample) }
    }

    @Test
    fun onboarding() {
        for (dark in listOf(false, true)) {
            shot("welcome", AppThemeStyle.DEFAULT, dark, padded = false) { WelcomeScreen(onStart = {}) }
            shot("permission", AppThemeStyle.DEFAULT, dark, padded = false) { SmsPermissionScreen(wasDenied = false, onAllowClick = {}) }
        }
        for ((style, dark) in variants) {
            shot("reveal", style, dark, padded = false) { FirstRunReveal(RevealStats(count = 342, months = 6, banks = 3), onDone = {}) }
        }
    }
}
