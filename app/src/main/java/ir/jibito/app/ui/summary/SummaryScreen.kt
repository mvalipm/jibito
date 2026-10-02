package ir.jibito.app.ui.summary

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.data.repository.CategorySpend
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.notify.BudgetLevel
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.JalaliMonth
import ir.jibito.app.util.Money

private val WarningAmber = Color(0xFFF2A541)

@Composable
fun SummaryScreen() {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val viewModel: SummaryViewModel = viewModel(
        factory = SummaryViewModel.factory(app.container.budgetRepository)
    )
    val month by viewModel.month.collectAsState()
    val summary by viewModel.summary.collectAsState()
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingOverall by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
    ) {
        MonthSwitcher(
            month = month,
            canGoNext = month != JalaliMonth.current(),
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
        )

        val s = summary
        if (s == null || s.month != month) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp + LocalBottomBarSpace.current),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { HeroCard(s, onEditBudget = { editingOverall = true }) }
                item {
                    Column(Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp)) {
                        Text(
                            stringResource(R.string.summary_categories),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = colors.onBackground,
                        )
                        Text(
                            stringResource(R.string.summary_tap_to_budget),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                items(s.categories, key = { it.categoryId }) { c ->
                    CategoryRow(c, onClick = { editing = c.categoryId })
                }

                // درآمدها (بدون بودجه)
                if (s.incomeCategories.isNotEmpty() || s.uncategorizedIncomeRial > 0) {
                    item {
                        Text(
                            stringResource(R.string.summary_income_categories),
                            modifier = Modifier.padding(top = 18.dp, start = 4.dp, end = 4.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = colors.onBackground,
                        )
                    }
                    items(s.incomeCategories, key = { "in-" + it.categoryId }) { c -> IncomeRow(c.icon, c.name, c.spentRial) }
                    if (s.uncategorizedIncomeRial > 0) {
                        item { IncomeRow("•", stringResource(R.string.summary_uncategorized), s.uncategorizedIncomeRial) }
                    }
                }
            }

            s.categories.firstOrNull { it.categoryId == editing }?.let { c ->
                BudgetDialog(
                    key = "cat-${c.categoryId}",
                    title = stringResource(R.string.budget_dialog_title, listOfNotNull(c.icon, c.name).joinToString(" ")),
                    hint = stringResource(R.string.budget_dialog_hint),
                    initialRial = c.budgetRial,
                    suggestionRial = null,
                    onSave = { rial ->
                        viewModel.setBudget(c.categoryId, rial)
                        editing = null
                    },
                    onDismiss = { editing = null },
                )
            }
            if (editingOverall) {
                BudgetDialog(
                    key = "overall",
                    title = stringResource(R.string.overall_dialog_title),
                    hint = stringResource(R.string.overall_dialog_hint),
                    initialRial = s.overallBudgetRial,
                    suggestionRial = s.categoryBudgetsSumRial.takeIf { it > 0 && it != s.overallBudgetRial },
                    onSave = { rial ->
                        viewModel.setOverallBudget(rial)
                        editingOverall = false
                    },
                    onDismiss = { editingOverall = false },
                )
            }
        }
    }
}

@Composable
private fun MonthSwitcher(month: JalaliMonth, canGoNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // در چیدمان راست‌به‌چپ این آیکون‌ها خودشان آینه می‌شوند: «ماه قبل» سمت راست، «ماه بعد» سمت چپ
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.summary_prev_month))
        }
        Text(
            text = month.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.summary_next_month))
        }
    }
}

/**
 * کارت بالای داشبورد: کل خرج ماه، و اگر بودجه‌ی کل تعیین شده:
 * نوار مصرف، «چقدر مونده» و «روزی چقدر تا آخر ماه». با زدن روی کارت، بودجه‌ی کل عوض می‌شود.
 */
@Composable
private fun HeroCard(s: MonthSummary, onEditBudget: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(colors.primary, Color(0xFFF08A4B))))
            .clickable(onClick = onEditBudget)
            .padding(22.dp)
    ) {
        Column {
            Text(
                stringResource(R.string.summary_spent, s.month.title),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Money.tomanNumber(s.totalSpentRial),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(R.string.unit_toman),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            OverallBudgetPart(s, onEditBudget)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroPill(stringResource(R.string.summary_income), Money.toman(s.totalIncomeRial))
                if (s.uncategorizedRial > 0) {
                    HeroPill(stringResource(R.string.summary_uncategorized), Money.toman(s.uncategorizedRial))
                }
                if (s.excludedRial > 0) {
                    HeroPill(stringResource(R.string.summary_excluded), Money.toman(s.excludedRial))
                }
            }
        }
    }
}

@Composable
private fun OverallBudgetPart(s: MonthSummary, onEditBudget: () -> Unit) {
    val budget = s.overallBudgetRial
    if (budget == null || budget <= 0) {
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.overall_set_budget),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.22f))
                .clickable(onClick = onEditBudget)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        return
    }
    val level = BudgetLevel.of(s.totalSpentRial, budget)
    val fraction = (s.totalSpentRial.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
    val barColor = when (level) {
        100 -> Color(0xFFFFE1E1)
        80 -> Color(0xFFFFE08A)
        else -> Color.White
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .weight(1f)
                .height(10.dp),
            color = barColor,
            trackColor = Color.White.copy(alpha = 0.25f),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.size(10.dp))
        Text(
            Jalali.toPersianDigits("${(s.totalSpentRial * 100 / budget).coerceAtMost(999)}٪"),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
        )
    }
    Spacer(Modifier.height(8.dp))
    val remaining = budget - s.totalSpentRial
    Text(
        if (remaining >= 0) {
            stringResource(R.string.overall_remaining, Money.toman(remaining), Money.toman(budget))
        } else {
            stringResource(R.string.overall_over, Money.toman(-remaining), Money.toman(budget))
        },
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    s.dailyAllowanceRial()?.let { daily ->
        Text(
            stringResource(R.string.overall_daily, Money.toman(daily)),
            color = Color.White.copy(alpha = 0.88f),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun HeroPill(label: String, value: String) {
    Column(
        Modifier
            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryRow(c: CategorySpend, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val base = c.colorHex.toColorOrNull() ?: colors.primary
    val budget = c.budgetRial
    val level = if (budget != null) BudgetLevel.of(c.spentRial, budget) else 0
    val barColor = when (level) {
        100 -> colors.error
        80 -> WarningAmber
        else -> base
    }
    val idle = c.spentRial == 0L && budget == null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (idle) 0.dp else 2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(base.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(c.icon ?: "•", fontSize = 18.sp)
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        c.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (idle) colors.onSurfaceVariant else colors.onSurface,
                    )
                    Text(
                        text = when {
                            budget == null -> stringResource(R.string.summary_no_budget)
                            level == 100 -> stringResource(R.string.summary_over_budget, Money.toman(c.spentRial - budget))
                            else -> stringResource(R.string.summary_remaining, Money.toman(budget - c.spentRial))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (level == 100) colors.error else colors.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Money.toman(c.spentRial),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = if (idle) colors.onSurfaceVariant else colors.onSurface,
                    )
                    if (budget != null) {
                        Text(
                            stringResource(R.string.summary_of_budget, Money.toman(budget)),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            if (budget != null && budget > 0) {
                Spacer(Modifier.height(10.dp))
                val fraction = (c.spentRial.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp),
                        color = barColor,
                        trackColor = barColor.copy(alpha = 0.15f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                    Spacer(Modifier.size(10.dp))
                    val percent = (c.spentRial * 100 / budget).coerceAtMost(999)
                    Text(
                        Jalali.toPersianDigits("$percent٪"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = barColor,
                    )
                }
            }
        }
    }
}

private val IncomeGreen = Color(0xFF1E9E6A)

@Composable
private fun IncomeRow(icon: String?, name: String, amountRial: Long) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(IncomeGreen.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(icon ?: "•", fontSize = 18.sp) }
        Spacer(Modifier.size(12.dp))
        Text(
            name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        Text(
            "+ " + Money.toman(amountRial),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = IncomeGreen,
        )
    }
}

/** پنجره‌ی تعیین بودجه (یک دسته، یا کل ماه). مبلغ به تومان وارد و به ریال ذخیره می‌شود. */
@Composable
private fun BudgetDialog(
    key: String,
    title: String,
    hint: String,
    initialRial: Long?,
    /** پیشنهاد (مثلاً جمع بودجه‌ی دسته‌ها)؛ null یعنی نشان نده */
    suggestionRial: Long?,
    onSave: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = initialRial?.let { (it / 10).toString() }.orEmpty()
    var text by rememberSaveable(key) { mutableStateOf(initial) }
    val toman = text.toLatinDigits().filter { it in '0'..'9' }.take(12).toLongOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text(stringResource(R.string.unit_toman)) },
                    supportingText = {
                        toman?.let { Text(Money.toman(it * 10)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                suggestionRial?.let { sum ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.overall_dialog_sum, Money.toman(sum)),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { text = (sum / 10).toString() }) {
                            Text(stringResource(R.string.overall_dialog_use_sum))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(toman?.let { it * 10 }) },
                enabled = toman != null && toman > 0,
            ) { Text(stringResource(R.string.budget_dialog_save)) }
        },
        dismissButton = {
            Row {
                if (initialRial != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text(stringResource(R.string.budget_dialog_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.budget_dialog_cancel)) }
            }
        },
    )
}

private fun String.toLatinDigits(): String = buildString {
    for (ch in this@toLatinDigits) {
        append(
            when (ch) {
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                else -> ch
            }
        )
    }
}

private fun String?.toColorOrNull(): Color? = try {
    this?.let { Color(android.graphics.Color.parseColor(it)) }
} catch (e: IllegalArgumentException) {
    null
}
