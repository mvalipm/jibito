package ir.jibito.app.ui.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.jibito.app.ui.common.JibiToast
import ir.jibito.app.ui.common.ToastMessage
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

/** اسکرین‌شات‌های فهرست تراکنش‌ها، فونت بزرگ، اسنک‌بار «برگردون» و کارت بررسی پیامک (پایه‌ی مشترک: ScreenshotTestBase) */
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

    /** اسنک‌بار بالای صفحه: «برگردون» با پیام بلند هم کامل دیده می‌شود؛ پیام خیلی بلند به خط دوم می‌رود */
    @Test
    fun toast() {
        for ((style, dark) in listOf(AppThemeStyle.DEFAULT to false, AppThemeStyle.DEFAULT to true)) {
            shot("toast", style, dark, padded = false) { Toasts() }
        }
        shot("toast", AppThemeStyle.DEFAULT, dark = false, padded = false, fontScale = 2f) { Toasts() }
    }

    /** دکمه‌های «سوپرمارکته؟ آره» و «یه چیز دیگه» با اسم دسته‌ی بلند: دکمه‌ی دوم به خط بعد می‌رود و نمی‌ماند زیر لبه */
    @Test
    fun suggestionLongName() {
        val long = tx(9, 3, 4_500_000, merchant = "فروشگاه افق کوروش", suggested = "رستوران و کافه‌های شهر")
        shot("suggestion_long", AppThemeStyle.DEFAULT, dark = false) { TxRow(long) }
        shot("suggestion_long", AppThemeStyle.DEFAULT, dark = false, fontScale = 2f) { TxRow(long) }
    }

    @Composable
    private fun Toasts() {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            // همان که در تصویر گزارش‌شده دکمه‌اش نصفه بود
            ToastSample("۲۶ تراکنش دیگر هم رفت تو تاکسی اینترنتی", "برگردون")
            Spacer(Modifier.height(12.dp))
            ToastSample("رفت تو کافه", "برگردون")
            Spacer(Modifier.height(12.dp))
            ToastSample("ملی ۱۰۰۷ از جمع موجودی کنار رفت و دیگر در جمع موجودی حساب نمی‌شود", "برگردون")
            Spacer(Modifier.height(12.dp))
            ToastSample("ثبت شد: انتقال به خودت", null)
        }
    }

    @Composable
    private fun ToastSample(text: String, action: String?) {
        JibiToast(ToastMessage(text, action, onAction = {}), onDismiss = {})
    }
}
