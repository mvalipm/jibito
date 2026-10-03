package ir.jibito.app.ui.summary

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali

/**
 * یک «کار لازم» به شکل استوری: حلقه‌ی رنگی دور یک دایره (آیکون یا عدد) و یک برچسب کوتاه زیرش.
 * رنگ حلقه معنی دارد: مرجانی = دسته‌بندی کن، قرمز = بیرون زد، کهربایی = هشدار، فیروزه‌ای = پیشنهاد.
 * @param id برای یادآوری «دیده شد» (حلقه خاکستری می‌شود)
 */
data class TodoStory(
    val id: String,
    val ring: Color,
    val bg: Color,
    val fg: Color,
    val icon: ImageVector?,
    val text: String?,
    val label: String,
    val onClick: () -> Unit,
)

/** «کارهای لازم»: ردیف افقی استوری‌ها؛ فقط وقتی چیزی هست */
@Composable
fun TodoSection(stories: List<TodoStory>) {
    if (stories.isEmpty()) return
    val t = JibitoTheme.colors
    // شناسه‌های دیده‌شده، با «،» جدا (تا بعد از چرخش صفحه هم بماند)
    var seenRaw by rememberSaveable { mutableStateOf("") }
    val seen = seenRaw.split(',').filter { it.isNotEmpty() }.toSet()
    val unseen = stories.count { it.id !in seen }

    Column(Modifier.padding(top = 14.dp)) {
        SectionHeader(
            title = stringResource(R.string.todo_title),
            note = Jalali.toPersianDigits(stringResource(R.string.todo_count, unseen)),
        )
        LazyRow(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(stories, key = { it.id }) { s ->
                val isSeen = s.id in seen
                StoryBubble(s, isSeen) {
                    if (!isSeen) seenRaw = (seen + s.id).joinToString(",")
                    s.onClick()
                }
            }
        }
    }
}

@Composable
private fun StoryBubble(s: TodoStory, seen: Boolean, onClick: () -> Unit) {
    val t = JibitoTheme.colors
    val ring by animateColorAsState(if (seen) t.seenRing else s.ring, tween(400), label = "ring")
    Column(
        Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(68.dp)
                .border(3.dp, ring, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(s.bg),
            contentAlignment = Alignment.Center,
        ) {
            if (s.icon != null) {
                Icon(s.icon, contentDescription = null, tint = s.fg, modifier = Modifier.size(26.dp))
            } else {
                Text(s.text.orEmpty(), color = s.fg, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            s.label,
            color = if (seen) t.faint else androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** سرتیتر هر بخش «خلاصه»: عنوان درشت و یک یادداشت کوچک در طرف دیگر */
@Composable
fun SectionHeader(title: String, note: String? = null, noteColor: Color? = null, noteBold: Boolean = false) {
    val t = JibitoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
        )
        if (note != null) {
            Text(
                note,
                fontSize = 13.sp,
                color = noteColor ?: t.muted,
                fontWeight = if (noteBold) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}
