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
import ir.jibito.app.ui.smslist.groupPosition
import ir.jibito.app.ui.summary.AttentionCard
import ir.jibito.app.ui.summary.AttentionItem
import ir.jibito.app.ui.theme.AppThemeStyle
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.welcome.WelcomeScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * اسکرین‌شات بخش‌های اصلی ظاهر اپ، در هر سه پوسته و حالت روشن/تیره.
 * فقط وقتی ساخته می‌شوند که -Proborazzi.test.record=true داده شود (در CI)؛ خروجی: app/build/outputs/roborazzi/
 * هدف: دیدن ظاهر بعد از هر تغییر، بدون گوشی.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {

    private val now = System.currentTimeMillis()
    private val hour = 60 * 60 * 1000L

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

    private val variants = listOf(
        AppThemeStyle.DEFAULT to false, AppThemeStyle.DEFAULT to true,
        AppThemeStyle.WARM to false, AppThemeStyle.WARM to true,
        AppThemeStyle.COOL to false, AppThemeStyle.COOL to true,
    )

    private fun shot(name: String, style: AppThemeStyle, dark: Boolean, padded: Boolean = true, content: @Composable () -> Unit) {
        val file = "build/outputs/roborazzi/${name}_${style.name.lowercase()}_${if (dark) "dark" else "light"}.png"
        captureRoboImage(file) {
            JibitoTheme(style = style, darkTheme = dark) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
                    DayHeader(group)
                    group.items.forEachIndexed { i, t -> TransactionRow(t, groupPosition(i, group.items.size), onClick = {}) }
                }
            }
        }
    }

    @Test
    fun attentionCard() {
        val items = listOf(
            AttentionItem(JibitoIcons.Bell, "نوتیفیکیشن‌ها خاموش‌اند", AttentionItem.Tone.WARN) {},
            AttentionItem(JibitoIcons.Warning, "کافه: ۱۱۲٪ بودجه", AttentionItem.Tone.DANGER) {},
            AttentionItem(JibitoIcons.Tag, "۸۵۰ هزار تومان خرج بی‌دسته", AttentionItem.Tone.NORMAL) {},
            AttentionItem(JibitoIcons.Message, "۲ پیامک منتظر بررسی", AttentionItem.Tone.NORMAL) {},
        )
        for ((style, dark) in variants) shot("attention", style, dark) { AttentionCard(items) }
    }

    @Test
    fun onboarding() {
        for (dark in listOf(false, true)) {
            shot("welcome", AppThemeStyle.DEFAULT, dark, padded = false) { WelcomeScreen(onStart = {}) }
            shot("permission", AppThemeStyle.DEFAULT, dark, padded = false) { SmsPermissionScreen(wasDenied = false, onAllowClick = {}) }
        }
    }
}
