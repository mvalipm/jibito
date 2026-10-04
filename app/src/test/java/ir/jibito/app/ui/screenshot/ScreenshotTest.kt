package ir.jibito.app.ui.screenshot

import ir.jibito.app.ui.common.LocalLoopingMotion
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
import ir.jibito.app.ui.summary.TodoAction
import ir.jibito.app.ui.review.ReviewCard
import ir.jibito.app.data.repository.ReviewItem
import ir.jibito.app.data.review.ReviewDetector
import ir.jibito.app.data.parser.SmsTextNormalizer
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
import android.provider.Settings
import org.robolectric.RuntimeEnvironment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.FrameLayout
import ir.jibito.app.widget.SpendWidget
import ir.jibito.app.ui.settings.PermissionBanner
import ir.jibito.app.ui.settings.RowDivider
import ir.jibito.app.ui.settings.RowTrailing
import ir.jibito.app.ui.settings.SettingsGroup
import ir.jibito.app.ui.settings.SettingsIcons
import ir.jibito.app.ui.settings.SettingsRow
import ir.jibito.app.ui.settings.SettingsSection
import ir.jibito.app.ui.settings.ThemePicker
import ir.jibito.app.ui.theme.JibitoIcons
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import ir.jibito.app.ui.main.FloatingNavBar
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.ui.main.NavIcons
import ir.jibito.app.ui.main.NavItem
import ir.jibito.app.ui.todo.TodoList

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

    /** «حذف انیمیشن‌ها»ی گوشی: دموی خوش‌آمد و انیمیشن‌های بی‌پایان ثابت می‌مانند تا تصویرها هر بار یکی باشند */
    @Before
    fun disableAnimations() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
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
        val cafe = categoryTint(CategoryPalette.LIGHT[1], "☕")
        return listOf(
            TodoStory("review", t.coral, t.uncatBg, t.uncatFg, DesignIcons.Message, null, "۲ پیامک مبهم", "کمکم کن، دفعه‌ی بعد خودم می‌فهمم.") {},
            TodoStory("uncat", t.coral, t.uncatBg, t.uncatFg, null, "۳", "خرج بی‌دسته", "دسته بده تا «کجا رفت؟» درست نشونت بده.") {},
            TodoStory("budget-2", t.alert, cafe.bg, cafe.fg, cafe.icon, cafe.glyph, "کافه ۱۱۲٪", "۳٫۴ میلیون از بودجه‌ی ۳ میلیونی") {},
            TodoStory("transfer", t.teal, t.transferBg, t.transferFg, DesignIcons.Transfer, null, "انتقال به خودت؟", "اگه بین کارت‌های خودت جابه‌جا کردی، خرج حساب نمی‌شه.", actions = listOf(TodoAction("آره، مال خودمه") {}, TodoAction("نه") {})) {},
            TodoStory("rec", t.teal, t.tealTint, t.tealTintFg, DesignIcons.Repeat, null, "شارژ ماهانه؟", "اگه ماهانه‌ست، قبل از موعدش یادت میندازم.") {},
            TodoStory("notif", t.amber, t.amberTint, t.amberTintFg, DesignIcons.Bell, null, "نوتیف خاموشه", "هشدار بودجه و «این خرج مال چی بود؟» بهت نمی‌رسه.", actions = listOf(TodoAction("روشن کن") {})) {},
        )
    }

    /** تب «کارها» بدون نوار پایین (فهرست تنبل است، پس ارتفاع ثابت می‌خواهد) */
    @Composable
    private fun Todo() {
        Box(Modifier.fillMaxWidth().height(860.dp)) { TodoList(stories()) }
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
        SpendTrend(
            values.mapIndexed { i, v -> MonthSpend(end.plus(i - 5), v * 10) },
            // ماه جاری «تا امروز»: مقایسه با همین موقعِ شهریور، و پیش‌بینی آخر ماه
            lastMonthSameTimeRial = 33_300_000L * 10,
            isCurrent = true,
            projectedRial = 40_000_000L * 10,
        )
    }

    /** ۱٪ تفاوت پیکسل‌ها (لبه‌های نرم فونت) قبول است؛ بیشتر از آن یعنی ظاهر عوض شده */
    private val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))

    private val variants = listOf(
        AppThemeStyle.DEFAULT to false, AppThemeStyle.DEFAULT to true,
        AppThemeStyle.WARM to false, AppThemeStyle.WARM to true,
        AppThemeStyle.COOL to false, AppThemeStyle.COOL to true,
        AppThemeStyle.MATCHA to false, AppThemeStyle.MATCHA to true,
        AppThemeStyle.LAVENDER to false, AppThemeStyle.LAVENDER to true,
        AppThemeStyle.CHERRY to false, AppThemeStyle.CHERRY to true,
        AppThemeStyle.DUSK to false, AppThemeStyle.DUSK to true,
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
                    // انیمیشن‌های بی‌پایان خاموش، تا صفحه آرام شود و عکس گرفته شود
                    LocalLoopingMotion provides false,
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
        shot("todo", AppThemeStyle.DEFAULT, dark = false, padded = false, fontScale = 2f) { Todo() }
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

    /** کارت بررسی پیامک مبهم: عددهای داخل متن قابل لمس، کارت خلاصه و دکمه‌های پایین */
    @Test
    fun review() {
        val body = "مبلغ ۲٬۵۰۰٬۰۰۰ ریال از حساب ۱۲۳۴ کسر شد.\nموجودی: ۴۱٬۲۳۰٬۰۰۰"
        val item = ReviewItem(
            smsId = 1,
            sender = "+989120000000",
            body = body,
            dateMillis = now,
            bankName = null,
            guess = ReviewDetector.guess(SmsTextNormalizer.normalize(body)),
        )
        for ((style, dark) in listOf(AppThemeStyle.DEFAULT to false, AppThemeStyle.DEFAULT to true)) {
            shot("review", style, dark) {
                Box(Modifier.fillMaxWidth().height(720.dp)) {
                    ReviewCard(item, sameSenderOthers = 0, onConfirm = { _, _, _, _ -> }, onDismiss = {}, onAddInstitution = { null }, onShare = {})
                }
            }
        }
    }

    @Test
    fun todo() {
        for ((style, dark) in variants) shot("todo", style, dark, padded = false) { Todo() }
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
            shot("reveal", style, dark, padded = false) { FirstRunReveal(RevealStats(count = 342, months = 6, banks = 3, topCategory = "سوپرمارکت", topSharePercent = 28), onDone = {}) }
        }
    }

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

    @Test
    fun settingsParts() {
        for ((style, dark) in variants) {
            shot("settings", style, dark) {
                PermissionBanner(smsOk = true, notifyOk = false, onFix = {})
                Spacer(Modifier.height(24.dp))
                SettingsSection("پوسته", JibitoIcons.Palette) { ThemePicker(style, onSelect = {}) }
            }
        }
        shot("settings", AppThemeStyle.DEFAULT, false, fontScale = 2f) {
            SettingsSection("پوسته", JibitoIcons.Palette) { ThemePicker(AppThemeStyle.DEFAULT, onSelect = {}) }
        }
    }

    /** صفحه‌ی اصلی تنظیمات: گروه با ردیف‌های زیرصفحه، کلید و وضعیت دسترسی */
    @Test
    fun settingsHub() {
        for ((style, dark) in variants) {
            shot("settings_hub", style, dark) { SettingsHubSample() }
        }
        shot("settings_hub", AppThemeStyle.DEFAULT, false, fontScale = 2f) { SettingsHubSample() }
    }

    @Composable
    private fun SettingsHubSample() {
        val tones = JibitoTheme.colors
        SettingsGroup("داده‌هات") {
            SettingsRow(SettingsIcons.Backup, tones.teal, "پشتیبان‌گیری", "۴۵ روزه پشتیبان نگرفتی؛ وقتشه!", attention = true, trailing = RowTrailing.Action("الان بگیر") {}, onClick = {})
            RowDivider()
            SettingsRow(
                JibitoIcons.Lock, tones.transfer, "قفل اپ", "با اثر انگشت یا قفل گوشی",
                trailing = RowTrailing.Toggle(checked = true, onChange = {}),
            )
        }
        Spacer(Modifier.height(22.dp))
        SettingsGroup("نوتیف و دسترسی‌ها") {
            SettingsRow(
                JibitoIcons.Message, MaterialTheme.colorScheme.primary, "خوندن پیامک", "خرج‌ها خودکار ثبت می‌شن",
                trailing = RowTrailing.Status(true),
            )
            RowDivider()
            SettingsRow(
                JibitoIcons.Bell, tones.amber, "نوتیف", "خاموشه", attention = true,
                trailing = RowTrailing.Action("روشن کن") {}, onClick = {},
            )
            RowDivider()
            SettingsRow(JibitoIcons.Palette, MaterialTheme.colorScheme.primary, "پوسته", "مرجانی · مثل گوشی", onClick = {})
        }
    }

    /** نوار پایینِ سه‌تبی؛ عدد «کارها» همه‌ی کارهای لازم است */
    @Composable
    private fun BoxScope.TabBar(selected: Int, haze: HazeState) {
        FloatingNavBar(
            items = listOf(
                NavItem(NavIcons.Summary, "خلاصه"),
                NavItem(NavIcons.Transactions, "تراکنش‌ها"),
                NavItem(NavIcons.Todo, "کارها", badge = 3),
            ),
            selectedIndex = selected,
            onSelect = {},
            hazeState = haze,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

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
                TabBar(selected = 2, haze = haze)
            }
        }
    }
}

