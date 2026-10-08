package ir.jibito.app.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.category.Nature
import ir.jibito.app.data.category.SpendNature
import ir.jibito.app.domain.Category
import ir.jibito.app.ui.theme.JibitoTheme

/**
 * «ماهیت این دسته چیه؟»: اجباری، ضروری، دلخواه، و (اگر پیش‌فرضی دارد) «پیش‌فرض». با یک لمس ذخیره می‌شود.
 * @param onPick Nature.code، یا SpendNature.DEFAULT برای برگشت به پیش‌فرض
 */
@Composable
internal fun NaturePickerDialog(cat: Category, byId: Map<Long, Category>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val current = Nature.of(cat.nature)
    val default = SpendNature.defaultOf(cat, byId)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nature_pick_title, cat.name), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Nature.entries.forEach { n ->
                    NatureOption(natureName(n), natureHint(n), natureColor(n), selected = current == n) { onPick(n.code) }
                }
                if (default != null) {
                    val label = if (cat.code != null) {
                        stringResource(R.string.nature_pick_default, natureName(default))
                    } else {
                        stringResource(R.string.nature_pick_parent) + " (" + natureName(default) + ")"
                    }
                    NatureOption(label, null, natureColor(default).copy(alpha = 0.5f), selected = current == null) { onPick(SpendNature.DEFAULT) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.budget_dialog_cancel)) } },
    )
}

@Composable
internal fun NatureOption(label: String, hint: String?, color: Color, selected: Boolean, onClick: () -> Unit) {
    val t = JibitoTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) t.chip else Color.Transparent)
            .border(1.dp, if (selected) color else t.border, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            if (hint != null) Text(hint, fontSize = 11.sp, lineHeight = 17.sp, color = t.muted)
        }
    }
}
