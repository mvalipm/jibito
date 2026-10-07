package ir.jibito.app.ui.summary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.repository.CategorySpend
import ir.jibito.app.data.repository.MonthSummary
import ir.jibito.app.ui.common.CategoryIconTile
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.theme.CategoryStyle
import ir.jibito.app.ui.theme.CategoryTint
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/** حداکثر چند دسته جدا دیده شوند؛ بقیه یک تکه‌ی «بقیه» می‌شوند */
private const val WHERE_SLICES = 6

/** کلید تکه‌ی «بقیه و بی‌دسته» */
private const val REST_KEY = -1L

/**
 * «کجا رفت؟» (طرح «جیبی»): نوار سهم دسته‌ها که روی هر تیکه‌اش می‌شود زد،
 * زیرش دسته‌ی انتخاب‌شده درشت (آیکون، اسم، یک نکته، درصد و مبلغ) و کپسول‌های همه‌ی دسته‌ها.
 * لمس دسته‌ی درشت ← ریز خرج و بودجه‌ی همان دسته. «همه‌ی دسته‌ها و بودجه‌ها ›» فهرست کامل را باز می‌کند.
 * سهم‌ها بدون خرج یک‌باره‌اند (یک خرید خانه بقیه‌ی دسته‌ها را از نوار محو نکند)؛ خرج یک‌باره کنار مبلغ دسته
 * و زیر نوار جدا نوشته می‌شود.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhereSection(s: MonthSummary, onOpenCategory: (Long) -> Unit, onShowAll: () -> Unit) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val total = s.budgetSpentRial
    val spent = s.categories.filter { it.budgetSpentRial > 0 }.sortedByDescending { it.budgetSpentRial }.take(WHERE_SLICES)
    val rest = total - spent.sumOf { it.budgetSpentRial }
    var picked by rememberSaveable(s.month.key) { mutableStateOf<Long?>(null) }
    val selected = picked?.takeIf { p -> p == REST_KEY && rest > 0 || spent.any { it.categoryId == p } }
        ?: spent.firstOrNull()?.categoryId

    Column(Modifier.padding(top = 22.dp)) {
        SectionHeader(
            title = stringResource(R.string.where_title),
            note = if (total > 0) stringResource(R.string.where_hint) else null,
        )
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (total <= 0 || spent.isEmpty()) {
                Text(
                    stringResource(R.string.glance_no_spend),
                    modifier = Modifier.padding(top = 12.dp),
                    fontSize = 14.sp,
                    color = t.muted,
                )
                OneOffNote(s.oneOffRial)
                return@Column
            }

            // نوار سهم‌ها: تکه‌ی انتخاب‌شده بلندتر و پررنگ
            val slices = spent.map { Slice(it.categoryId, it.name, it.budgetSpentRial, categoryTint(it.colorHex, it.icon), it) } +
                if (rest > 0) listOf(Slice(REST_KEY, stringResource(R.string.chart_rest), rest, restTint(), null)) else emptyList()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(34.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                slices.forEach { slice ->
                    val on = slice.key == selected
                    val h by animateDpAsState(if (on) 34.dp else 20.dp, spring(dampingRatio = 0.5f, stiffness = 500f), label = "sliceH")
                    val a by animateFloatAsState(if (on) 1f else 0.5f, tween(300), label = "sliceA")
                    val share = share(slice.amount, total)
                    Box(
                        Modifier
                            .weight(maxOf(slice.amount.toFloat() / total, 0.02f))
                            .height(h)
                            .alpha(a)
                            .clip(RoundedCornerShape(7.dp))
                            .background(slice.tint.fg)
                            .clickable { picked = slice.key }
                            .semantics {
                                contentDescription = "${slice.name} $share"
                                this.selected = on
                            },
                    )
                }
            }

            OneOffNote(s.oneOffRial)

            // دسته‌ی انتخاب‌شده، درشت
            val current = slices.firstOrNull { it.key == selected } ?: slices.first()
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    (fadeIn(tween(250)) + slideInVertically(tween(400)) { it / 4 }) togetherWith fadeOut(tween(120))
                },
                contentKey = { it.key },
                label = "whereDetail",
            ) { slice ->
                SelectedRow(slice, total, onClick = { slice.spend?.let { onOpenCategory(it.categoryId) } })
            }

            // کپسول همه‌ی دسته‌ها
            FlowRow(
                Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                slices.forEach { slice ->
                    val on = slice.key == selected
                    val bg by animateColorAsState(if (on) slice.tint.bg else t.chip, tween(300), label = "chipBg")
                    Row(
                        Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(bg)
                            .clickable { picked = slice.key }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(slice.tint.fg))
                        Spacer(Modifier.width(6.dp))
                        Text(slice.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onBackground, maxLines = 1)
                    }
                }
            }

            Text(
                stringResource(R.string.glance_show_all),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onShowAll)
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
            )
        }
    }
}

private data class Slice(val key: Long, val name: String, val amount: Long, val tint: CategoryTint, val spend: CategorySpend?)

@Composable
private fun restTint(): CategoryTint {
    val dark = JibitoTheme.colors.dark
    return CategoryStyle.tint(null, null, dark).copy(icon = DesignIcons.Dots)
}

@Composable
private fun SelectedRow(slice: Slice, total: Long, onClick: () -> Unit) {
    val t = JibitoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = slice.spend != null, onClick = onClick)
            // فاصله از لبه‌ی گرد، تا گوشه‌ها درصد و مبلغ را نبرند
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconTile(slice.tint, size = 56.dp, radius = 18.dp, iconSize = 28.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                slice.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            noteFor(slice.spend)?.let { note ->
                Text(note, fontSize = 13.sp, color = t.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.padding(end = 6.dp), horizontalAlignment = Alignment.Start) {
            Text(
                Jalali.toPersianDigits(share(slice.amount, total)),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = slice.tint.fg,
            )
            Text(amount(Money.compact(slice.amount)), fontSize = 12.sp, color = t.muted)
            // خرج یک‌باره‌ی همین دسته، جدا از سهمش
            slice.spend?.oneOffRial?.takeIf { it > 0 }?.let { oneOff ->
                Text(
                    amount(stringResource(R.string.day_one_off, Money.compact(oneOff))),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.sugFg,
                )
            }
        }
    }
}

/** «+ ★ ۱۰ میلیارد خرج یک‌باره، جدا از این سهم‌ها» زیر نوار؛ بی‌خرج یک‌باره چیزی نمی‌کشد */
@Composable
private fun OneOffNote(oneOffRial: Long) {
    if (oneOffRial <= 0) return
    Text(
        Jalali.toPersianDigits(stringResource(R.string.where_one_off, amount(Money.compact(oneOffRial)))),
        modifier = Modifier.padding(top = 8.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = JibitoTheme.colors.sugFg,
    )
}

/** یک نکته زیر اسم دسته: وضع بودجه‌اش، یا بزرگ‌ترین زیردسته‌هایش */
@Composable
private fun noteFor(c: CategorySpend?): String? {
    c ?: return null
    val budget = c.budgetRial?.takeIf { it > 0 }
    return when {
        budget != null && c.budgetSpentRial > budget ->
            Jalali.toPersianDigits(stringResource(R.string.where_over_budget, (c.budgetSpentRial * 100 / budget - 100).toInt()))
        budget != null -> stringResource(R.string.where_left_budget, amount(Money.compact(budget - c.budgetSpentRial)))
        else -> c.children.filter { it.name != null && it.spentRial > 0 }
            .sortedByDescending { it.spentRial }
            .take(2)
            .joinToString("، ") { it.name.orEmpty() }
            .takeIf { it.isNotEmpty() }
    }
}

private fun share(amount: Long, total: Long): String {
    if (total <= 0) return "۰٪"
    val p = amount * 100.0 / total
    return Jalali.toPersianDigits(if (p > 0 && p < 1) "<۱٪" else "${p.toInt()}٪")
}
