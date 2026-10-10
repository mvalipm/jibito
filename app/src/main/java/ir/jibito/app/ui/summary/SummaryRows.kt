package ir.jibito.app.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.ui.common.CategoryIconTile
import ir.jibito.app.ui.common.amount
import ir.jibito.app.data.repository.CategorySpend
import ir.jibito.app.notify.BudgetLevel
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

@Composable
internal fun CategoryRow(c: CategorySpend, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = categoryTint(c.colorHex, c.icon)
    val base = tint.fg
    val budget = c.budgetRial
    // خرج یک‌باره از بودجه کم نمی‌شود
    val level = if (budget != null) BudgetLevel.of(c.budgetSpentRial, budget) else 0
    val barColor = when (level) {
        100 -> JibitoTheme.colors.alert
        80 -> JibitoTheme.colors.amber
        else -> base
    }
    val idle = c.spentRial == 0L && budget == null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconTile(tint, size = 44.dp, radius = 15.dp, iconSize = 22.dp)
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
                            level == 100 -> stringResource(R.string.summary_over_budget, amount(Money.toman(c.budgetSpentRial - budget)))
                            else -> stringResource(R.string.summary_remaining, amount(Money.toman(budget - c.budgetSpentRial)))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (level == 100) colors.error else colors.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        amount(Money.toman(c.spentRial)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = if (idle) colors.onSurfaceVariant else colors.onSurface,
                    )
                    if (budget != null) {
                        Text(
                            stringResource(R.string.summary_of_budget, amount(Money.toman(budget))),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            if (budget != null && budget > 0) {
                Spacer(Modifier.height(10.dp))
                val fraction = (c.budgetSpentRial.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
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
                    val percent = (c.budgetSpentRial * 100 / budget).coerceAtMost(999)
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

@Composable
internal fun IncomeRow(colorHex: String?, icon: String?, name: String, amountRial: Long) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconTile(categoryTint(colorHex, icon), size = 44.dp, radius = 15.dp, iconSize = 22.dp)
        Spacer(Modifier.size(12.dp))
        Text(
            name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        Text(
            amount("+ " + Money.toman(amountRial)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = JibitoTheme.colors.income,
        )
    }
}
