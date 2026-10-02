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
        AttentionItem(JibitoIcons.Bell, "نوتیفیکیشن‌ها خاموش‌اند", AttentionItem.Tone.WARN) {},
        AttentionItem(JibitoIcons.Warning, "کافه: ۱۱۲٪ بودجه", AttentionItem.Tone.DANGER) {},
        AttentionItem(JibitoIcons.Tag, "۸۵۰ هزار تومان خرج بی‌دسته", AttentionItem.Tone.NORMAL) {},
        AttentionItem(JibitoIcons.Repeat, "«شارژ ساختمون» هر ماه پرداخت می‌شه؟ یادآوری بسازم", AttentionItem.Tone.NORMAL) {},
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
