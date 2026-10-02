package ir.jibito.app.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali

/**
 * یک مورد «کار لازم».
 * @param label برچسب کوتاه زیر حباب (۱-۲ کلمه)
 * @param text جمله‌ی کامل؛ صفحه‌خوان همین را می‌خواند
 */
data class AttentionItem(
    val icon: ImageVector,
    val label: String,
    val text: String,
    val tone: Tone,
    val onClick: () -> Unit,
) {
    enum class Tone { NORMAL, WARN, DANGER }
}

/**
 * ۳) چه کاری لازم است؟ — مثل استوری: یک ردیف حباب گرد با حلقه‌ی رنگی (رنگ = فوریت) و برچسب کوتاه زیرش.
 * فقط وقتی چیزی هست؛ هر حباب با یک لمس به همان‌جا می‌برد.
 */
@Composable
fun AttentionCard(items: List<AttentionItem>) {
    if (items.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.attn_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.primary)
                    .padding(horizontal = 9.dp, vertical = 1.dp),
            ) {
                Text(
                    Jalali.toPersianDigits(items.size.toString()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = colors.onPrimary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.forEach { StoryBubble(it) }
        }
    }
}

@Composable
private fun StoryBubble(item: AttentionItem) {
    val colors = MaterialTheme.colorScheme
    val tint = when (item.tone) {
        AttentionItem.Tone.DANGER -> colors.error
        AttentionItem.Tone.WARN -> JibitoTheme.colors.warning
        AttentionItem.Tone.NORMAL -> colors.primary
    }
    // فونت بزرگ گوشی: ستون پهن‌تر تا برچسب جا شود
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 1.8f)
    Column(
        Modifier
            .width(80.dp * scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = item.onClick)
            .clearAndSetSemantics {
                contentDescription = item.text
                role = Role.Button
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(66.dp)
                .border(3.dp, tint, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
