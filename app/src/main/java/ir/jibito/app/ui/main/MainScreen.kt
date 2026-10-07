package ir.jibito.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

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
import dev.chrisbanes.haze.hazeSource
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.ui.reports.ReportsScreen
import ir.jibito.app.ui.account.AccountScreen
import ir.jibito.app.data.wallet.AccountRef
import android.net.Uri
import ir.jibito.app.ui.review.ReviewScreen
import ir.jibito.app.ui.settings.SettingsScreen
import ir.jibito.app.ui.settings.WhatsNewDialog
import ir.jibito.app.ui.settings.installedVersion
import ir.jibito.app.ui.settings.pendingWhatsNew
import ir.jibito.app.ui.smslist.SmsListScreen
import ir.jibito.app.ui.summary.SummaryScreen
import ir.jibito.app.ui.summary.SummaryViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.ui.summary.rememberTodoStories
import ir.jibito.app.ui.todo.TodoScreen
import ir.jibito.app.util.Changelog
import ir.jibito.app.util.Jalali
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ir.jibito.app.ui.welcome.FirstRunReveal
import ir.jibito.app.ui.welcome.RevealStats

private enum class Tab(val route: String, val label: Int) {
    Summary("summary", R.string.tab_summary),
    Transactions("transactions", R.string.tab_transactions),
    Reports("reports", R.string.tab_reports),
    Todo("todo", R.string.tab_todo),
}

/** صفحه‌هایی که تب نیستند: «بررسی» از داخل «کارها» و «تنظیمات» از چرخ‌دنده‌ی «خلاصه» باز می‌شوند */
private const val ROUTE_REVIEW = "review"
private const val ROUTE_SETTINGS = "settings"
/** جزئیات یک حساب از کارت «موجودی حساب‌ها» در «گزارش‌ها»؛ {key} = AccountRef.key یا ACCOUNT_ALL */
private const val ROUTE_ACCOUNT = "account/{key}"
private const val ACCOUNT_ALL = "all"

/**
 * فضای خالی‌ای که صفحه‌ها باید پایین فهرستشان بگذارند تا آخرین مورد زیر نوار شناور گم نشود
 * (ارتفاع نوار + فاصله‌اش از پایین + نوار سیستم).
 */
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

/**
 * صفحه‌ی اصلی اپ با نوار پایینِ شناور: خلاصه، تراکنش‌ها، گزارش‌ها، کارها.
 * «بررسی» یکی از کارهای تب «کارها» است و «تنظیمات» با دکمه‌ی بالای «خلاصه» باز می‌شود.
 * تب‌ها با Navigation-Compose عوض می‌شوند: هر تب حالت خودش (جای اسکرول، جست‌وجو، برگه‌ی باز) را نگه می‌دارد
 * و دکمه‌ی برگشت از هر تب به «خلاصه» و از خلاصه به بیرون می‌رود.
 * محتوای صفحه تا پایین کشیده می‌شود و زیر نوار شیشه‌ای (تار) دیده می‌شود.
 * موقع باز شدن اپ: پیامک‌ها خوانده می‌شوند؛ اگر پیامک تازه‌ای در «صندوق بررسی» باشد،
 * اول صندوق «بررسی» (داخل تب «کارها») باز می‌شود (به‌جای نوتیفیکیشن — تا نوتیفیکیشن‌های اپ همیشه معتبر بمانند).
 *
 * @param openTransactionId تراکنشی که باید باز شود (لمس نوتیفیکیشن «این خرج مال چی بود؟»)
 * @param onOpenHandled بعد از رسیدگی به openTransactionId صدا زده می‌شود تا دوباره باز نشود
 */
@Composable
fun MainScreen(openTransactionId: Long? = null, onOpenHandled: () -> Unit = {}) {
    val container = (LocalContext.current.applicationContext as JibitoApplication).container
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    // «بررسی» زیرمجموعه‌ی «کارها»ست و جزئیات حساب زیرمجموعه‌ی «گزارش‌ها»؛ در «تنظیمات» و جزئیات حساب نوار پایین پنهان است
    val tab = when (route) {
        ROUTE_REVIEW -> Tab.Todo
        ROUTE_ACCOUNT -> Tab.Reports
        else -> Tab.entries.firstOrNull { it.route == route } ?: Tab.Summary
    }
    val showBar = route != ROUTE_SETTINGS && route != ROUTE_ACCOUNT
    fun go(target: Tab) {
        nav.navigate(target.route) {
            // الگوی استاندارد نوار پایین: پشته همیشه «خلاصه ← تب فعلی» است و حالت هر تب ذخیره و برگردانده می‌شود
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun openReview() {
        go(Tab.Todo)
        nav.navigate(ROUTE_REVIEW) { launchSingleTop = true }
    }
    fun openSettings() = nav.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
    fun openAccount(ref: AccountRef?) = nav.navigate("account/" + Uri.encode(ref?.key ?: ACCOUNT_ALL))
    // «خرج‌های بی‌دسته» از کارهای لازم ← تراکنش‌ها با فیلتر
    var onlyUncategorized by rememberSaveable { mutableStateOf(false) }
    // کارت بودجه در «کارها» ← جزئیات همان دسته در «خلاصه»
    var openCategory by rememberSaveable { mutableStateOf<Long?>(null) }
    // تراکنشی که از نوتیفیکیشن آمده و هنوز برگه‌اش باز نشده
    var pendingOpen by rememberSaveable { mutableStateOf<Long?>(null) }
    // «ثبت اولین خرج» در «خلاصه» ← برگه‌ی ثبت دستی در «تراکنش‌ها»
    var pendingManual by rememberSaveable { mutableStateOf(false) }
    val pendingFlow = remember { container.reviewRepository.observePending() }
    val pending by pendingFlow.collectAsState(initial = emptyList())
    val hazeState = remember { HazeState() }
    // عدد روی تب «کارها»: فقط کارهای فوری (پیشنهادها شمرده نمی‌شوند تا عدد هیچ‌وقت عادی نشود)
    val todoSummaryVm: SummaryViewModel = viewModel(key = "todo-count", factory = SummaryViewModel.factory(container.budgetRepository))
    val todoSummary by todoSummaryVm.summary.collectAsState()
    val todoCount = rememberTodoStories(
        todoSummary,
        pendingReview = pending.size,
        onOpenReview = {},
        onOpenUncategorized = {},
        onOpenTransactions = {},
        onOpenSettings = {},
        onOpenCategory = {},
    ).count { it.urgent }

    LaunchedEffect(openTransactionId) {
        val id = openTransactionId ?: return@LaunchedEffect
        onlyUncategorized = false
        pendingOpen = id
        go(Tab.Transactions)
        onOpenHandled()
    }

    // «۳۴۲ تراکنش پیدا شد»: فقط بار اولی که پیامک‌های این گوشی خوانده می‌شوند
    var reveal by remember { mutableStateOf<RevealStats?>(null) }

    // «چه چیزی تازه است»: بعد از به‌روزرسانی یک بار؛ «دیده‌شده» وقتی ثبت می‌شود که کاربر پنجره را ببندد
    var whatsNew by remember { mutableStateOf<List<Changelog.Release>>(emptyList()) }

    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        whatsNew = withContext(Dispatchers.IO) { pendingWhatsNew(appContext, container.whatsNewSeen) }
    }
    LaunchedEffect(Unit) {
        val repository = container.transactionRepository
        val firstRun = !container.firstRun.done
        // کاربری که از نسخه‌ی قبل به‌روز کرده، داده دارد و این صفحه را نمی‌بیند
        val hadData = !firstRun || repository.observeTransactions().first().isNotEmpty()
        var found = 0
        // خطای همگام‌سازی نباید اپ را موقع باز شدن ببندد (وگرنه هر بار باز کردن = بسته شدن)؛ ثبت می‌شود
        // «فعلاً دستی»: بدون اجازه‌ی پیامک چیزی برای خواندن نیست (و خطای بی‌جا هم ثبت نشود)
        val canReadSms = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        try {
            if (canReadSms) found = repository.syncFromSms()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ErrorLog.record(appContext, "sync on open", e)
        }
        if (firstRun && canReadSms) {
            container.firstRun.markDone()
            if (!hadData && found > 0) {
                // فهرست مشترک تراکنش‌ها کمی بعد از ذخیره به‌روز می‌شود
                val all = withTimeoutOrNull(3_000) { repository.observeTransactions().first { it.size >= found } }
                reveal = all?.let { RevealStats.of(it) } ?: RevealStats(count = found, months = 0, banks = 0)
            }
        }
        if (container.reviewRepository.countNotYetShown() > 0) {
            container.reviewRepository.markAllShown()
            // اگر کاربر از نوتیفیکیشن یک تراکنش آمده، همان مهم‌تر است
            if (pendingOpen == null) openReview()
        }
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSpace = if (showBar) FloatingNavBarHeight + FloatingNavBarBottomMargin + navInset + 8.dp else navInset + 8.dp

    Box(Modifier.fillMaxSize()) {
        // محتوا: تا پایین صفحه کشیده می‌شود؛ فاصله‌ی پایین را خود صفحه‌ها با LocalBottomBarSpace می‌گذارند
        CompositionLocalProvider(LocalBottomBarSpace provides bottomSpace) {
            Column(
                Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(WindowInsets.navigationBars)
                    .hazeSource(state = hazeState)
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
                                onOpenSettings = ::openSettings,
                                openCategoryId = openCategory,
                                onCategoryOpened = { openCategory = null },
                                onAddManual = {
                                    onlyUncategorized = false
                                    pendingManual = true
                                    go(Tab.Transactions)
                                },
                            )
                        }
                        composable(Tab.Transactions.route) {
                            SmsListScreen(
                                onlyUncategorized = onlyUncategorized,
                                onFilterChange = { onlyUncategorized = it },
                                openTransactionId = pendingOpen,
                                onOpened = { pendingOpen = null },
                                openManual = pendingManual,
                                onManualOpened = { pendingManual = false },
                            )
                        }
                        composable(Tab.Reports.route) { ReportsScreen(onOpenAccount = ::openAccount) }
                        composable(Tab.Todo.route) {
                            TodoScreen(
                                pendingReview = pending.size,
                                onOpenReview = ::openReview,
                                onOpenUncategorized = {
                                    onlyUncategorized = true
                                    go(Tab.Transactions)
                                },
                                onOpenTransactions = {
                                    onlyUncategorized = false
                                    go(Tab.Transactions)
                                },
                                onOpenSettings = ::openSettings,
                                // جزئیات دسته (و تغییر بودجه‌اش) در «خلاصه» است
                                onOpenCategory = {
                                    openCategory = it
                                    go(Tab.Summary)
                                },
                            )
                        }
                        composable(ROUTE_REVIEW) { ReviewScreen(onClose = { nav.popBackStack() }) }
                        composable(ROUTE_SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
                        composable(ROUTE_ACCOUNT) { entry ->
                            AccountScreen(
                                key = entry.arguments?.getString("key") ?: ACCOUNT_ALL,
                                onBack = { nav.popBackStack() },
                                // همان برگه‌ی تراکنش در «تراکنش‌ها» (مثل لمس نوتیفیکیشن)
                                onOpenTransaction = { id ->
                                    onlyUncategorized = false
                                    pendingOpen = id
                                    go(Tab.Transactions)
                                },
                                onOpenAccount = { openAccount(it) },
                            )
                        }
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

        if (showBar) {
            FloatingNavBar(
                items = listOf(
                    NavItem(NavIcons.Summary, stringResource(Tab.Summary.label)),
                    NavItem(NavIcons.Transactions, stringResource(Tab.Transactions.label)),
                    NavItem(NavIcons.Reports, stringResource(Tab.Reports.label)),
                    NavItem(NavIcons.Todo, stringResource(Tab.Todo.label), badge = todoCount),
                ),
                selectedIndex = tab.ordinal,
                onSelect = { go(Tab.entries[it]) },
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        reveal?.let { stats -> FirstRunReveal(stats, onDone = { reveal = null }) }
        if (whatsNew.isNotEmpty() && reveal == null) {
            WhatsNewDialog(whatsNew, onDismiss = {
                container.whatsNewSeen.markSeen(installedVersion(appContext))
                whatsNew = emptyList()
            })
        }
    }
}
