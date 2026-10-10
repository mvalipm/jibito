package ir.jibito.app.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
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
import ir.jibito.app.ui.summary.TodoPage
import ir.jibito.app.ui.summary.TodoStory
import ir.jibito.app.ui.summary.rememberTodoStories
import ir.jibito.app.ui.summary.todoPriority
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
                } else if (items.isNotEmpty()) {
                    Text(stringResource(R.string.todo_no_urgent), fontSize = 14.sp, color = t.muted)
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
        // «خرج بی‌دسته» هیچ‌وقت تمام نمی‌شود؛ اگر فقط همین مانده، به‌جای فضای خالی بگو بقیه تمام است
        if (firstSteps == null && items.isNotEmpty() && items.all { it.id == "uncat" }) {
            item(key = "rest-done") { RestDone() }
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

/** فقط کارِ همیشگیِ «خرج بی‌دسته» مانده */
@Composable
private fun RestDone() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mascot(76.dp, face = MascotFace.HAPPY)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.todo_rest_done), fontSize = 14.sp, color = JibitoTheme.colors.muted)
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
