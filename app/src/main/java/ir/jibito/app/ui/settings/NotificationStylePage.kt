package ir.jibito.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.notify.NotificationStyle
import ir.jibito.app.ui.theme.JibitoTheme

/** «نوتیف مال چی بود؟»: دکمه‌ها / دکمه + بنویس / فقط بنویس، هر کدام با پیش‌نمایش دکمه‌های نوتیف */
@Composable
internal fun NotificationStylePageContent() {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val settings = app.container.notificationStyle
    val current by settings.style.collectAsState()

    PageCard {
        Text(
            stringResource(R.string.settings_notif_style_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NotificationStyle.entries.forEach { style ->
                StyleOption(style, selected = style == current, onSelect = { settings.set(style) })
            }
        }
        Text(
            stringResource(R.string.settings_notif_style_later),
            modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
            fontSize = 12.sp,
            color = JibitoTheme.colors.muted,
        )
    }
}

@Composable
private fun StyleOption(style: NotificationStyle, selected: Boolean, onSelect: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) primary else JibitoTheme.colors.border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioDot(selected)
            Spacer(Modifier.size(10.dp))
            Text(
                stringResource(style.label),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            stringResource(style.hint),
            modifier = Modifier.padding(top = 4.dp, start = 30.dp),
            fontSize = 12.sp,
            color = JibitoTheme.colors.muted,
        )
        Spacer(Modifier.height(10.dp))
        // پیش‌نمایش ردیف دکمه‌های نوتیف
        val samples = listOf(R.string.notif_style_sample_1, R.string.notif_style_sample_2, R.string.notif_style_sample_3)
        val actions = samples.take(style.categoryButtons).map { stringResource(it) } +
            listOfNotNull(if (style.hasReply) stringResource(R.string.notif_reply) else null)
        Row(
            Modifier
                .padding(start = 30.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(JibitoTheme.colors.chip)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            actions.forEach {
                Text(it, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(20.dp)
            .border(2.dp, if (selected) primary else JibitoTheme.colors.faint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(if (selected) primary else Color.Transparent, CircleShape)
        )
    }
}
