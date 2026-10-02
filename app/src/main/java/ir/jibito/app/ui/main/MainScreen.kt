package ir.jibito.app.ui.main

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
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
import ir.jibito.app.util.ErrorLog
import kotlinx.coroutines.CancellationException
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

private enum class Tab(val route: String, val label: Int) {
    Summary("summary", R.string.tab_summary),
    Transactions("transactions", R.string.tab_transactions),
    Review("review", R.string.tab_review),
    Settings("settings", R.string.tab_settings),
}

/**
 * فضای خالی‌ای که صفحه‌ها باید پایین فهرستشان بگذارند تا آخرین مورد زیر نوار شناور گم نشود
 * (ارتفاع نوار + فاصله‌اش از پایین + نوار سیستم).
 */
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

/**
 * صفحه‌ی اصلی اپ با نوار پایینِ شناور: خلاصه، تراکنش‌ها، بررسی، تنظیمات.
 * تب‌ها با Navigation-Compose عوض می‌شوند: هر تب حالت خودش (جای اسکرول، جست‌وجو، برگه‌ی باز) را نگه می‌دارد
 * و دکمه‌ی برگشت از هر تب به «خلاصه» و از خلاصه به بیرون می‌رود.
 * محتوای صفحه تا پایین کشیده می‌شود و زیر نوار شیشه‌ای (تار) دیده می‌شود.
 * موقع باز شدن اپ: پیامک‌ها خوانده می‌شوند؛ اگر پیامک تازه‌ای در «صندوق بررسی» باشد،
 * اول تب «بررسی» باز می‌شود (به‌جای نوتیفیکیشن — تا نوتیفیکیشن‌های اپ همیشه معتبر بمانند).
 *
 * @param openTransactionId تراکنشی که باید باز شود (لمس نوتیفیکیشن «این خرج مال چی بود؟»)
 * @param onOpenHandled بعد از رسیدگی به openTransactionId صدا زده می‌شود تا دوباره باز نشود
 */
@Composable
fun MainScreen(openTransactionId: Long? = null, onOpenHandled: () -> Unit = {}) {
    val container = (LocalContext.current.applicationContext as JibitoApplication).container
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val tab = Tab.entries.firstOrNull { it.route == backStackEntry?.destination?.route } ?: Tab.Summary
    fun go(target: Tab) {
        nav.navigate(target.route) {
            // الگوی استاندارد نوار پایین: پشته همیشه «خلاصه ← تب فعلی» است و حالت هر تب ذخیره و برگردانده می‌شود
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    // «خرج‌های بی‌دسته» از کارهای لازم ← تراکنش‌ها با فیلتر
    var onlyUncategorized by rememberSaveable { mutableStateOf(false) }
    // تراکنشی که از نوتیفیکیشن آمده و هنوز برگه‌اش باز نشده
    var pendingOpen by rememberSaveable { mutableStateOf<Long?>(null) }
    val pendingFlow = remember { container.reviewRepository.observePending() }
    val pending by pendingFlow.collectAsState(initial = emptyList())
    val hazeState = remember { HazeState() }

    LaunchedEffect(openTransactionId) {
        val id = openTransactionId ?: return@LaunchedEffect
        onlyUncategorized = false
        pendingOpen = id
        go(Tab.Transactions)
        onOpenHandled()
    }

    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        // خطای همگام‌سازی نباید اپ را موقع باز شدن ببندد (وگرنه هر بار باز کردن = بسته شدن)؛ ثبت می‌شود
        try {
            container.transactionRepository.syncFromSms()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ErrorLog.record(appContext, "sync on open", e)
        }
        if (container.reviewRepository.countNotYetShown() > 0) {
            container.reviewRepository.markAllShown()
            // اگر کاربر از نوتیفیکیشن یک تراکنش آمده، همان مهم‌تر است
            if (pendingOpen == null) go(Tab.Review)
        }
    }

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
                // بنر زرد «پیامک‌های منتظر» حذف شد: عدد روی تب «بررسی» و مورد «کارهای لازم» در خلاصه کافی است
                Box(
                    Modifier
                        .weight(1f)
                        // بنر خودش فاصله‌ی نوار وضعیت را گذاشته
                ) {
                    NavHost(
                        navController = nav,
                        startDestination = Tab.Summary.route,
                        modifier = Modifier.fillMaxSize(),
                        // جابه‌جایی نرم و سریع بین تب‌ها (پیش‌فرض کتابخانه کُند است)
                        enterTransition = { fadeIn(tween(180)) },
                        exitTransition = { fadeOut(tween(120)) },
                        popEnterTransition = { fadeIn(tween(180)) },
                        popExitTransition = { fadeOut(tween(120)) },
                    ) {
                        composable(Tab.Summary.route) {
                            SummaryScreen(
                                pendingReview = pending.size,
                                onOpenReview = { go(Tab.Review) },
                                onOpenUncategorized = {
                                    onlyUncategorized = true
                                    go(Tab.Transactions)
                                },
                                onOpenTransactions = {
                                    onlyUncategorized = false
                                    go(Tab.Transactions)
                                },
                            )
                        }
                        composable(Tab.Transactions.route) {
                            SmsListScreen(
                                onlyUncategorized = onlyUncategorized,
                                onClearFilter = { onlyUncategorized = false },
                                openTransactionId = pendingOpen,
                                onOpened = { pendingOpen = null },
                            )
                        }
                        composable(Tab.Review.route) { ReviewScreen(onClose = { go(Tab.Summary) }) }
                        composable(Tab.Settings.route) { SettingsScreen() }
                    }
                }
            }
        }

        // محوشدگی ملایم پایین صفحه: محتوایی که زیر نوار شناور و نوار سیستم می‌رود، بی‌پرده دیده نشود
        val fadeColor = MaterialTheme.colorScheme.background
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(navInset + FloatingNavBarBottomMargin + FloatingNavBarHeight / 2)
                .background(Brush.verticalGradient(listOf(fadeColor.copy(alpha = 0f), fadeColor.copy(alpha = 0.92f))))
        )

        FloatingNavBar(
            items = listOf(
                NavItem(NavIcons.Summary, stringResource(Tab.Summary.label)),
                NavItem(NavIcons.Transactions, stringResource(Tab.Transactions.label)),
                NavItem(NavIcons.Review, stringResource(Tab.Review.label), badge = pending.size),
                NavItem(NavIcons.Settings, stringResource(Tab.Settings.label)),
            ),
            selectedIndex = tab.ordinal,
            onSelect = { go(Tab.entries[it]) },
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
