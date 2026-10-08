package ir.jibito.app.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.category.Nature
import ir.jibito.app.data.category.NatureBreakdown
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.CategoryTree
import ir.jibito.app.ui.common.CategoryIconTile
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/** ترتیب نمایش ماهیت‌ها؛ null = «هنوز معلوم نیست» (آخر، و فقط وقتی خرجی دارد) */
private val ORDER = listOf(Nature.MUST, Nature.NEED, Nature.WANT, null)

@Composable
internal fun natureColor(nature: Nature?): Color {
    val t = JibitoTheme.colors
    return when (nature) {
        Nature.MUST -> t.transferFg
        Nature.NEED -> t.teal
        Nature.WANT -> t.coral
        null -> t.faint
    }
}

@Composable
internal fun natureName(nature: Nature?): String = stringResource(
    when (nature) {
        Nature.MUST -> R.string.nature_must
        Nature.NEED -> R.string.nature_need
        Nature.WANT -> R.string.nature_want
        null -> R.string.nature_unknown
    }
)

@Composable
internal fun natureHint(nature: Nature?): String = stringResource(
    when (nature) {
        Nature.MUST -> R.string.nature_must_hint
        Nature.NEED -> R.string.nature_need_hint
        Nature.WANT -> R.string.nature_want_hint
        null -> R.string.nature_unknown_hint
    }
)

/** خلاصه‌ی یک‌خطی دکمه‌ی بسته: «۶۰٪ اجباری · ۱۵٪ دلخواه»؛ null تا داده نیامده یا خرجی نیست */
@Composable
internal fun natureSummary(b: NatureBreakdown?): String? {
    if (b == null || b.totalRial <= 0) return null
    return Jalali.toPersianDigits(stringResource(R.string.nature_summary, b.percent(Nature.MUST), b.percent(Nature.WANT)))
}

/**
 * «خرجت چه‌جور بود؟»: نوار سه‌رنگ (اجباری، ضروری، دلخواه)، و برای هر ماهیت سهم، مبلغ و دسته‌هایش.
 * لمس هر دسته ← انتخاب ماهیتش (پیش‌فرض‌ها از SpendNature). خرج یک‌باره و بیرون از خرج حساب نمی‌شوند.
 * @param previousMonth اسم ماه قبل، برای مقایسه‌ی خرج دلخواه (فقط بازه‌ی «این ماه»)
 */
@Composable
internal fun NatureContent(
    b: NatureBreakdown?,
    categories: List<Category>,
    previousMonth: String,
    onSetNature: (Category, Int) -> Unit,
) {
    val t = JibitoTheme.colors
    if (b == null) {
        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (b.totalRial <= 0) {
        EmptyNote(stringResource(R.string.nature_empty))
        return
    }
    val byId = remember(categories) { categories.associateBy { it.id } }
    var picking by remember { mutableStateOf<Category?>(null) }
    NatureBar(b)
    Spacer(Modifier.height(12.dp))
    ORDER.filter { it != null || b.amount(null) > 0 }.forEachIndexed { i, nature ->
        if (i > 0) HorizontalDivider(color = t.border, thickness = 1.dp)
        NatureRow(nature, b, byId, onPick = { picking = it })
    }
    b.change(Nature.WANT)?.let { percent ->
        val text = if (percent < 0) {
            stringResource(R.string.nature_want_less, -percent, previousMonth)
        } else {
            stringResource(R.string.nature_want_more, percent, previousMonth)
        }
        Note(Jalali.toPersianDigits(text), bg = if (percent < 0) t.sugBg else t.amberTint, fg = if (percent < 0) t.sugFg else t.amberTintFg)
    }
    Text(
        stringResource(R.string.nature_tap_hint),
        modifier = Modifier.padding(top = 10.dp),
        fontSize = 11.sp,
        lineHeight = 18.sp,
        color = t.faint,
    )
    picking?.let { c ->
        NaturePickerDialog(c, byId, onPick = { nature -> onSetNature(c, nature); picking = null }, onDismiss = { picking = null })
    }
}

/** نوار افقی سه‌رنگ؛ سهم هر ماهیت به اندازه‌ی خرجش */
@Composable
private fun NatureBar(b: NatureBreakdown) {
    val shown = ORDER.filter { b.amount(it) > 0 }
    val description = shown.map { natureName(it) + " " + b.percent(it) + "٪" }.joinToString("، ")
    Row(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(9.dp))
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        shown.forEach { nature ->
            Box(
                Modifier
                    .weight(b.amount(nature).toFloat().coerceAtLeast(1f))
                    .height(18.dp)
                    .background(natureColor(nature)),
            )
        }
    }
}

/** یک ماهیت: رنگ، اسم، سه دسته‌ی پرخرج، سهم و مبلغ؛ لمسش همه‌ی دسته‌هایش را باز می‌کند */
@Composable
private fun NatureRow(nature: Nature?, b: NatureBreakdown, byId: Map<Long, Category>, onPick: (Category) -> Unit) {
    val t = JibitoTheme.colors
    var open by rememberSaveable(nature) { mutableStateOf(false) }
    val items = b.items[nature].orEmpty().mapNotNull { item -> byId[item.categoryId]?.let { it to item.amountRial } }
    val top = items.take(3).joinToString("، ") { it.first.name }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = items.isNotEmpty()) { open = !open }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(natureColor(nature)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(natureName(nature), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                top.ifEmpty { natureHint(nature) },
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
                color = t.muted,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Jalali.toPersianDigits(stringResource(R.string.nature_share, b.percent(nature))),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = natureColor(nature),
            )
            Text(amount(Money.compact(b.amount(nature))), fontSize = 11.sp, color = t.muted, maxLines = 1)
        }
        if (items.isNotEmpty()) {
            Icon(
                JibitoIcons.ChevronForward,
                contentDescription = null,
                tint = t.faint,
                modifier = Modifier.padding(start = 6.dp).size(18.dp).rotate(if (open) 90f else 0f),
            )
        }
    }
    if (open) {
        Column(Modifier.padding(start = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEach { (cat, rial) -> CategoryLine(cat, rial, byId, onClick = { onPick(cat) }) }
        }
    }
}

@Composable
private fun CategoryLine(cat: Category, rial: Long, byId: Map<Long, Category>, onClick: () -> Unit) {
    val root = CategoryTree.rootOf(cat, byId)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconTile(categoryTint(root.colorHex, CategoryTree.iconOf(cat, byId)), size = 28.dp, radius = 9.dp, iconSize = 16.dp)
        Spacer(Modifier.width(10.dp))
        Text(cat.name, modifier = Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(amount(Money.compact(rial)), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}
