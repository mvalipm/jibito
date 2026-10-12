package ir.jibito.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.ui.theme.AppThemeStyle
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.previewColors

/**
 * پوسته‌ها کنار هم، چهارتا در هر ردیف: هر کدام یک «جیب» کوچک به رنگ‌های خودش؛ انتخاب‌شده حلقه‌ی رنگی و تیک دارد،
 * و توضیحش زیر ردیف‌ها می‌آید.
 * @param proLocked ماه رایگان تمام شده: پوسته‌های «جیبیتوی کامل» قفل کوچک دارند و لمسشان [onLocked] است
 */
@Composable
internal fun ThemePicker(
    current: AppThemeStyle,
    onSelect: (AppThemeStyle) -> Unit,
    proLocked: Boolean = false,
    onLocked: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppThemeStyle.entries.chunked(THEMES_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (row.size < THEMES_PER_ROW) Spacer(Modifier.weight((THEMES_PER_ROW - row.size) / 2f))
                row.forEach { style ->
                    val selected = style == current
                    val locked = proLocked && style.pro
                    // گزینه‌های انتخاب‌نشده هم قاب کم‌رنگ دارند تا معلوم باشد لمس‌شدنی‌اند
                    val ring by animateColorAsState(if (selected) colors.primary else JibitoTheme.colors.border, label = "themeRing")
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .border(if (selected) 2.dp else 1.dp, ring, RoundedCornerShape(20.dp))
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { if (locked) onLocked() else onSelect(style) })
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ThemeSwatch(previewColors(style), selected, locked)
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
                // ردیف آخر ناقص: جای خالی دو طرف، تا گزینه‌ها هم‌عرض و وسط بمانند (نه یک حفره در گوشه)
                val missing = THEMES_PER_ROW - row.size
                if (missing > 0) Spacer(Modifier.weight(missing / 2f))
            }
        }
        // اسم و توضیح پوسته‌ی انتخاب‌شده، درست زیر شبکه
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(JibitoTheme.colors.chip)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                stringResource(current.label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
            )
            Text(
                stringResource(current.hint),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private const val THEMES_PER_ROW = 4

/** نمونه‌ی یک پوسته: جیب کوچک به رنگ اول، دو سکه به رنگ‌های بعدی، تیک وقتی انتخاب شده و قفل وقتی بسته است */
@Composable
private fun ThemeSwatch(palette: List<Color>, selected: Boolean, locked: Boolean) {
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
        } else if (locked) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .background(JibitoTheme.colors.chip, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    JibitoIcons.Lock,
                    contentDescription = stringResource(R.string.cd_pro_locked),
                    tint = JibitoTheme.colors.amber,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
