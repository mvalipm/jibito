package ir.jibito.app.ui.smslist

import androidx.compose.ui.semantics.Role
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.ui.theme.DesignIcons
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import ir.jibito.app.ui.theme.JibitoTheme
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import ir.jibito.app.domain.Category
import androidx.compose.material3.Icon

/** «انتقال بین حساب‌های خودم»: یک ردیف با کلید روشن/خاموش (نه خرج حساب می‌شود نه درآمد) */
@Composable
internal fun SelfTransferRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    val jt = JibitoTheme.colors
    SwitchRow(
        icon = DesignIcons.Transfer,
        title = stringResource(R.string.sheet_self_transfer),
        subtitle = stringResource(R.string.sheet_self_transfer_short),
        fg = jt.transferFg,
        bg = jt.transferBg,
        checked = checked,
        onChange = onChange,
    )
}

/** «خرج یک‌باره»: خرید بزرگ و نامعمول (خانه، ماشین…) که الگوی خرج ماه‌ها را به هم نزند */
@Composable
internal fun OneOffRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    val jt = JibitoTheme.colors
    SwitchRow(
        icon = DesignIcons.Star,
        title = stringResource(R.string.sheet_one_off),
        subtitle = stringResource(R.string.sheet_one_off_short),
        fg = jt.sugFg,
        bg = jt.sugBg,
        checked = checked,
        onChange = onChange,
    )
}

/** ردیفِ آیکون + عنوان + توضیح + کلید روشن/خاموش (حاشیه‌ی رنگی وقتی روشن است) */
@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    fg: Color,
    bg: Color,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val jt = JibitoTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, if (checked) fg else jt.border, shape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).background(bg, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text(subtitle, fontSize = 11.sp, color = jt.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = fg),
        )
    }
}

/** زیردسته‌های دسته‌ی باز (و جزئیاتِ زیردسته‌ی باز) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubPanel(
    root: Category,
    subs: List<Category>,
    childrenOf: Map<Long?, List<Category>>,
    selectedId: Long?,
    openSubId: Long?,
    onOpenSub: (Long) -> Unit,
    onPick: (Long?) -> Unit,
    onAdd: (parentId: Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(colors.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(10.dp)
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip(
                label = stringResource(R.string.sheet_all_of, root.name),
                selected = root.id == selectedId,
                onClick = { onPick(root.id) },
            )
            subs.forEach { sub ->
                val details = childrenOf[sub.id].orEmpty()
                val isOpen = openSubId == sub.id
                CategoryChip(
                    label = sub.name + if (details.isNotEmpty()) (if (isOpen) "  ⌄" else "  ›") else "",
                    selected = sub.id == selectedId || details.any { it.id == selectedId },
                    onClick = { if (details.isEmpty()) onPick(sub.id) else onOpenSub(sub.id) },
                )
            }
            AddChip("＋") { onAdd(root.id) }
        }
        subs.firstOrNull { it.id == openSubId }?.let { open ->
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryChip(
                    label = stringResource(R.string.sheet_all_of, open.name),
                    selected = open.id == selectedId,
                    onClick = { onPick(open.id) },
                )
                childrenOf[open.id].orEmpty().forEach { d ->
                    CategoryChip(label = d.name, selected = d.id == selectedId, onClick = { onPick(d.id) })
                }
                AddChip("＋") { onAdd(open.id) }
            }
        }
        if (!root.countsAsSpend) {
            Text(
                stringResource(R.string.sheet_not_spend_hint),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
internal fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected || highlighted) FontWeight.Bold else FontWeight.Normal) },
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(17.dp),
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = if (highlighted) JibitoTheme.colors.sugBg else JibitoTheme.colors.chip,
            labelColor = if (highlighted) JibitoTheme.colors.sugFg else colors.onSurface,
            iconColor = colors.primary,
            selectedContainerColor = colors.primary,
            selectedLabelColor = colors.onPrimary,
            selectedLeadingIconColor = colors.onPrimary,
        ),
    )
}

/** برای جست‌وجو: ی/ک عربی، نیم‌فاصله و فاصله یکسان می‌شوند */
internal fun String.normalizedForSearch(): String =
    trim().replace('ي', 'ی').replace('ك', 'ک').replace("‌", "").replace(" ", "").lowercase()

internal fun String?.toColorOrNull(): Color? = try {
    this?.let { Color(it.toColorInt()) }
} catch (e: IllegalArgumentException) {
    null
}

@Composable
internal fun AddChip(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = colors.primary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
}
