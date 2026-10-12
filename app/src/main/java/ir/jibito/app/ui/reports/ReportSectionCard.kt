package ir.jibito.app.ui.reports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme

/** مدت باز/بسته شدن کارت‌ها */
internal const val EXPAND_MILLIS = 250

/**
 * دکمه‌ی یک گزارش: آیکون، عنوان و (وقتی بسته است) خلاصه‌ی یک‌خطی؛ لمسش کارت زیرش را باز یا بسته می‌کند.
 * @param isNew نقطه‌ی رنگی کنار عنوان: چیز تازه‌ای هست که هنوز دیده نشده
 * @param locked قفل کوچک کنار عنوان: این گزارش در «جیبیتوی کامل» است (ماه رایگان تمام شده)
 */
@Composable
internal fun SectionCard(
    title: String,
    icon: String,
    iconBg: Color,
    iconFg: Color,
    summary: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    isNew: Boolean = false,
    locked: Boolean = false,
    content: @Composable () -> Unit,
) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val chevron = remember { DesignIcons.svg("chevron", DesignIcons.CHEVRON_DOWN) }
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(EXPAND_MILLIS), label = "chevron")
    val state = stringResource(if (expanded) R.string.state_section_open else R.string.state_section_closed)
    val newLabel = stringResource(R.string.cd_reports_new)
    val lockedLabel = stringResource(R.string.cd_pro_locked)
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, t.border, shape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(
                    onClickLabel = stringResource(if (expanded) R.string.cd_section_close else R.string.cd_section_open),
                    role = Role.Button,
                    onClick = onToggle,
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .clearAndSetSemantics {
                    contentDescription = listOfNotNull(
                        title,
                        if (locked) lockedLabel else null,
                        if (isNew && !expanded) newLabel else null,
                        if (expanded) null else summary,
                    ).joinToString("، ")
                    stateDescription = state
                    heading()
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon, iconBg, iconFg, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1)
                    if (locked) {
                        Spacer(Modifier.width(6.dp))
                        Icon(JibitoIcons.Lock, contentDescription = null, tint = t.amber, modifier = Modifier.size(15.dp))
                    }
                    if (isNew && !expanded) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(t.amber))
                    }
                }
                AnimatedVisibility(visible = !expanded && summary != null) {
                    Text(
                        summary.orEmpty(),
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = t.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                chevron,
                contentDescription = null,
                tint = t.muted,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp)
                    .rotate(turn),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(EXPAND_MILLIS)) + fadeIn(tween(EXPAND_MILLIS)),
            exit = shrinkVertically(tween(EXPAND_MILLIS)) + fadeOut(tween(EXPAND_MILLIS)),
        ) {
            Column { content() }
        }
    }
}
