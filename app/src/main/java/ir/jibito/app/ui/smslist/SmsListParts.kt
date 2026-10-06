package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import androidx.compose.ui.draw.clip
import ir.jibito.app.util.Jalali
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.CategoryTint
import ir.jibito.app.ui.theme.categoryTint
import ir.jibito.app.ui.common.BobbingMascot
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.CategoryTree
import ir.jibito.app.domain.Transaction
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue

/** ظاهر دسته‌ی اصلیِ یک تراکنش (برای کاشی رنگی ردیف) */
@Composable
internal fun tintOf(sms: Transaction, byId: Map<Long, Category>): CategoryTint? {
    val c = sms.categoryId?.let { byId[it] } ?: return null
    val root = CategoryTree.rootOf(c, byId)
    return categoryTint(root.colorHex, CategoryTree.iconOf(c, byId))
}

/** سوییچ دوقسمتی «همه | بی‌دسته ۹۹+» (قسمت انتخاب‌شده: کارت روشن روی ریل) */
@Composable
internal fun SegmentedFilter(
    onlyUncategorized: Boolean,
    uncategorizedCount: Int,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = JibitoTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(t.chip)
            .padding(4.dp),
    ) {
        Segment(stringResource(R.string.filter_all), selected = !onlyUncategorized, onClick = { onChange(false) }, modifier = Modifier.weight(1f))
        Segment(
            stringResource(R.string.filter_uncategorized),
            selected = onlyUncategorized,
            badge = uncategorizedCount,
            onClick = { onChange(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, badge: Int = 0) {
    val t = JibitoTheme.colors
    val bg by animateColorAsState(if (selected) t.sheet else t.chip, tween(250), label = "segBg")
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onBackground else t.muted, tween(250), label = "segFg")
    Row(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (badge > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .height(20.dp)
                    .defaultMinSize(minWidth = 20.dp)
                    .clip(CircleShape)
                    .background(t.badge)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Jalali.toPersianDigits(if (badge > 99) "99+" else badge.toString()),
                    color = t.badgeFg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** فیلتر «بی‌دسته» خالی است: جیبی خوشحال */
@Composable
internal fun AllCategorized() {
    val t = JibitoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 30.dp, end = 30.dp, top = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BobbingMascot(120.dp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.uncat_empty_title), fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.uncat_empty_body), fontSize = 14.sp, color = t.muted, textAlign = TextAlign.Center)
    }
}
