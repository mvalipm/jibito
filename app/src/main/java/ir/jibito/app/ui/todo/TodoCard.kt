package ir.jibito.app.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.summary.TodoAction
import ir.jibito.app.ui.summary.TodoPage
import ir.jibito.app.ui.summary.TodoProgress
import ir.jibito.app.ui.summary.TodoStory
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali

/**
 * یک کار: آیکون در مربع گرد (مثل بقیه‌ی اپ)، عنوان و یک جمله توضیح.
 * کارهای یک‌لمسی دکمه‌های خودشان را همین‌جا دارند. اگر کار پیشرفت دارد، نوارش زیر عنوان است.
 * کارت چندتایی (pages): توضیح و دکمه‌ها ورق می‌خورند؛ کاربر چپ و راست می‌کشد و هر مورد را که خواست جواب می‌دهد (TodoPager).
 * لمس توضیح یک مورد، پیامک‌های همان تراکنش را نشان می‌دهد (رمز دوم، کسر، واریز) تا کاربر خودش ببیند و تصمیم بگیرد.
 * بدنه‌ی کارت فقط وقتی لمس‌شدنی است که کار onClick داشته باشد.
 */
@Composable
internal fun TodoCard(s: TodoStory, onOpenSms: (TodoStory, TodoPage) -> Unit) {
    val pager = if (s.pages.isNotEmpty()) rememberPagerState(pageCount = { s.pages.size }) else null
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.sheet)
            .border(1.dp, t.border, shape)
            .then(s.onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(s.bg),
                contentAlignment = Alignment.Center,
            ) {
                if (s.icon != null) {
                    Icon(s.icon, contentDescription = null, tint = s.fg, modifier = Modifier.size(22.dp))
                } else {
                    Text(s.text.orEmpty(), color = s.fg, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.label, modifier = Modifier.weight(1f, fill = false), fontSize = 16.sp, fontWeight = FontWeight.Black, color = colors.onBackground)
                    // «۲ از ۵» برای کارت چندتایی
                    if (pager != null && s.pages.size > 1) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Jalali.toPersianDigits(
                                stringResource(R.string.todo_page_of, (pager.currentPage + 1).coerceAtMost(s.pages.size), s.pages.size)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(s.bg)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = s.fg,
                        )
                    }
                }
                if (pager == null) {
                    s.detail?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, fontSize = 13.sp, lineHeight = 21.sp, color = t.muted)
                    }
                }
            }
            if (s.onClick != null && s.actions.isEmpty() && pager == null) {
                Spacer(Modifier.width(8.dp))
                Icon(JibitoIcons.ChevronForward, contentDescription = null, tint = t.faint, modifier = Modifier.size(20.dp))
            }
        }
        s.progress?.let { ProgressLine(it, s.fg) }
        if (pager != null) {
            TodoPager(s, pager, onOpenSms)
        } else if (s.actions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ActionButtons(s.actions)
        }
    }
}

/** نوار باریک پیشرفت (از طرف شروع پر می‌شود، پس در راست‌به‌چپ از راست) و متنش */
@Composable
private fun ProgressLine(progress: TodoProgress, color: Color) {
    Column(Modifier.padding(top = 10.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.22f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(color),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(progress.label, fontSize = 12.sp, color = JibitoTheme.colors.muted)
    }
}

/**
 * دکمه‌های یک کار؛ اولی دکمه‌ی اصلی (پُر) است و بقیه حاشیه‌دار.
 * ارتفاع حداقل ۴۸dp؛ اگر برچسب با فونت بزرگ دو خط شد، همه‌ی دکمه‌های ردیف هم‌قدشان می‌شوند.
 */
@Composable
internal fun ActionButtons(actions: List<TodoAction>) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEachIndexed { i, a ->
            val primary = i == 0
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .then(if (primary) Modifier.background(t.btnBg) else Modifier.border(1.dp, t.faint, shape))
                    .clickable(role = Role.Button, onClick = a.onClick)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    a.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (primary) t.btnFg else colors.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}
