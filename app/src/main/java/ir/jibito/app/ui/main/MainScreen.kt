package ir.jibito.app.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.ui.review.ReviewScreen
import ir.jibito.app.ui.settings.SettingsScreen
import ir.jibito.app.ui.smslist.SmsListScreen
import ir.jibito.app.ui.summary.SummaryScreen
import ir.jibito.app.util.Jalali

private enum class Tab(val label: Int) {
    Summary(R.string.tab_summary),
    Transactions(R.string.tab_transactions),
    Review(R.string.tab_review),
    Settings(R.string.tab_settings),
}

/**
 * فضای خالی‌ای که صفحه‌ها باید پایین فهرستشان بگذارند تا آخرین مورد زیر نوار شناور گم نشود
 * (ارتفاع نوار + فاصله‌اش از پایین + نوار سیستم).
 */
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

/**
 * صفحه‌ی اصلی اپ با نوار پایینِ شناور: خلاصه، تراکنش‌ها، بررسی، تنظیمات.
 * محتوای صفحه تا پایین کشیده می‌شود و زیر نوار شیشه‌ای (تار) دیده می‌شود.
 * موقع باز شدن اپ: پیامک‌ها خوانده می‌شوند؛ اگر پیامک تازه‌ای در «صندوق بررسی» باشد،
 * اول تب «بررسی» باز می‌شود (به‌جای نوتیفیکیشن — تا نوتیفیکیشن‌های اپ همیشه معتبر بمانند).
 */
@Composable
fun MainScreen() {
    val container = (LocalContext.current.applicationContext as JibitoApplication).container
    var tab by rememberSaveable { mutableStateOf(Tab.Summary) }
    val pendingFlow = remember { container.reviewRepository.observePending() }
    val pending by pendingFlow.collectAsState(initial = emptyList())
    val hazeState = remember { HazeState() }

    LaunchedEffect(Unit) {
        container.transactionRepository.syncFromSms()
        if (container.reviewRepository.countNotYetShown() > 0) {
            container.reviewRepository.markAllShown()
            tab = Tab.Review
        }
    }

    // دکمه‌ی برگشت گوشی: از هر تب ← خلاصه؛ از خلاصه ← خروج
    BackHandler(enabled = tab != Tab.Summary) { tab = Tab.Summary }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSpace = FloatingNavBarHeight + FloatingNavBarBottomMargin + navInset + 8.dp

    Box(Modifier.fillMaxSize()) {
        // محتوا: تا پایین صفحه کشیده می‌شود؛ فاصله‌ی پایین را خود صفحه‌ها با LocalBottomBarSpace می‌گذارند
        CompositionLocalProvider(LocalBottomBarSpace provides bottomSpace) {
            Column(
                Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(WindowInsets.navigationBars)
                    .haze(state = hazeState)
            ) {
                if (pending.isNotEmpty() && tab != Tab.Review) {
                    Box(Modifier.statusBarsPadding()) {
                        ReviewBanner(count = pending.size, onClick = { tab = Tab.Review })
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        // بنر خودش فاصله‌ی نوار وضعیت را گذاشته
                        .then(if (pending.isNotEmpty() && tab != Tab.Review) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)
                ) {
                    when (tab) {
                        Tab.Summary -> SummaryScreen()
                        Tab.Transactions -> SmsListScreen()
                        Tab.Review -> ReviewScreen(onClose = { tab = Tab.Summary })
                        Tab.Settings -> SettingsScreen()
                    }
                }
            }
        }

        FloatingNavBar(
            items = listOf(
                NavItem(NavIcons.Summary, stringResource(Tab.Summary.label)),
                NavItem(NavIcons.Transactions, stringResource(Tab.Transactions.label)),
                NavItem(NavIcons.Review, stringResource(Tab.Review.label), badge = pending.size),
                NavItem(NavIcons.Settings, stringResource(Tab.Settings.label)),
            ),
            selectedIndex = tab.ordinal,
            onSelect = { tab = Tab.entries[it] },
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ReviewBanner(count: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(colors.secondaryContainer, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📩", fontSize = 18.sp)
        Text(
            Jalali.toPersianDigits(stringResource(R.string.review_banner, count)),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onSecondaryContainer,
        )
        Text("›", fontSize = 20.sp, color = colors.onSecondaryContainer)
    }
}
