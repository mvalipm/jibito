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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.ui.common.Mascot
import ir.jibito.app.ui.common.MascotFace
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.ui.summary.SummaryViewModel
import ir.jibito.app.ui.summary.TodoStory
import ir.jibito.app.ui.summary.rememberTodoStories
import ir.jibito.app.ui.summary.todoPriority
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali

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
) {
    val app = LocalContext.current.applicationContext as JibitoApplication
    // خلاصه‌ی همین ماه، برای بودجه‌های نزدیک سقف
    val viewModel: SummaryViewModel = viewModel(factory = SummaryViewModel.factory(app.container.budgetRepository))
    val summary by viewModel.summary.collectAsState()
    val stories = rememberTodoStories(
        summary,
        pendingReview = pendingReview,
        onOpenReview = onOpenReview,
        onOpenUncategorized = onOpenUncategorized,
        onOpenTransactions = onOpenTransactions,
        onOpenSettings = onOpenSettings,
        onOpenCategory = onOpenCategory,
    )
    TodoList(stories.sortedBy { todoPriority(it.id) })
}

/** فهرست کارها (بدون وابستگی به داده، برای اسکرین‌شات هم) */
@Composable
fun TodoList(items: List<TodoStory>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val t = JibitoTheme.colors
    LazyColumn(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 16.dp + LocalBottomBarSpace.current),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "head") {
            Row(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(R.string.todo_title),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.onBackground,
                )
                if (items.isNotEmpty()) {
                    Text(
                        Jalali.toPersianDigits(stringResource(R.string.todo_count, items.size)),
                        modifier = Modifier.padding(bottom = 6.dp),
                        fontSize = 14.sp,
                        color = t.muted,
                    )
                }
            }
        }
        if (items.isEmpty()) {
            item(key = "empty") { AllClear() }
        }
        items(items, key = { it.id }) { TodoCard(it) }
    }
}

/** یک کار: دایره‌ی رنگی (همان استوریِ «خلاصه»)، عنوان و یک جمله توضیح */
@Composable
private fun TodoCard(s: TodoStory) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.sheet)
            .border(1.dp, t.border, shape)
            .clickable(onClick = s.onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .border(2.5.dp, s.ring, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
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
            Text(s.label, fontSize = 16.sp, fontWeight = FontWeight.Black, color = colors.onBackground)
            s.detail?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, fontSize = 13.sp, lineHeight = 21.sp, color = t.muted)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(JibitoIcons.ChevronForward, contentDescription = null, tint = t.faint, modifier = Modifier.size(20.dp))
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
        Mascot(130.dp, MascotFace.HAPPY)
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
