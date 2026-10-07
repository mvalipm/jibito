package ir.jibito.app.ui.todo

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.ui.settings.rememberAppLock
import ir.jibito.app.ui.summary.rememberNotificationPrompt
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import kotlinx.coroutines.flow.map

/** یک قدم از «قدم‌های اول»: با انجام شدن کارش خودبه‌خود تیک می‌خورد */
data class FirstStep(
    val id: String,
    val label: String,
    val detail: String,
    val done: Boolean,
    val onClick: () -> Unit,
)

/** کارت «قدم‌های اول» بالای تب «کارها»؛ [onHide] یعنی کاربر دیگر نمی‌خواهدش */
data class FirstStepsUi(val steps: List<FirstStep>, val onHide: () -> Unit)

/**
 * راهنمای کاربر تازه، به شکل چند کار انجام‌دادنی (نه چند صفحه متن):
 * دسته دادن به یک خرج، روشن کردن نوتیف، گذاشتن بودجه و قفل اپ.
 * هر قدم از روی خود داده‌ها تیک می‌خورد. وقتی همه انجام شد (یا کاربر بستش)، برای همیشه می‌رود.
 * @return null یعنی کارت نشان داده نشود
 */
@Composable
fun rememberFirstSteps(
    s: MonthSummary?,
    onOpenUncategorized: () -> Unit,
    onOpenTransactions: () -> Unit,
    onEditBudget: () -> Unit,
): FirstStepsUi? {
    val context = LocalContext.current
    val app = context.applicationContext as JibitoApplication
    val prefs = remember { FirstStepsPrefs(app) }
    var finished by remember { mutableStateOf(prefs.finished) }
    val progressFlow = remember {
        app.container.transactionRepository.observeTransactions().map { list ->
            val expenses = list.filter { it.transaction.type == FlowType.WITHDRAWAL && !it.isSelfTransfer && !it.isFailedPurchase }
            CategoryProgress(
                userCategorized = expenses.any { it.categoryId != null && !it.isAutoCategorized },
                hasUncategorized = expenses.any { it.categoryId == null },
            )
        }
    }
    // تا داده نیامده، قدم دسته «انجام‌نشده» به نظر می‌رسد؛ پس کارت هم تا آن موقع پنهان است
    val progress by progressFlow.collectAsState(initial = null)
    val notification = rememberNotificationPrompt()
    val lock = rememberAppLock()
    if (finished) return null
    val p = progress ?: return null
    // خلاصه هنوز نیامده ← معلوم نیست بودجه گذاشته شده یا نه
    val summary = s ?: return null

    val steps = buildList {
        add(
            FirstStep(
                "cat",
                stringResource(R.string.steps_cat),
                stringResource(R.string.steps_cat_detail),
                done = p.userCategorized,
                onClick = if (p.hasUncategorized) onOpenUncategorized else onOpenTransactions,
            )
        )
        add(
            FirstStep(
                "notif",
                stringResource(R.string.steps_notif),
                stringResource(R.string.steps_notif_detail),
                done = notification.enabled,
                onClick = notification.fix,
            )
        )
        add(
            FirstStep(
                "budget",
                stringResource(R.string.steps_budget),
                stringResource(R.string.steps_budget_detail),
                done = summary.overallBudgetRial != null || summary.categories.any { it.budgetRial != null },
                onClick = onEditBudget,
            )
        )
        // گوشی بدون قفل صفحه، قفل اپ هم ندارد
        if (lock.available) {
            add(
                FirstStep(
                    "lock",
                    stringResource(R.string.steps_lock),
                    stringResource(R.string.steps_lock_detail),
                    done = lock.enabled,
                    onClick = { lock.set(true) },
                )
            )
        }
    }
    val allDone = steps.all { it.done }
    LaunchedEffect(allDone) {
        if (allDone) {
            prefs.finished = true
            finished = true
        }
    }
    if (allDone) return null
    return FirstStepsUi(steps) {
        prefs.finished = true
        finished = true
    }
}

private data class CategoryProgress(val userCategorized: Boolean, val hasUncategorized: Boolean)

/** «قدم‌های اول» تمام شده یا بسته شده؛ دیگر نشان داده نمی‌شود */
private class FirstStepsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("first_steps", Context.MODE_PRIVATE)

    var finished: Boolean
        get() = prefs.getBoolean(KEY_FINISHED, false)
        set(value) = prefs.edit { putBoolean(KEY_FINISHED, value) }

    private companion object {
        const val KEY_FINISHED = "finished"
    }
}

/** کارت «قدم‌های اول»: عنوان، «۱ از ۴»، نوار پیشرفت و قدم‌ها (انجام‌نشده‌ها لمس‌شدنی‌اند) */
@Composable
fun FirstStepsCard(ui: FirstStepsUi, modifier: Modifier = Modifier) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    val doneCount = ui.steps.count { it.done }
    val total = ui.steps.size
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.sheet)
            .border(1.dp, t.border, shape)
            .padding(top = 12.dp, bottom = 6.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.steps_title),
                modifier = Modifier.semantics { heading() },
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                Jalali.toPersianDigits(stringResource(R.string.steps_progress, doneCount, total)),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(t.tealTint)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = t.tealTintFg,
            )
            Spacer(Modifier.weight(1f))
            val hide = stringResource(R.string.steps_hide)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = ui.onHide)
                    .semantics { contentDescription = hide },
                contentAlignment = Alignment.Center,
            ) {
                Icon(JibitoIcons.Close, contentDescription = null, tint = t.faint, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        // نوار پیشرفت
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(t.chip),
        ) {
            if (doneCount > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(doneCount.toFloat() / total)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(t.teal),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // اول کارهای مانده، بعد انجام‌شده‌ها
        ui.steps.sortedBy { it.done }.forEach { StepRow(it) }
    }
}

@Composable
private fun StepRow(step: FirstStep) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val doneText = stringResource(R.string.steps_done_a11y)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (step.done) Modifier else Modifier.clickable(role = Role.Button, onClick = step.onClick))
            .semantics { if (step.done) stateDescription = doneText }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .then(
                    if (step.done) Modifier.background(t.teal)
                    else Modifier.border(2.dp, t.border, CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (step.done) Icon(JibitoIcons.Check, contentDescription = null, tint = t.onFg, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                step.label,
                fontSize = 15.sp,
                fontWeight = if (step.done) FontWeight.Medium else FontWeight.Bold,
                color = if (step.done) t.faint else colors.onBackground,
                textDecoration = if (step.done) TextDecoration.LineThrough else null,
            )
            if (!step.done) {
                Text(step.detail, fontSize = 13.sp, lineHeight = 21.sp, color = t.muted)
            }
        }
        if (!step.done) {
            Spacer(Modifier.width(8.dp))
            Icon(JibitoIcons.ChevronForward, contentDescription = null, tint = t.faint, modifier = Modifier.size(20.dp))
        }
    }
}
