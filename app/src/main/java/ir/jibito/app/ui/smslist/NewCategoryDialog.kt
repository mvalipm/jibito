package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.data.category.CreateCategoryResult
import ir.jibito.app.data.category.CustomCategories
import ir.jibito.app.domain.Category

/** کجا دسته‌ی شخصی ساخته شود */
internal data class CreateTarget(
    val parentId: Long?,
    val prefill: String = "",
    /** از جست‌وجو آمده ← کاربر جایش را انتخاب می‌کند */
    val chooseParent: Boolean = false,
)

/**
 * ساختن دسته‌ی شخصی: اسم، (از جست‌وجو: جایش)، و برای دسته‌ی اصلی یک آیکون.
 * بعد از ساختن، همان لحظه برای تراکنش انتخاب می‌شود.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewCategoryDialog(
    target: CreateTarget,
    roots: List<Category>,
    byId: Map<Long, Category>,
    onConfirm: (String, Long?, String?, (CreateCategoryResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(target.prefill) }
    var parentId by remember { mutableStateOf(target.parentId) }
    var icon by remember { mutableStateOf(CustomCategories.ICONS.first()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val errEmpty = stringResource(R.string.custom_error_empty)
    val errLong = stringResource(R.string.custom_error_long)
    val errDup = stringResource(R.string.custom_error_duplicate)
    val parentName = parentId?.let { byId[it]?.name }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (parentName == null) stringResource(R.string.custom_title_root)
                else stringResource(R.string.custom_title_sub, parentName),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        singleLine = true,
                        label = { Text(stringResource(R.string.custom_name)) },
                        isError = error != null,
                        supportingText = { error?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (target.chooseParent && roots.isNotEmpty()) {
                        Text(
                            stringResource(R.string.custom_where),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CategoryChip(
                                label = stringResource(R.string.custom_as_root),
                                selected = parentId == null,
                                onClick = { parentId = null },
                            )
                            roots.forEach { r ->
                                CategoryChip(
                                    label = listOfNotNull(r.icon, r.name).joinToString(" "),
                                    selected = parentId == r.id,
                                    onClick = { parentId = r.id },
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (parentId == null) {
                        Text(
                            stringResource(R.string.custom_icon),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CustomCategories.ICONS.forEach { e ->
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (e == icon) colors.primary.copy(alpha = 0.18f) else colors.surfaceVariant)
                                        .clickable { icon = e },
                                    contentAlignment = Alignment.Center,
                                ) { Text(e) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    onConfirm(name, parentId, if (parentId == null) icon else null) { result ->
                        saving = false
                        if (result is CreateCategoryResult.Invalid) {
                            error = when (result.reason) {
                                CreateCategoryResult.Reason.EMPTY -> errEmpty
                                CreateCategoryResult.Reason.TOO_LONG -> errLong
                                CreateCategoryResult.Reason.DUPLICATE -> errDup
                            }
                        }
                    }
                },
            ) { Text(stringResource(R.string.custom_save), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.budget_dialog_cancel)) } },
    )
}
