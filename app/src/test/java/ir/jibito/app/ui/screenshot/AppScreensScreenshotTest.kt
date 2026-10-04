package ir.jibito.app.ui.screenshot

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import ir.jibito.app.JibitoApplication
import ir.jibito.app.MainActivity
import ir.jibito.app.R
import ir.jibito.app.data.category.CategorySeeder
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.BudgetEntity
import ir.jibito.app.data.local.entity.OverallBudgetEntity
import ir.jibito.app.data.local.entity.RecurringPaymentEntity
import ir.jibito.app.data.local.entity.ReviewSmsEntity
import ir.jibito.app.data.local.entity.TransactionFlowEntity
import ir.jibito.app.data.repository.SOURCE_MANUAL
import ir.jibito.app.data.repository.SOURCE_SMS_MANUAL
import ir.jibito.app.data.security.AppLockSession
import ir.jibito.app.ui.theme.DarkMode
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * اسکرین‌شات «همه‌ی صفحه‌های» اپ، همان‌طور که کاربر می‌بیند: خود MainActivity با دیتابیس پرشده باز می‌شود
 * و تست مثل کاربر لمس می‌کند تا به هر صفحه، برگه (bottom sheet) و پنجره (dialog) برسد.
 * برخلاف [ScreenshotTest] (تکه‌های ظاهر در همه‌ی پوسته‌ها)، این‌جا کل صفحه با همه‌ی پنجره‌های رویش گرفته می‌شود.
 *
 * ساعت ثابت است (پنجشنبه ۹ مهر ۱۴۰۵، ساعت ۲۰ تهران): کد اپ System.currentTimeMillis را صدا می‌زند و
 * Robolectric با instrumentedPackages آن را به ساعت ساختگی (SystemClock.setCurrentTimeMillis) وصل می‌کند،
 * تا ماه جاری، «امروز/دیروز» و روزهای مانده هر بار یکی باشند.
 * تصویرها: app/src/test/screenshots/app_*.png
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w400dp-h860dp-xxhdpi",
    application = ScreenshotApplication::class,
    instrumentedPackages = ["ir.jibito.app"],
)
class AppScreensScreenshotTest {

    companion object {
        const val PHONE = "w400dp-h860dp-xxhdpi"
        /** صفحه‌های بلند (اسکرول‌دار) یک‌جا و کامل */
        const val TALL = "w400dp-h1700dp-xxhdpi"
        const val ZONE = "Asia/Tehran"
        const val HOUR = 60 * 60 * 1000L
        const val DAY = 24 * HOUR
        const val BACKUP_PASSWORD = "jibito-1405"
    }

    @get:Rule
    val compose = createEmptyComposeRule()

    /** پنجشنبه ۹ مهر ۱۴۰۵، ساعت ۲۰ تهران */
    private val now = Calendar.getInstance(TimeZone.getTimeZone(ZONE)).apply { clear(); set(2026, Calendar.OCTOBER, 1, 20, 0) }.timeInMillis
    private lateinit var savedZone: TimeZone

    private val app get() = RuntimeEnvironment.getApplication() as JibitoApplication
    private val container get() = app.container
    private val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))

    @Before
    fun setUp() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE))
        SystemClock.setCurrentTimeMillis(now)
        // اگر ساعت ساختگی به کد اپ نرسد، همه‌ی تصویرها با ماه واقعی ساخته می‌شوند؛ پس همین اول بشکند
        assertEquals(JalaliMonth(1405, 7), JalaliMonth.current())
        // «حذف انیمیشن‌ها»ی گوشی: جیبی و «؟»ها ثابت می‌مانند تا تصویرها هر بار یکی باشند
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        app.deleteDatabase(AppDatabase.NAME)
        AppLockSession.unlocked = false
        AppLockSession.backgroundedAt = 0
        // صفحه‌ی «۳۴۲ تراکنش پیدا شد» فقط بار اول است و تست جدای خودش را دارد
        container.firstRun.markDone()
    }

    @After
    fun tearDown() {
        runCatching { container.database.close() }
        app.deleteDatabase(AppDatabase.NAME)
        // بازگردانیِ آماده در تست پشتیبان، نباید در شروع تست بعدی اعمال شود
        java.io.File(app.filesDir, "pending_restore").deleteRecursively()
        TimeZone.setDefault(savedZone)
    }

    // ───────────────────────── داده‌ی نمونه ─────────────────────────

    private fun at(daysAgo: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone(ZONE)).apply {
            timeInMillis = now - daysAgo * DAY
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private var nextSmsId = 1L

    /** یک تراکنش؛ مبلغ‌ها به تومان (در دیتابیس ریال) */
    private fun flow(
        date: Long,
        toman: Long,
        merchant: String? = null,
        category: Long? = null,
        deposit: Boolean = false,
        bankId: Int? = 11,
        balanceToman: Long? = null,
        failed: Boolean = false,
        suggested: String? = null,
        auto: Boolean = false,
        manual: Boolean = false,
    ): TransactionFlowEntity {
        val rial = toman * 10
        val body = if (manual) null else buildString {
            append(if (deposit) "واریز: " else "برداشت: ")
            append(Jalali.toPersianDigits("%,d".format(rial)))
            append(" ریال")
            merchant?.let { append("\n").append(it) }
            balanceToman?.let { append("\nمانده: ").append(Jalali.toPersianDigits("%,d".format(it * 10))) }
        }
        return TransactionFlowEntity(
            smsId = if (manual) null else nextSmsId++,
            bankId = if (manual) null else bankId,
            flowType = if (deposit) 1 else 2,
            amount = rial,
            remainAfter = balanceToman?.let { it * 10 },
            dateEpoch = date,
            merchant = merchant,
            suggestedCategory = suggested,
            isFailedPurchase = failed,
            categoryId = category,
            description = null,
            smsContent = body,
            source = if (manual) SOURCE_MANUAL else SOURCE_SMS_MANUAL,
            updatedAt = date,
            notifiedAt = date,
            isAutoCategorized = auto,
        )
    }

    /**
     * یک ماه واقعی: خرج‌های دسته‌دار و بی‌دسته، پیشنهاد دسته، خرید ناموفق، جفت «انتقال به خودت؟»،
     * ثبت دستی، درآمد، ۵ ماه قبل برای روند، بودجه‌ها، پرداخت ماهانه، دسته‌ی شخصی و دو پیامک مبهم.
     */
    private fun seed() = runBlocking {
        val db = container.database
        CategorySeeder(db).ensure()
        val categories = db.categoryDao()
        suspend fun id(code: String) = categories.byCode(code)!!.id

        val market = id("food.market")
        val bread = id("food.bread")
        val dining = id("dining")
        val cafe = id("dining.cafe")
        val restaurant = id("dining.restaurant")
        val taxi = id("transport.taxi")
        val fuel = id("transport.car.fuel")
        val clothes = id("clothing.clothes")
        val pharmacy = id("health.pharmacy")
        val building = id("home.services.building")
        val salary = id("income.salary")

        val rows = mutableListOf(
            // امروز
            flow(at(0, 19, 30), 185_000, "کافه لمیز", cafe, balanceToman = 41_230_000),
            flow(at(0, 18, 40), 420_000, "اسنپ", taxi, auto = true),
            flow(at(0, 10, 15), 25_000_000, category = salary, deposit = true, bankId = 15, balanceToman = 66_230_000),
            // دیروز
            flow(at(1, 21, 10), 1_275_000, "فروشگاه افق کوروش", suggested = "سوپرمارکت"),
            flow(at(1, 17, 0), 300_000, "دیجی‌کالا", failed = true),
            flow(at(1, 13, 20), 5_000_000, "کارت ۶۰۳۷", balanceToman = 36_230_000),
            flow(at(1, 13, 22), 5_000_000, deposit = true, bankId = 15, balanceToman = 41_230_000),
            flow(at(1, 9, 0), 90_000),
            // روزهای قبل همین ماه
            flow(at(2, 20, 0), 3_240_000, "هایپراستار", market),
            flow(at(2, 12, 30), 1_850_000, "رستوران نایب", restaurant),
            flow(at(3, 18, 0), 1_450_000, "کافه رخ", cafe),
            flow(at(3, 8, 0), 900_000, "جایگاه سوخت", fuel, bankId = 15),
            flow(at(4, 19, 0), 2_100_000, "زارا", clothes),
            flow(at(5, 11, 0), 450_000, "داروخانه دکتر عبیدی", pharmacy),
            flow(at(5, 7, 30), 60_000, "نان سنگک", bread, manual = true),
            flow(at(6, 10, 0), 1_500_000, "شارژ ساختمان", building),
            flow(at(7, 16, 0), 1_320_000, "کافه نادری", cafe),
            flow(at(8, 14, 0), 500_000, "ایرانسل"),
        )
        // ۵ ماه قبل: روند خرج و «این پرداخت ماهانه است؟»
        val months = listOf(38_000_000L, 52_500_000L, 41_200_000L, 66_000_000L, 47_800_000L)
        months.forEachIndexed { i, total ->
            val back = 30 * (i + 1)
            rows += flow(at(back + 1, 14, 0), 500_000, "ایرانسل")
            rows += flow(at(back + 3, 20, 0), total * 45 / 100, "هایپراستار", market)
            rows += flow(at(back + 6, 13, 0), total * 25 / 100, "رستوران نایب", restaurant)
            rows += flow(at(back + 10, 18, 0), total * 30 / 100 - 500_000, "زارا", clothes)
            rows += flow(at(back + 2, 10, 0), 25_000_000, category = salary, deposit = true, bankId = 15)
        }
        db.transactionFlowDao().insertAll(rows)

        // بودجه: کل ماه ۳۰ میلیون، «رستوران و کافه» ۴٫۵ میلیون (از آن گذشته)
        db.summaryDao().upsertOverallBudget(OverallBudgetEntity(monthlyLimitRial = 300_000_000))
        db.summaryDao().upsertBudget(BudgetEntity(categoryId = dining, monthlyLimitRial = 45_000_000))

        db.recurringDao().insert(
            RecurringPaymentEntity(title = "اجاره خانه", amountRial = 150_000_000, dayOfMonth = 1, lastRemindedMonthKey = null, createdAt = now - 60 * DAY)
        )

        val repository = container.transactionRepository
        val swim = (repository.createCategory("باشگاه شنا", null, 2, "⭐") as ir.jibito.app.data.category.CreateCategoryResult.Created).id
        repository.createCategory("بلیت استخر", swim, 2, null)

        // «نشان داده شده»: وگرنه اپ موقع باز شدن خودش صفحه‌ی بررسی را باز می‌کند
        val shownAt = now - HOUR
        db.reviewDao().insertAll(
            listOf(
                ReviewSmsEntity(
                    smsId = 9_001, sender = "+989120000000",
                    body = "مبلغ ۲٬۵۰۰٬۰۰۰ ریال از حساب ۱۲۳۴ کسر شد.\nموجودی: ۴۱٬۲۳۰٬۰۰۰",
                    dateEpoch = at(0, 17, 5), bankId = null, autoShownAt = shownAt,
                ),
                ReviewSmsEntity(
                    smsId = 9_002, sender = "200033",
                    body = "انتقال وجه\n۸۵۰,۰۰۰\nبه ۶۲۱۹۸۶۱۰۴۴۵۵۶۶۷۷\n۱۴۰۵/۰۷/۰۸",
                    dateEpoch = at(1, 11, 45), bankId = 11, autoShownAt = shownAt,
                ),
            )
        )
    }

    // ───────────────────────── باز کردن اپ و لمس ─────────────────────────

    private fun grantSms() = shadowOf(app).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    /** اپ را مثل کاربر باز می‌کند؛ [block] روی صفحه کار می‌کند و عکس می‌گیرد */
    private fun launch(
        dark: Boolean = false,
        tall: Boolean = false,
        block: (ActivityScenario<MainActivity>) -> Unit,
    ) {
        RuntimeEnvironment.setQualifiers(if (tall) TALL else PHONE)
        container.themeSettings.setDarkMode(if (dark) DarkMode.DARK else DarkMode.LIGHT)
        // ساعت Compose را خود تست جلو می‌برد: مکان‌نمای چشمک‌زنِ فیلدهای متن هیچ‌وقت «آرام» نمی‌شود
        // و با جلو رفتن خودکار، صبر برای آرامش صفحه (در پنجره‌ها و برگه‌ها) تمام نمی‌شد
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            settle()
            block(scenario)
        }
    }

    /** یک قدم: ۱۰۰ میلی‌ثانیه‌ی Compose جلو می‌رود و کارهای رشته‌ی اصلی انجام می‌شوند */
    private fun step() {
        compose.mainClock.advanceTimeBy(100)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        // دیتابیس روی رشته‌های دیگر جواب می‌دهد
        Thread.sleep(25)
    }

    /** تا دیتابیس جواب بدهد و انیمیشن‌ها (باز شدن برگه و پنجره) تمام شوند */
    private fun settle() = repeat(20) { step() }

    private fun str(@StringRes id: Int, vararg args: Any): String = app.getString(id, *args)

    /** حداکثر حدود ۱۵ ثانیه (ساعت سیستم در این تست ثابت است؛ پس با شمردن قدم‌ها) */
    private fun waitFor(matcher: SemanticsMatcher, steps: Int = 120) {
        repeat(steps) {
            step()
            if (compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return
        }
        throw AssertionError("پیدا نشد: ${matcher.description}")
    }

    private fun clickLabel(label: String) =
        SemanticsMatcher("onClickLabel = $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    /** اولین گره‌ی قابل لمس با این نوشته (یا خود نوشته، که لمسش به دکمه‌ی زیرش می‌رسد) */
    private fun node(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        waitFor(matcher)
        val clickable = compose.onAllNodes(matcher and hasClickAction())
        return if (clickable.fetchSemanticsNodes().isNotEmpty()) clickable.onFirst() else compose.onAllNodes(matcher).onFirst()
    }

    /**
     * لمس با خود «کار» دکمه، نه با مختصات: دکمه‌ای که پایین برگه‌ی نیمه‌باز (بیرون از صفحه) است هم لمس می‌شود.
     * و بعد از لمس منتظر «آرام شدن» نمی‌ماند (نوار پیشرفتِ کار پس‌زمینه تا ساعت جلو نرود تمام نمی‌شود).
     */
    private fun tap(matcher: SemanticsMatcher) {
        val n = node(matcher)
        runCatching { n.performScrollTo() }
        if (n.fetchSemanticsNode().config.contains(SemanticsActions.OnClick)) {
            n.performSemanticsAction(SemanticsActions.OnClick)
        } else {
            n.performClick()
        }
        settle()
    }

    private fun tapText(text: String) = tap(hasText(text))
    private fun tapText(@StringRes id: Int) = tapText(str(id))
    private fun tapDescription(text: String) = tap(hasContentDescription(text))

    private fun typeInto(index: Int, text: String) {
        waitFor(hasSetTextAction())
        compose.onAllNodes(hasSetTextAction())[index].performTextInput(text)
        settle()
    }

    private fun shot(name: String, dark: Boolean = false) {
        settle()
        captureScreenRoboImage("src/test/screenshots/app_${name}_${if (dark) "dark" else "light"}.png", options)
    }

    private fun openTab(@StringRes label: Int) = tap(hasText(str(label)) and hasClickAction())
    private fun openSettings() = tapDescription(str(R.string.settings_title))

    private fun openReview() {
        openTab(R.string.tab_todo)
        tapText(Jalali.toPersianDigits(str(R.string.todo_review, 2)))
        waitFor(hasContentDescription(str(R.string.review_more)))
    }

    /** در فهرست بی‌دسته‌ها اولین ردیف است (در فهرست کامل، در صفحه‌ی گوشی پایین‌تر از لبه می‌افتد) */
    private fun openUncategorizedPurchase() {
        openTab(R.string.tab_transactions)
        tap(hasText(str(R.string.filter_uncategorized)) and hasClickAction())
        tapText("فروشگاه افق کوروش")
    }

    // ───────────────────────── شروع: خوش‌آمد، اجازه، قفل ─────────────────────────

    @Test
    fun onboarding() {
        for (dark in listOf(false, true)) {
            launch(dark) { scenario ->
                waitFor(hasText(str(R.string.start_button)))
                shot("welcome", dark)
                tapText(R.string.start_button)
                waitFor(hasText(str(R.string.perm_allow)))
                shot("permission", dark)
                // «اجازه نمی‌دم» در پنجره‌ی اندروید
                tapText(R.string.perm_allow)
                scenario.onActivity { activity ->
                    val request = shadowOf(activity).lastRequestedPermission
                    @Suppress("DEPRECATION")
                    activity.onRequestPermissionsResult(
                        request.requestCode,
                        request.requestedPermissions,
                        IntArray(request.requestedPermissions.size) { PackageManager.PERMISSION_DENIED },
                    )
                }
                shot("permission_denied", dark)
            }
        }
    }

    @Test
    fun lock() {
        seed()
        grantSms()
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        container.appLockSettings.setEnabled(true)
        for (dark in listOf(false, true)) {
            AppLockSession.unlocked = false
            launch(dark) {
                waitFor(hasText(str(R.string.lock_title)))
                shot("lock", dark)
            }
        }
    }

    // ───────────────────────── خلاصه ─────────────────────────

    @Test
    fun summary() {
        seed()
        grantSms()
        for (dark in listOf(false, true)) {
            launch(dark, tall = true) {
                waitFor(hasText(str(R.string.glance_show_all)))
                shot("summary", dark)
                tapText(R.string.glance_show_all)
                waitFor(hasText(str(R.string.glance_all_title)))
                shot("summary_all_categories", dark)
            }
        }
    }

    @Test
    fun summaryOverlays() {
        seed()
        grantSms()
        launch {
            // انتخاب ماه
            tapText(JalaliMonth(1405, 7).title)
            shot("summary_month_menu")
        }
        launch {
            // بودجه‌ی کل ماه (لمس عدد بزرگ)
            tap(clickLabel(str(R.string.overall_dialog_title)))
            shot("summary_overall_budget_dialog")
        }
        launch {
            // جزئیات دسته و بودجه‌اش
            tapText(R.string.glance_show_all)
            tapText("رستوران و کافه")
            shot("summary_category_detail")
            tapText(R.string.detail_edit_budget)
            shot("summary_category_budget_dialog")
        }
    }

    // ───────────────────────── تراکنش‌ها ─────────────────────────

    @Test
    fun transactions() {
        seed()
        grantSms()
        for (dark in listOf(false, true)) {
            launch(dark, tall = true) {
                openTab(R.string.tab_transactions)
                waitFor(hasText("فروشگاه افق کوروش"))
                shot("transactions", dark)
                tap(hasText(str(R.string.filter_uncategorized)) and hasClickAction())
                shot("transactions_uncategorized", dark)
            }
        }
    }

    @Test
    fun transactionsSearch() {
        seed()
        grantSms()
        launch {
            openTab(R.string.tab_transactions)
            tap(clickLabel(str(R.string.cd_search)))
            shot("transactions_search")
            tapText(R.string.search_chip_date)
            waitFor(hasText(str(R.string.search_sheet_when)))
            shot("transactions_filter_sheet")
            tapText(R.string.search_date_custom_long)
            waitFor(hasText(str(R.string.search_pick_month)))
            shot("transactions_filter_month_dialog")
        }
    }

    @Test
    fun categoryPicker() {
        seed()
        grantSms()
        launch {
            openUncategorizedPurchase()
            shot("category_picker")
            tapText(R.string.sheet_show_sms)
            shot("category_picker_sms")
            tapText(R.string.sheet_report_wrong)
            waitFor(hasText(str(R.string.report_reason_title)))
            shot("report_wrong_dialog")
        }
        launch {
            openUncategorizedPurchase()
            tapText(R.string.custom_add_root_short)
            shot("new_category_dialog")
        }
    }

    @Test
    fun manualEntry() {
        seed()
        grantSms()
        launch {
            openTab(R.string.tab_transactions)
            tapDescription(str(R.string.cd_add_manual))
            shot("manual_entry")
        }
    }

    // ───────────────────────── کارها و بررسی ─────────────────────────

    @Test
    fun todo() {
        seed()
        grantSms()
        for (dark in listOf(false, true)) {
            launch(dark, tall = true) {
                openTab(R.string.tab_todo)
                waitFor(hasText(Jalali.toPersianDigits(str(R.string.todo_review, 2))))
                shot("todo", dark)
            }
        }
    }

    @Test
    fun review() {
        seed()
        grantSms()
        for (dark in listOf(false, true)) {
            launch(dark, tall = true) {
                openReview()
                shot("review", dark)
            }
        }
    }

    @Test
    fun reviewOverlays() {
        seed()
        grantSms()
        launch {
            openReview()
            tapDescription(str(R.string.review_more))
            shot("review_menu")
        }
        launch {
            openReview()
            tapText(R.string.review_pick_bank)
            shot("review_bank_picker")
        }
    }

    // ───────────────────────── تنظیمات ─────────────────────────

    @Test
    fun settings() {
        seed()
        grantSms()
        for (dark in listOf(false, true)) {
            launch(dark, tall = true) {
                openSettings()
                waitFor(hasText(str(R.string.settings_advanced_title)))
                shot("settings", dark)
            }
        }
    }

    @Test
    fun settingsPages() {
        seed()
        grantSms()
        val pages = listOf(
            "backup" to R.string.security_title,
            "export" to R.string.export_title,
            "category_display" to R.string.settings_display_title,
            "custom_categories" to R.string.settings_custom_title,
            "recurring" to R.string.recurring_title,
            "theme" to R.string.settings_theme_title,
            "privacy" to R.string.settings_privacy_title,
            "advanced" to R.string.settings_advanced_title,
        )
        for ((name, title) in pages) {
            launch(tall = true) {
                openSettings()
                tapText(title)
                shot("settings_$name")
            }
        }
    }

    @Test
    fun customCategoryDialogs() {
        seed()
        grantSms()
        val more = str(R.string.settings_custom_more, "باشگاه شنا")
        launch {
            openSettings()
            tapText(R.string.settings_custom_title)
            tapDescription(more)
            shot("settings_custom_menu")
            tapText(R.string.settings_custom_rename)
            shot("settings_custom_rename_dialog")
        }
        launch {
            openSettings()
            tapText(R.string.settings_custom_title)
            tapDescription(more)
            tapText(R.string.settings_custom_delete)
            shot("settings_custom_delete_dialog")
        }
        launch {
            openSettings()
            tapText(R.string.settings_custom_title)
            tapText(R.string.settings_custom_new)
            shot("settings_custom_new_dialog")
        }
    }

    @Test
    fun recurringDialog() {
        seed()
        grantSms()
        launch {
            openSettings()
            tapText(R.string.recurring_title)
            tapText(R.string.recurring_add)
            shot("settings_recurring_add_dialog")
        }
    }

    @Test
    fun backupDialogs() {
        seed()
        grantSms()
        launch {
            openSettings()
            tapText(R.string.security_title)
            tapText(R.string.backup_export_button)
            shot("backup_export_password_dialog")
        }
        // یک پشتیبان واقعی، تا «برگردوندن» تا آخر برود
        val backup = ByteArrayOutputStream().also { out ->
            runBlocking { container.backupManager.export(out, BACKUP_PASSWORD.toCharArray()) }
        }.toByteArray()
        val uri = Uri.parse("content://ir.jibito.test/backup.jibito")
        shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(backup))
        launch { scenario ->
            openSettings()
            tapText(R.string.security_title)
            tapText(R.string.backup_restore_button)
            shot("backup_restore_confirm_dialog")
            tapText(R.string.backup_restore_choose_file)
            // فایل انتخاب‌شده در پنجره‌ی «انتخاب فایل» اندروید
            scenario.onActivity { activity ->
                val shadow = shadowOf(activity)
                val request = shadow.nextStartedActivityForResult
                shadow.receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(uri))
            }
            settle()
            shot("backup_restore_password_dialog")
            typeInto(0, BACKUP_PASSWORD)
            tapText(R.string.backup_restore_go)
            waitFor(hasText(str(R.string.backup_restore_ready_title)), steps = 200)
            shot("backup_restore_ready_dialog")
        }
    }

    // ───────────────────────── اپ خالی (هنوز هیچ تراکنشی نیست) ─────────────────────────

    @Test
    fun empty() {
        grantSms()
        launch {
            settle()
            shot("summary_empty")
            openTab(R.string.tab_transactions)
            shot("transactions_empty")
            openTab(R.string.tab_todo)
            shot("todo_empty")
        }
    }
}
