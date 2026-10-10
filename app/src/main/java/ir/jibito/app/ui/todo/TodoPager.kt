package ir.jibito.app.ui.todo

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.summary.TodoPage
import ir.jibito.app.ui.summary.TodoStory
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme

/** بیشترین تعداد نقطه‌ی زیر کارت چندتایی؛ بیشتر از این، جای نقطه‌ی فعال نسبی است */
internal const val MAX_PAGER_DOTS = 7

/** نقطه‌ی فعال از بین [MAX_PAGER_DOTS] نقطه: با کمتر از ۷ مورد همان شماره‌ی صفحه، با بیشتر نسبی (اولی اول، آخری آخر) */
internal fun pagerDotIndex(current: Int, count: Int): Int {
    if (count <= MAX_PAGER_DOTS) return current.coerceIn(0, (count - 1).coerceAtLeast(0))
    return (current.coerceIn(0, count - 1) * (MAX_PAGER_DOTS - 1) + (count - 1) / 2) / (count - 1)
}

/**
 * بدنه‌ی کارت چندتایی: هر صفحه توضیح و دکمه‌های خودش را دارد، کاربر چپ و راست می‌کشد.
 * ارتفاع جای متن توضیح برای همه‌ی صفحه‌ها اندازه‌ی بلندترین توضیح است (از پیش اندازه‌گیری می‌شود)،
 * تا با ورق زدن کارت (و لیست زیرش) نپرد؛ بسته به عرض گوشی و اندازه‌ی فونت، تعداد خط‌های متن فرق می‌کند.
 */
@Composable
internal fun TodoPager(s: TodoStory, pager: PagerState, onOpenSms: (TodoStory, TodoPage) -> Unit) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current
    val detailStyle = base.merge(TextStyle(fontSize = 13.sp, lineHeight = 21.sp))
    val linkStyle = base.merge(TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold))
    val linkText = stringResource(R.string.todo_show_sms)
    val details = s.pages.map { it.detail }
    val anySms = s.pages.any { it.sms.isNotEmpty() }
    BoxWithConstraints(Modifier.fillMaxWidth().animateContentSize()) {
        val widthPx = constraints.maxWidth
        val detailHeight = remember(details, widthPx, density, detailStyle) {
            val px = details.maxOfOrNull { measurer.measure(it, detailStyle, constraints = Constraints(maxWidth = widthPx)).size.height } ?: 0
            with(density) { px.toDp() }
        }
        // ردیف «پیامک‌هاش رو ببین»: آیکون ۱۵dp یا متن (هرکدام بلندتر) و فاصله‌ی ۴ بالا و ۲ پایین
        val linkRowHeight = remember(linkText, density, linkStyle) {
            val px = measurer.measure(linkText, linkStyle).size.height
            with(density) { maxOf(px.toDp(), 15.dp) } + 6.dp
        }
        HorizontalPager(
            state = pager,
            key = { s.pages.getOrNull(it)?.key ?: it },
            pageSpacing = 16.dp,
            verticalAlignment = Alignment.Top,
        ) { page ->
            val p = s.pages.getOrNull(page) ?: return@HorizontalPager
            Column {
                Spacer(Modifier.height(8.dp))
                if (p.sms.isEmpty()) {
                    Text(p.detail, modifier = Modifier.heightIn(min = detailHeight), fontSize = 13.sp, lineHeight = 21.sp, color = t.muted)
                    // اگر صفحه‌های دیگر لینک پیامک دارند، جایش همین‌جا هم خالی بماند
                    if (anySms) Spacer(Modifier.height(linkRowHeight))
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenSms(s, p) }
                    ) {
                        Text(p.detail, modifier = Modifier.heightIn(min = detailHeight), fontSize = 13.sp, lineHeight = 21.sp, color = t.muted)
                        Row(Modifier.padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(DesignIcons.Message, contentDescription = null, tint = colors.primary, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(linkText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                ActionButtons(p.actions)
            }
        }
    }
    if (s.pages.size > 1) {
        Spacer(Modifier.height(10.dp))
        PagerDots(count = s.pages.size, current = pager.currentPage, active = s.fg)
    }
}

/** نقطه‌های صفحه‌ی کارت چندتایی (برای دیدنی بودن ورق خوردن)؛ شماره‌اش را همان «۱ از ۶» بالای کارت می‌گوید */
@Composable
private fun PagerDots(count: Int, current: Int, active: Color) {
    val shown = minOf(count, MAX_PAGER_DOTS)
    val on = pagerDotIndex(current, count)
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(shown) { i ->
            Spacer(
                Modifier
                    .height(6.dp)
                    .width(if (i == on) 16.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (i == on) active else JibitoTheme.colors.faint.copy(alpha = 0.35f)),
            )
        }
    }
}
