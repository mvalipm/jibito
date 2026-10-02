package ir.jibito.app.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.jibito.app.ui.theme.JibitoTheme
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import ir.jibito.app.ui.theme.JibitoIcons

/** یک مورد «کار لازم» */
data class AttentionItem(val icon: ImageVector, val text: String, val tone: Tone, val onClick: () -> Unit) {
    enum class Tone { NORMAL, WARN, DANGER }
}

/** ۳) چه کاری لازم است؟ — فقط وقتی چیزی هست؛ هر مورد با یک لمس به همان‌جا می‌برد */
@Composable
fun AttentionCard(items: List<AttentionItem>) {
    if (items.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(22.dp))
            .padding(vertical = 6.dp)
    ) {
        items.forEach { item ->
            val tint = when (item.tone) {
                AttentionItem.Tone.DANGER -> colors.error
                AttentionItem.Tone.WARN -> JibitoTheme.colors.warning
                AttentionItem.Tone.NORMAL -> colors.primary
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = item.onClick)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.size(10.dp))
                Text(
                    item.text,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(JibitoIcons.ChevronForward, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}
