package ir.jibito.app.ui.summary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.CategoryStyle
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.ui.common.CategoryIconTile
import ir.jibito.app.ui.common.amount
import ir.jibito.app.data.repository.CategorySpend
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.notify.BudgetLevel
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/**
 * رنگ‌های نمودار در حالت روشن/تیره: همان رنگ پررنگ دسته در طرح «جیبی» (CategoryStyle)،
 * تا نوار سهم‌ها، کاشی‌ها و نمودارها همه یک رنگ باشند.
 */
object ChartColors {
    fun forCategory(hex: String?, dark: Boolean): Color = CategoryStyle.color(hex, dark)

    fun neutral(dark: Boolean): Color = CategoryStyle.neutral(dark)
}

private fun percentOf(amount: Long, total: Long): String {
    if (total <= 0) return "۰٪"
    val p = amount * 100.0 / total
    return if (p > 0 && p < 1) "<۱٪" else "${p.toInt()}٪"
}

/**
 * جزئیات یک دسته‌ی اصلی در این ماه: خرج، بودجه و باقی‌مانده، و اینکه داخل خودش کجا خرج شده (زیردسته‌ها).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailSheet(
    c: CategorySpend,
    monthTitle: String,
    onEditBudget: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = JibitoTheme.colors.dark
    val base = ChartColors.forCategory(c.colorHex, dark)
    val budget = c.budgetRial
    val level = if (budget != null) BudgetLevel.of(c.spentRial, budget) else 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = JibitoTheme.colors.sheet,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryIconTile(categoryTint(c.colorHex, c.icon), size = 52.dp, radius = 18.dp, iconSize = 26.dp)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = colors.onSurface)
                        Text(
                            stringResource(R.string.summary_spent, monthTitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Text(amount(Money.toman(c.spentRial)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = colors.onSurface)
                }

                // بودجه
                Spacer(Modifier.height(18.dp))
                if (budget != null && budget > 0) {
                    val fraction = (c.spentRial.toFloat() / budget).coerceIn(0f, 1f)
                    val barColor = when (level) {
                        100 -> JibitoTheme.colors.alert
                        80 -> JibitoTheme.colors.amber
                        else -> base
                    }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = barColor,
                        trackColor = barColor.copy(alpha = 0.15f),
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (level == 100) stringResource(R.string.summary_over_budget, Money.toman(c.spentRial - budget))
                        else stringResource(R.string.overall_remaining, Money.toman(budget - c.spentRial), Money.toman(budget)),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (level == 100) colors.error else colors.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onEditBudget, shape = RoundedCornerShape(14.dp)) {
                        Text(stringResource(R.string.detail_edit_budget))
                    }
                } else {
                    Button(onClick = onEditBudget, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.detail_set_budget), fontWeight = FontWeight.Bold)
                    }
                }

                // زیردسته‌ها
                Spacer(Modifier.height(22.dp))
                Text(
                    stringResource(R.string.detail_inside_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                if (c.children.isEmpty()) {
                    Text(
                        stringResource(R.string.detail_inside_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                val max = c.children.maxOfOrNull { it.spentRial } ?: 0L
                c.children.forEach { sub ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                sub.name ?: stringResource(R.string.detail_direct),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurface,
                            )
                            Text(
                                Jalali.toPersianDigits(percentOf(sub.spentRial, c.spentRial)),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                Money.toman(sub.spentRial),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        // طول نوار نسبت به بزرگ‌ترین زیردسته؛ همه از یک خط پایه (راست) شروع می‌شوند
                        Box(Modifier.fillMaxWidth().height(6.dp)) {
                            Box(
                                Modifier
                                    .fillMaxWidth(if (max > 0) (sub.spentRial.toFloat() / max).coerceIn(0.02f, 1f) else 0f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(base),
                            )
                        }
                    }
                }
            }
        }
    }
}
