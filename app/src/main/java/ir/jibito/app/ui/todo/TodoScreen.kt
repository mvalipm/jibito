package ir.jibito.app.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.ui.common.JibiToast
import ir.jibito.app.ui.common.Mascot
import ir.jibito.app.ui.common.ToastMessage
import ir.jibito.app.ui.common.MascotFace
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.ui.summary.BudgetDialog
import ir.jibito.app.ui.summary.SummaryViewModel
import ir.jibito.app.ui.summary.TodoAction
import ir.jibito.app.ui.summary.TodoPage
import ir.jibito.app.ui.summary.TodoStory
import ir.jibito.app.ui.summary.rememberTodoStories
import ir.jibito.app.ui.summary.todoPriority
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import kotlinx.coroutines.delay

/**
 * تب «کارها»: همه‌ی کارهای لازم زیر هم، مهم‌ترها بالا.
 * «بررسی» (پیامک‌های مبهم) یکی از همین کارهاست و با لمسش صندوق بررسی باز می‌شود.
 */
@Composable
fun TodoScreen(
    pendingReview: Int,
    onOpenReview: () -> Unit,
    onOpenUncategorized: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCategory: (Long) -> Unit,
    /** برگه‌ی «مال چی بود؟» یک تراکنش (فروشگاه‌های «جیبت رو مرتب کنیم») */
    onOpenTransaction: (Long) -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as JibitoApplication
    // خلاصه‌ی همین ماه، برای بودجه‌های نزدیک سقف
    val viewModel: SummaryViewModel = viewModel(factory = SummaryViewModel.factory(app.container.budgetRepository))
    val summary by viewModel.summary.collectAsState()
    val trend by viewModel.trend.collectAsState()
    // «ثبت شد · برگردون» بعد از جواب دادن به یک کار (مثلاً «دو حساب یکی‌اند یا جدا؟»)
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(TOAST_MILLIS)
            toast = null
        }
    }
    val stories = rememberTodoStories(
        summary,
        pendingReview = pendingReview,
        onOpenReview = onOpenReview,
        onOpenUncategorized = onOpenUncategorized,
        onOpenSettings = onOpenSettings,
        onOpenCategory = onOpenCategory,
        onToast = { toast = it },
    )
    var editingBudget by remember { mutableStateOf(false) }
    // مورد لمس‌شده‌ی یک کارت چندتایی: پیامک‌هایش در برگه‌ی پایین (کلیدش، تا بعد از جواب دادن جای دیگر هم بسته بماند)
    var smsPage by remember { mutableStateOf<Pair<String, TodoPage>?>(null) }
    // «جیبت رو مرتب کنیم» روزهای اول بالای «خلاصه» است؛ بعد این‌جا
    val stepsOnSummary = rememberFirstStepsOnSummary()
    val firstSteps = rememberFirstSteps(
        summary,
        averageSpendRial = trend?.averageRial,
        onOpenUncategorized = onOpenUncategorized,
        onOpenTransactions = onOpenTransactions,
        onOpenTransaction = onOpenTransaction,
        onEditBudget = { editingBudget = true },
        onSetBudget = viewModel::setOverallBudget,
    ).takeUnless { stepsOnSummary }
    Box(Modifier.fillMaxSize()) {
        TodoList(
            stories.sortedBy { todoPriority(it.id) },
            firstSteps = firstSteps,
            onOpenSms = { story, page -> smsPage = story.label to page },
        )
        JibiToast(
            message = toast,
            onDismiss = { toast = null },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        )
    }
    smsPage?.let { (title, page) ->
        TodoSmsSheet(title = title, page = page, onDismiss = { smsPage = null })
    }
    // قدم «بودجه‌ی ماهانه بذار»: همان پنجره‌ی بودجه‌ی کل «خلاصه»
    val s = summary
    if (editingBudget && s != null) {
        BudgetDialog(
            key = "overall",
            title = stringResource(R.string.overall_dialog_title),
            hint = stringResource(R.string.overall_dialog_hint),
            initialRial = s.overallBudgetRial,
            suggestionRial = s.categoryBudgetsSumRial.takeIf { it > 0 && it != s.overallBudgetRial },
            onSave = { rial ->
                viewModel.setOverallBudget(rial)
                editingBudget = false
            },
            onDismiss = { editingBudget = false },
        )
    }
}

private const val TOAST_MILLIS = 4_000L

/**
 * فهرست کارها (بدون وابستگی به داده، برای اسکرین‌شات هم) در دو گروه:
 * «الان» (کارهای فوری که روی تب شمرده می‌شوند) و «پیشنهادها».
 * [firstSteps]: کارت «قدم‌های اول» برای کاربر تازه، بالای همه.
 */
@Composable
fun TodoList(
    items: List<TodoStory>,
    modifier: Modifier = Modifier,
    firstSteps: FirstStepsUi? = null,
    /** لمس یک مورد از کارت چندتایی که پیامک دارد */
    onOpenSms: (TodoStory, TodoPage) -> Unit = { _, _ -> },
) {
    val colors = MaterialTheme.colorScheme
    val t = JibitoTheme.colors
    val urgent = items.filter { it.urgent }
    val suggestions = items.filterNot { it.urgent }
    LazyColumn(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 16.dp + LocalBottomBarSpace.current),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "head") {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)) {
                Text(
                    stringResource(R.string.todo_title),
                    modifier = Modifier.semantics { heading() },
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.onBackground,
                )
                if (urgent.isNotEmpty()) {
                    Text(
                        Jalali.toPersianDigits(stringResource(R.string.todo_urgent_count, urgent.size)),
                        fontSize = 14.sp,
                        color = t.muted,
                    )
                }
            }
        }
        if (firstSteps != null) {
            item(key = "first-steps") { FirstStepsCard(firstSteps) }
        }
        if (items.isEmpty() && firstSteps == null) {
            item(key = "empty") { AllClear() }
        }
        if (urgent.isNotEmpty()) {
            item(key = "sec-now") { SectionLabel(stringResource(R.string.todo_section_now), t.uncatFg) }
            items(urgent, key = { it.id }) { TodoCard(it, onOpenSms) }
        }
        if (suggestions.isNotEmpty()) {
            item(key = "sec-sug") { SectionLabel(stringResource(R.string.todo_section_suggestions), t.muted) }
            items(suggestions, key = { it.id }) { TodoCard(it, onOpenSms) }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text,
        modifier = Modifier
            .padding(start = 4.dp, end = 4.dp, top = 8.dp)
            .semantics { heading() },
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

/**
 * یک کار: آیکون در مربع گرد (مثل بقیه‌ی اپ)، عنوان و یک جمله توضیح.
 * کارهای یک‌لمسی دکمه‌های خودشان را همین‌جا دارند.
 * کارت چندتایی (pages): توضیح و دکمه‌ها ورق می‌خورند؛ کاربر چپ و راست می‌کشد و هر مورد را که خواست جواب می‌دهد.
 * لمس توضیح یک مورد، پیامک‌های همان تراکنش را نشان می‌دهد (رمز دوم، کسر، واریز) تا کاربر خودش ببیند و تصمیم بگیرد.
 */
@Composable
private fun TodoCard(s: TodoStory, onOpenSms: (TodoStory, TodoPage) -> Unit) {
    val pager = if (s.pages.isNotEmpty()) rememberPagerState(pageCount = { s.pages.size }) else null
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.sheet)
            .border(1.dp, t.border, shape)
            .clickable(onClick = s.onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(s.bg),
                contentAlignment = Alignment.Center,
            ) {
                if (s.icon != null) {
                    Icon(s.icon, contentDescription = null, tint = s.fg, modifier = Modifier.size(22.dp))
                } else {
                    Text(s.text.orEmpty(), color = s.fg, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.label, modifier = Modifier.weight(1f, fill = false), fontSize = 16.sp, fontWeight = FontWeight.Black, color = colors.onBackground)
                    // «۲ از ۵» برای کارت چندتایی
                    if (pager != null && s.pages.size > 1) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Jalali.toPersianDigits(
                                stringResource(R.string.todo_page_of, (pager.currentPage + 1).coerceAtMost(s.pages.size), s.pages.size)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(s.bg)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = s.fg,
                        )
                    }
                }
                if (pager == null) {
                    s.detail?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, fontSize = 13.sp, lineHeight = 21.sp, color = t.muted)
                    }
                }
            }
            if (s.actions.isEmpty() && pager == null) {
                Spacer(Modifier.width(8.dp))
                Icon(JibitoIcons.ChevronForward, contentDescription = null, tint = t.faint, modifier = Modifier.size(20.dp))
            }
        }
        if (pager != null) {
            HorizontalPager(
                state = pager,
                key = { s.pages.getOrNull(it)?.key ?: it },
                pageSpacing = 16.dp,
                verticalAlignment = Alignment.Top,
            ) { page ->
                val p = s.pages.getOrNull(page) ?: return@HorizontalPager
                Column {
                    Spacer(Modifier.height(8.dp))
                    if (p.sms.isEmpty()) {
                        Text(p.detail, fontSize = 13.sp, lineHeight = 21.sp, color = t.muted)
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenSms(s, p) }
                        ) {
                            Text(p.detail, fontSize = 13.sp, lineHeight = 21.sp, color = t.muted)
                            Row(Modifier.padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(DesignIcons.Message, contentDescription = null, tint = colors.primary, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.todo_show_sms), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ActionButtons(p.actions)
                }
            }
            if (s.pages.size > 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.todo_page_hint),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 11.sp,
                    color = t.faint,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (s.actions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ActionButtons(s.actions)
        }
    }
}

/** دکمه‌های یک کار؛ اولی دکمه‌ی اصلی است */
@Composable
internal fun ActionButtons(actions: List<TodoAction>) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEachIndexed { i, a ->
            val primary = i == 0
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (primary) t.btnBg else t.chip)
                    .clickable(role = Role.Button, onClick = a.onClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    a.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (primary) t.btnFg else colors.onBackground,
                    maxLines = 1,
                )
            }
        }
    }
}

/** هیچ کاری نمانده */
@Composable
private fun AllClear() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mascot(130.dp, face = MascotFace.HAPPY)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.review_all_done),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.todo_all_clear),
            fontSize = 15.sp,
            lineHeight = 26.sp,
            textAlign = TextAlign.Center,
            color = JibitoTheme.colors.muted,
        )
    }
}
