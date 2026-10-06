package ir.jibito.app.ui.screenshot

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.jibito.app.ui.smslist.DayHeader
import ir.jibito.app.ui.smslist.groupByDay
import ir.jibito.app.ui.review.ReviewCard
import ir.jibito.app.data.repository.ReviewItem
import ir.jibito.app.data.review.ReviewDetector
import ir.jibito.app.data.parser.SmsTextNormalizer
import ir.jibito.app.ui.theme.AppThemeStyle
import org.junit.Test
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ir.jibito.app.ui.summary.TrendCard

/** اسکرین‌شات‌های فهرست تراکنش‌ها، فونت بزرگ و کارت بررسی پیامک (پایه‌ی مشترک: ScreenshotTestBase) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class TransactionScreenshotTest : ScreenshotTestBase() {

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
}
