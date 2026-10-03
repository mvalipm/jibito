package ir.jibito.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.theme.AppThemeStyle
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.previewColors

/** آیکون‌های صفحه‌ی تنظیمات که جای دیگری لازم نیستند (همان خانواده‌ی خطی ۲۴×۲۴) */
internal object SettingsIcons {
    val Backup by lazy { DesignIcons.svg("backup", "M12 4v10.5M7.5 10L12 14.5 16.5 10M4.5 14v5.5h15V14") }
    val Shield by lazy { DesignIcons.svg("shield", "M12 3l7 3v5c0 4.5-3 8.2-7 10-4-1.8-7-5.5-7-10V6zM9 12l2 2 4-4") }
    val Heart by lazy { DesignIcons.svg("heart", DesignIcons.HEALTH) }
    val Wrench by lazy { DesignIcons.svg("wrench", DesignIcons.WRENCH) }
    val Weekly by lazy { DesignIcons.svg("weekly", DesignIcons.CALENDAR) }
}

/** آنچه ته هر ردیف می‌آید */
internal sealed interface RowTrailing {
    /** ورود به زیرصفحه */
    data object Chevron : RowTrailing

    /** کلید روشن/خاموش؛ لمس کل ردیف هم عوضش می‌کند */
    data class Toggle(val checked: Boolean, val enabled: Boolean = true, val onChange: (Boolean) -> Unit) : RowTrailing

    /** وضعیت دسترسی (روشنه / خاموشه) */
    data class Status(val ok: Boolean) : RowTrailing
}

/**
 * یک گروه در صفحه‌ی اصلی تنظیمات: سرتیتر کوچک، و ردیف‌ها در یک کارت گرد با خط جداکننده.
 */
@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp).semantics { heading() },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = JibitoTheme.colors.muted,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(JibitoTheme.colors.sheet),
            content = content,
        )
    }
}

/** خط جداکننده‌ی بین ردیف‌های یک گروه (از زیر آیکون شروع می‌شود) */
@Composable
internal fun RowDivider() =
    HorizontalDivider(Modifier.padding(start = 66.dp, end = 16.dp), color = JibitoTheme.colors.border)

/**
 * ردیف تنظیمات: آیکون رنگی، عنوان، و زیرش «مقدار فعلی» تا کاربر بدون باز کردن هر بخش وضعیت را ببیند.
 * [attention]: مقدار با رنگ هشدار (مثلاً «۴۵ روزه پشتیبان نگرفتی»).
 */
@Composable
internal fun SettingsRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    value: String? = null,
    attention: Boolean = false,
    trailing: RowTrailing = RowTrailing.Chevron,
    onClick: (() -> Unit)? = null,
) {
    val interaction = when {
        trailing is RowTrailing.Toggle -> Modifier.toggleable(
            value = trailing.checked,
            enabled = trailing.enabled,
            role = Role.Switch,
            onValueChange = trailing.onChange,
        )
        onClick != null -> Modifier.clickable(role = Role.Button, onClick = onClick)
        else -> Modifier
    }
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .then(interaction)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (value != null) {
                Text(
                    value,
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 12.5.sp,
                    fontWeight = if (attention) FontWeight.Bold else FontWeight.Normal,
                    color = if (attention) JibitoTheme.colors.warning else JibitoTheme.colors.muted,
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        when (trailing) {
            RowTrailing.Chevron -> Icon(
                JibitoIcons.ChevronForward,
                contentDescription = null,
                tint = JibitoTheme.colors.faint,
                modifier = Modifier.size(20.dp),
            )
            is RowTrailing.Toggle -> Switch(checked = trailing.checked, onCheckedChange = null, enabled = trailing.enabled)
            is RowTrailing.Status -> StatusChip(trailing.ok)
        }
    }
}

/** کپسول وضعیت با آیکون خطی (تیک یا هشدار) */
@Composable
internal fun StatusChip(ok: Boolean) {
    val tint = if (ok) JibitoTheme.colors.income else JibitoTheme.colors.alert
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (ok) DesignIcons.Check else DesignIcons.Warn, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(4.dp))
        Text(
            stringResource(if (ok) R.string.settings_perm_ok else R.string.settings_perm_missing),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}

/**
 * سربرگ زیرصفحه‌های تنظیمات: دکمه‌ی برگشت و عنوان. محتوای صفحه زیرش می‌آید.
 */
@Composable
internal fun SettingsPageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(JibitoIcons.Back, contentDescription = stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.size(4.dp))
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** کارت گرد محتوای زیرصفحه (بدون سرتیتر؛ عنوان در سربرگ صفحه است) */
@Composable
internal fun PageCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(JibitoTheme.colors.sheet)
            .padding(18.dp),
        content = content,
    )
}

/**
 * انتخاب یکی از چند گزینه، کپسول‌های هم‌عرض کنار هم (روشن/تیره، عمق دسته‌ها).
 * ارتفاع دست‌کم ۴۸dp تا لمسش راحت باشد.
 */
@Composable
internal fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(JibitoTheme.colors.chip)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) JibitoTheme.colors.onBg else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) JibitoTheme.colors.onFg else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/**
 * یک بخش تنظیمات: کارت گرد با سرتیتر و آیکون رنگی کم‌رنگ، و محتوا زیرش.
 */
@Composable
internal fun SettingsSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // هر بخش یک کارت گرد روی زمینه‌ی کرم، مثل بقیه‌ی صفحه‌های طرح
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(JibitoTheme.colors.sheet)
            .padding(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.size(12.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

/**
 * پوسته‌ها کنار هم، چهارتا در هر ردیف: هر کدام یک «جیب» کوچک به رنگ‌های خودش؛ انتخاب‌شده حلقه‌ی رنگی و تیک دارد،
 * و توضیحش زیر ردیف‌ها می‌آید.
 */
@Composable
internal fun ThemePicker(current: AppThemeStyle, onSelect: (AppThemeStyle) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppThemeStyle.entries.chunked(THEMES_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { style ->
                    val selected = style == current
                    // گزینه‌های انتخاب‌نشده هم قاب کم‌رنگ دارند تا معلوم باشد لمس‌شدنی‌اند
                    val ring by animateColorAsState(if (selected) colors.primary else JibitoTheme.colors.border, label = "themeRing")
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .border(if (selected) 2.dp else 1.dp, ring, RoundedCornerShape(20.dp))
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(style) })
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ThemeSwatch(previewColors(style), selected)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(style.label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                            color = colors.onBackground,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
                // ردیف آخر ناقص: جای خالی، تا همه‌ی گزینه‌ها هم‌عرض بمانند
                repeat(THEMES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(
            stringResource(current.hint),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

private const val THEMES_PER_ROW = 4

/** نمونه‌ی یک پوسته: جیب کوچک به رنگ اول، دو سکه به رنگ‌های بعدی، تیک وقتی انتخاب شده */
@Composable
private fun ThemeSwatch(palette: List<Color>, selected: Boolean) {
    Box(Modifier.size(width = 56.dp, height = 52.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            // سکه‌ها پشت جیب
            drawCircle(palette.getOrElse(2) { palette.last() }, radius = w * 0.13f, center = Offset(w * 0.68f, h * 0.18f))
            drawCircle(palette.getOrElse(1) { palette.last() }, radius = w * 0.13f, center = Offset(w * 0.38f, h * 0.22f))
            val top = h * 0.3f
            val pocket = Path().apply {
                moveTo(w * 0.12f, top)
                lineTo(w * 0.88f, top)
                lineTo(w * 0.88f, h * 0.62f)
                cubicTo(w * 0.88f, h * 0.82f, w * 0.66f, h * 0.94f, w * 0.5f, h)
                cubicTo(w * 0.34f, h * 0.94f, w * 0.12f, h * 0.82f, w * 0.12f, h * 0.62f)
                close()
            }
            drawPath(pocket, palette.first())
            drawRect(palette.getOrElse(3) { palette.last() }.copy(alpha = 0.9f), topLeft = Offset(w * 0.12f, top), size = Size(w * 0.76f, h * 0.1f))
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(JibitoIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp)) }
        }
    }
}

/** بالای تنظیمات وقتی دسترسی لازم نیست: چه چیزی خاموش است و چرا مهم است، با یک دکمه که همان‌جا درستش می‌کند */
@Composable
internal fun PermissionBanner(smsOk: Boolean, notifyOk: Boolean, onFix: () -> Unit) {
    val background = JibitoTheme.colors.moodWarn
    Column(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (!smsOk) JibitoIcons.Message else JibitoIcons.Bell,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(if (!smsOk) R.string.settings_banner_sms else R.string.settings_banner_notify),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onFix,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = background),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_banner_fix), fontWeight = FontWeight.Black) }
    }
}
