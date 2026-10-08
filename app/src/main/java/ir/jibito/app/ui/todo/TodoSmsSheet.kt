package ir.jibito.app.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.summary.TodoAction
import ir.jibito.app.ui.summary.TodoPage
import ir.jibito.app.ui.summary.TodoSms
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali

/**
 * برگه‌ی «پیامک‌های این تراکنش» از تب «کارها»: پیامک رمز دوم، پیامک کسر (و برای انتقال، پیامک واریز)
 * زیر هم، با همان دکمه‌های جواب؛ کاربر خودش متن پیامک‌ها را می‌بیند و همین‌جا تصمیم می‌گیرد.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodoSmsSheet(title: String, page: TodoPage, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = JibitoTheme.colors.sheet,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            TodoSmsContent(
                title,
                page,
                onAnswered = onDismiss,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
            )
        }
    }
}

/** محتوای برگه (جدا، برای اسکرین‌شات): عنوان و توضیح، پیامک‌ها، و دکمه‌های جواب */
@Composable
internal fun TodoSmsContent(title: String, page: TodoPage, onAnswered: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val t = JibitoTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = colors.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(page.detail, fontSize = 13.sp, lineHeight = 21.sp, color = t.muted)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.todo_sms_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = t.muted)
        page.sms.forEach { sms ->
            Spacer(Modifier.height(10.dp))
            SmsBubble(sms)
        }
        Spacer(Modifier.height(18.dp))
        // جواب از همین‌جا هم؛ بعدش برگه بسته می‌شود
        ActionButtons(page.actions.map { a -> TodoAction(a.label) { a.onClick(); onAnswered() } })
    }
}

@Composable
private fun SmsBubble(sms: TodoSms) {
    val colors = MaterialTheme.colorScheme
    val t = JibitoTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(t.chip, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sms.label, modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            sms.dateMillis?.let {
                Text(Jalali.format(it), fontSize = 11.sp, color = t.muted)
            }
        }
        Spacer(Modifier.height(6.dp))
        // متن خام پیامک: چپ‌به‌راست، چون بیشترش عدد است (مثل برگه‌ی تراکنش)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                sms.body,
                modifier = Modifier.fillMaxWidth(),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
