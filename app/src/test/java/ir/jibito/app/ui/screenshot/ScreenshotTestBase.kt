package ir.jibito.app.ui.screenshot

import ir.jibito.app.data.wallet.BalancePointRow
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
import ir.jibito.app.ui.smslist.TransactionRow
import ir.jibito.app.ui.summary.SummaryHero
import ir.jibito.app.ui.summary.TodoAction
import ir.jibito.app.ui.summary.TodoPage
import ir.jibito.app.ui.summary.TodoStory
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.data.category.CategoryPalette
import ir.jibito.app.data.repository.CategorySpend
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.data.repository.SubSpend
import ir.jibito.app.ui.theme.AppThemeStyle
import ir.jibito.app.ui.theme.JibitoTheme
import android.provider.Settings
import org.robolectric.RuntimeEnvironment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import ir.jibito.app.ui.settings.RowDivider
import ir.jibito.app.ui.settings.RowTrailing
import ir.jibito.app.ui.settings.SettingsGroup
import ir.jibito.app.ui.settings.SettingsIcons
import ir.jibito.app.ui.settings.SettingsRow
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.data.repository.MonthSpend
import ir.jibito.app.data.repository.SpendTrend
import ir.jibito.app.util.JalaliMonth
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziOptions
import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.Alignment
import dev.chrisbanes.haze.HazeState
import ir.jibito.app.ui.main.FloatingNavBar
import ir.jibito.app.ui.main.NavIcons
import ir.jibito.app.ui.main.NavItem
import ir.jibito.app.ui.todo.TodoList

/**
 * پایه‌ی مشترک تست‌های اسکرین‌شات: زمان و منطقه‌ی زمانی ثابت، انیمیشن خاموش، داده‌های نمونه و shot().
 * اسکرین‌شات بخش‌های اصلی ظاهر اپ، در هر سه پوسته و حالت روشن/تیره، و با فونت بزرگ.
 * تصویرهای مرجع در app/src/test/screenshots/ هستند:
 * - -Proborazzi.test.verify=true (پیش‌فرض CI): هر تفاوتی با مرجع، ساخت را می‌شکند (تصویر تفاوت در Artifact)
 * - -Proborazzi.test.record=true: مرجع‌ها از نو ساخته می‌شوند (CI: اجرای دستی با record_screenshots)
 * زمان و منطقه‌ی زمانی ثابت‌اند تا تصویرها هر بار یکی باشند.
 */
abstract class ScreenshotTestBase {

    protected companion object {
        const val ZONE = "Asia/Tehran"
    }

    /** پنجشنبه ۹ مهر ۱۴۰۵، ساعت ۲۰ تهران */
    protected val now = Calendar.getInstance(TimeZone.getTimeZone(ZONE)).apply { clear(); set(2026, Calendar.OCTOBER, 1, 20, 0) }.timeInMillis
    protected val hour = 60 * 60 * 1000L
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

    protected fun tx(
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
        oneOff: Boolean = false,
    ) = Transaction(
        id = id, bank = BankDirectory.byId(bankId), body = "", dateMillis = now - hoursAgo * hour,
        transaction = ParsedTransaction(type, amountRial, balanceRial), merchant = merchant, suggestedCategory = suggested,
        isFailedPurchase = failed, categoryId = if (categoryName != null) id else null, categoryName = categoryName,
        categoryIcon = categoryIcon, isAutoCategorized = auto, isSelfTransfer = selfTransfer, isOneOff = oneOff,
    )

    protected val sample = listOf(
        tx(1, 0, 1_850_000, merchant = "کافه لمیز", categoryName = "کافه", categoryIcon = "☕", balanceRial = 412_300_000),
        tx(2, 1, 4_200_000, merchant = "اسنپ", categoryName = "تاکسی اینترنتی", categoryIcon = "🚕", auto = true),
        tx(3, 2, 250_000_000, type = FlowType.DEPOSIT, bankId = 15, balanceRial = 662_300_000),
        tx(4, 26, 12_750_000, merchant = "فروشگاه افق کوروش", suggested = "سوپرمارکت"),
        tx(5, 27, 3_000_000, merchant = "دیجی‌کالا", failed = true),
        tx(6, 28, 50_000_000, merchant = "کارت ۶۰۳۷", selfTransfer = true),
        tx(7, 29, 900_000),
        tx(8, 30, 1_200_000_000, merchant = "لوازم خانگی سامان", oneOff = true),
    ).sortedByDescending { it.dateMillis }

    /** «کارهای لازم»: رنگ‌ها از پوسته‌ی فعلی، پس داخل خود تصویر ساخته می‌شوند */
    @Composable
    protected fun stories(): List<TodoStory> {
        val t = JibitoTheme.colors
        val cafe = categoryTint(CategoryPalette.LIGHT[1], "☕")
        return listOf(
            TodoStory("review", t.coral, t.uncatBg, t.uncatFg, DesignIcons.Message, null, "۲ پیامک مبهم", "کمکم کن، دفعه‌ی بعد خودم می‌فهمم.") {},
            TodoStory("uncat", t.coral, t.uncatBg, t.uncatFg, null, "۳", "خرج بی‌دسته", "دسته بده تا «کجا رفت؟» درست نشونت بده.") {},
            TodoStory("budget-2", t.alert, cafe.bg, cafe.fg, cafe.icon, cafe.glyph, "کافه ۱۱۲٪", "۳٫۴ میلیون از بودجه‌ی ۳ میلیونی") {},
            TodoStory(
                "transfer", t.teal, t.transferBg, t.transferFg, DesignIcons.Transfer, null, "انتقال به خودت؟",
                pages = listOf(
                    TodoPage("t1", "۵ میلیون تومان از ملت به سامان · دیروز. اگه بین کارت‌های خودت جابه‌جا کردی، خرج حساب نمی‌شه.", listOf(TodoAction("آره، مال خودمه") {}, TodoAction("نه") {})),
                    TodoPage("t2", "۲ میلیون تومان از ملت به پاسارگاد · ۳ مهر", listOf(TodoAction("آره، مال خودمه") {}, TodoAction("نه") {})),
                ),
            ) {},
            // کارت چندتایی: صفحه‌ی اول از سه صفحه
            TodoStory(
                "oneoff", t.teal, t.sugBg, t.sugFg, DesignIcons.Star, null, "خرج یک‌باره بود؟",
                pages = listOf(
                    TodoPage("1", "۱۲۰ میلیون تومان برای «لوازم خانگی سامان» · دیروز. خیلی بیشتر از معمول؛ اگه یک‌باره بود، میانگین و بودجه‌ت رو به هم نمی‌زنه.", listOf(TodoAction("آره، یک‌باره بود") {}, TodoAction("نه") {})),
                    TodoPage("2", "۸۰ میلیون تومان · ۷ تیر", listOf(TodoAction("آره، یک‌باره بود") {}, TodoAction("نه") {})),
                    TodoPage("3", "۶۰ میلیون تومان · ۲ اردیبهشت", listOf(TodoAction("آره، یک‌باره بود") {}, TodoAction("نه") {})),
                ),
            ) {},
            TodoStory("rec", t.teal, t.tealTint, t.tealTintFg, DesignIcons.Repeat, null, "شارژ ماهانه؟", "اگه ماهانه‌ست، قبل از موعدش یادت میندازم.") {},
            TodoStory("notif", t.amber, t.amberTint, t.amberTintFg, DesignIcons.Bell, null, "نوتیف خاموشه", "هشدار بودجه و «این خرج مال چی بود؟» بهت نمی‌رسه.", actions = listOf(TodoAction("روشن کن") {})) {},
        )
    }

    /** تب «کارها» بدون نوار پایین (فهرست تنبل است، پس ارتفاع ثابت می‌خواهد) */
    @Composable
    protected fun Todo() {
        Box(Modifier.fillMaxWidth().height(860.dp)) { TodoList(stories()) }
    }

    protected fun cat(id: Long, name: String, icon: String, palette: Int, spentToman: Long, budgetToman: Long? = null, vararg subs: Pair<String, Long>) =
        CategorySpend(
            categoryId = id, name = name, icon = icon, colorHex = CategoryPalette.LIGHT[palette],
            spentRial = spentToman * 10, budgetRial = budgetToman?.let { it * 10 },
            children = subs.map { SubSpend(it.first, it.second * 10) },
        )

    /** مهر ۱۴۰۵ با بودجه‌ی ۳۰ میلیونی؛ [spentToman] حال جیب را عوض می‌کند */
    protected fun summary(spentToman: Long) = MonthSummary(
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

    protected val trendSample = JalaliMonth(1405, 7).let { end ->
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
    protected val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))

    protected val variants = listOf(
        AppThemeStyle.DEFAULT to false, AppThemeStyle.DEFAULT to true,
        AppThemeStyle.WARM to false, AppThemeStyle.WARM to true,
        AppThemeStyle.COOL to false, AppThemeStyle.COOL to true,
        AppThemeStyle.MATCHA to false, AppThemeStyle.MATCHA to true,
        AppThemeStyle.LAVENDER to false, AppThemeStyle.LAVENDER to true,
        AppThemeStyle.CHERRY to false, AppThemeStyle.CHERRY to true,
        AppThemeStyle.DUSK to false, AppThemeStyle.DUSK to true,
    )

    protected fun shot(
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

    @Composable
    protected fun TxRow(t: Transaction) {
        TransactionRow(t, categoryTint(null, t.categoryIcon), onClick = {}, onAcceptSuggestion = if (t.suggestedCategory != null) ({}) else null)
    }

    /** سرصفحه‌ی «خلاصه»، ۹ روز از مهر گذشته */
    @Composable
    protected fun Hero(spentToman: Long) {
        SummaryHero(summary(spentToman), onPickMonth = {}, onEditBudget = {}, onToggleHidden = {}, nowMillis = now)
    }

    @Composable
    protected fun SettingsHubSample() {
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

    /** نوار پایینِ چهارتبی؛ عدد «کارها» همه‌ی کارهای لازم است */
    @Composable
    protected fun BoxScope.TabBar(selected: Int, haze: HazeState) {
        FloatingNavBar(
            items = listOf(
                NavItem(NavIcons.Summary, "خلاصه"),
                NavItem(NavIcons.Transactions, "تراکنش‌ها"),
                NavItem(NavIcons.Reports, "گزارش‌ها"),
                NavItem(NavIcons.Todo, "کارها", badge = 3),
            ),
            selectedIndex = selected,
            onSelect = {},
            hazeState = haze,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    /**
     * پیامک‌های مانده‌دار ۱۲۰ روز اخیر سه حساب: ملت (حقوق هر ۳۰ روز، اجاره، خرج روزانه)،
     * سامان (انتقال ماهانه و خرج کوچک) و بلو (پس‌انداز؛ ۳۵ روز است پیامکی نیامده).
     */
    protected fun balancePoints(): List<BalancePointRow> {
        val day = 24 * hour
        val start = now - 120 * day
        var id = 1L
        var mellat = 214_000_000L
        var saman = 31_000_000L
        var blu = 120_000_000L
        val out = mutableListOf<BalancePointRow>()
        fun add(d: Int, bank: Int, account: String?, remain: Long, deposit: Long = 0) {
            out += BalancePointRow(id++, bank, account, remain, start + d * day + 10 * hour, if (deposit > 0) 1 else 2, if (deposit > 0) deposit else 1_000_000)
        }
        for (d in 0 until 120) {
            when (d % 30) {
                8 -> { mellat += 480_000_000; add(d, 11, "5678", mellat, deposit = 480_000_000) }
                10 -> { mellat -= 170_000_000; add(d, 11, "5678", mellat) }
                20 -> { mellat -= 50_000_000; add(d, 11, "5678", mellat); saman += 50_000_000; add(d, 15, "1120", saman, deposit = 50_000_000) }
            }
            if (d % 4 != 0) { mellat -= ((d * 37) % 11 + 2) * 1_000_000L; add(d, 11, "5678", mellat) }
            if (d % 3 == 1) { saman -= ((d * 13) % 7 + 1) * 1_000_000L; add(d, 15, "1120", saman) }
            if (d % 30 == 9 && d < 85) { blu += 60_000_000; add(d, 40, null, blu, deposit = 60_000_000) }
        }
        return out
    }
}
