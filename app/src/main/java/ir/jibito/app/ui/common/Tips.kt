package ir.jibito.app.ui.common

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import ir.jibito.app.R
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme

/** نکته‌هایی که فقط یک بار، همان جایی که به کار می‌آیند، نشان داده می‌شوند */
enum class Tip(val key: String) {
    /** اولین خرج بی‌دسته در «تراکنش‌ها» */
    UNCATEGORIZED("uncategorized"),
    /** اولین بار در صندوق «بررسی» */
    REVIEW("review"),
    /** اولین پیشنهاد «انتقال به خودت؟» */
    TRANSFER("transfer"),
    /** «گزارش‌ها» وقتی هنوز ماه قبلی برای مقایسه نیست */
    REPORTS_THIN("reports_thin"),
}

/** کدام نکته‌ها دیده و بسته شده‌اند */
class TipStore(context: Context) {
    private val prefs = context.getSharedPreferences("tips", Context.MODE_PRIVATE)

    fun seen(tip: Tip): Boolean = prefs.getBoolean(tip.key, false)

    fun markSeen(tip: Tip) = prefs.edit { putBoolean(tip.key, true) }

    /** «دوباره نشون دادن نکته‌ها» در «راهنما» */
    fun resetAll() = prefs.edit { clear() }
}

/** [visible]: هنوز دیده و بسته نشده. [dismiss]: دیگر هیچ‌وقت نشان داده نشود (مثلاً با «فهمیدم» یا انجام همان کار) */
class TipState(val visible: Boolean, val dismiss: () -> Unit)

@Composable
fun rememberTip(tip: Tip): TipState {
    val context = LocalContext.current
    val store = remember { TipStore(context.applicationContext) }
    var seen by remember { mutableStateOf(store.seen(tip)) }
    return TipState(!seen) {
        if (!seen) {
            store.markSeen(tip)
            seen = true
        }
    }
}

/** کارت کوچک نکته: یک جمله و «فهمیدم» */
@Composable
fun TipCard(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val t = JibitoTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.tealTint)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(DesignIcons.Star, contentDescription = null, tint = t.tealTintFg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 13.sp, lineHeight = 21.sp, color = t.tealTintFg)
        }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClick = onDismiss)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.tip_got_it), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = t.tealTintFg)
        }
    }
}
