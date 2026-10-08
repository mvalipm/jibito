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
import ir.jibito.app.R
import ir.jibito.app.ui.theme.JibitoText
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali

/**
 * یک «کار لازم» در تب «کارها»: حلقه‌ی رنگی دور یک دایره (آیکون یا عدد)، یک عنوان کوتاه و یک جمله توضیح.
 * رنگ حلقه معنی دارد: مرجانی = دسته‌بندی کن، قرمز = بیرون زد، کهربایی = هشدار، فیروزه‌ای = پیشنهاد.
 * @param id شناسه‌ی کار (ترتیبش در تب با todoPriority)
 */
data class TodoStory(
    val id: String,
    val ring: Color,
    val bg: Color,
    val fg: Color,
    val icon: ImageVector?,
    val text: String?,
    val label: String,
    /** یک جمله توضیح زیر عنوان */
    val detail: String? = null,
    /** دکمه‌های داخل کارت برای کارهای یک‌لمسی (مثلاً «روشن کن»، «آره / نه»)؛ اولی دکمه‌ی اصلی است */
    val actions: List<TodoAction> = emptyList(),
    /**
     * چند مورد از یک نوع سؤال در یک کارت (مثلاً چند «خرج یک‌باره بود؟»): کاربر چپ و راست می‌کشد و
     * هر کدام را که خواست جواب می‌دهد. وقتی پر است، detail و actions خود کارت به کار نمی‌روند.
     */
    val pages: List<TodoPage> = emptyList(),
    val onClick: () -> Unit,
) {
    /**
     * کار فوری: تا انجام نشود عددهای اپ غلط یا ناقص است (پیامک مبهم، خرج بی‌دسته، بودجه‌ی نزدیک سقف).
     * فقط این‌ها روی تب «کارها» شمرده می‌شوند؛ بقیه پیشنهادند.
     */
    val urgent: Boolean get() = isUrgentTodo(id)
}

/** یک دکمه‌ی داخل کارت کار */
data class TodoAction(val label: String, val onClick: () -> Unit)

/**
 * یک مورد از کارت چندتایی: توضیح و دکمه‌های خودش. key پایدار (مثلاً شناسه‌ی تراکنش) تا بعد از جواب، جای بقیه نپرد.
 * @param sms پیامک‌های همین مورد (رمز دوم، کسر، واریز)؛ با لمس مورد نشان داده می‌شوند تا کاربر خودش ببیند و تصمیم بگیرد
 */
data class TodoPage(val key: String, val detail: String, val actions: List<TodoAction>, val sms: List<TodoSms> = emptyList())

/** یک پیامک در برگه‌ی «پیامک‌های این تراکنش»: برچسب (مثلاً «پیامک رمز دوم»)، زمان اگر معلوم است، و متن خام */
data class TodoSms(val label: String, val dateMillis: Long?, val body: String)

fun isUrgentTodo(id: String): Boolean = id == "review" || id == "uncat" || id.startsWith("budget-")

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
            style = JibitoText.sectionTitle,
            fontWeight = FontWeight.Black,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
        )
        if (note != null) {
            Text(
                note,
                style = JibitoText.body,
                color = noteColor ?: t.muted,
                fontWeight = if (noteBold) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}
