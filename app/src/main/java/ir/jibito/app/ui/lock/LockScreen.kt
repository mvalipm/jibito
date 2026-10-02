package ir.jibito.app.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import androidx.activity.compose.LocalActivity

/** صفحه‌ی قفل: تا قفل گوشی تأیید نشود، هیچ داده‌ای نشان داده نمی‌شود. پنجره‌ی تأیید خودکار باز می‌شود. */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val authenticate = rememberDeviceAuthenticator(
        title = stringResource(R.string.lock_prompt_title),
        subtitle = stringResource(R.string.lock_prompt_subtitle),
    ) { ok -> if (ok) onUnlocked() }

    LaunchedEffect(Unit) { authenticate() }
    // «برگشت» روی صفحه‌ی قفل: اپ به پس‌زمینه می‌رود (نه صفحه‌ی زیرین)
    val activity = LocalActivity.current
    BackHandler { activity?.moveTaskToBack(true) }

    Column(
        Modifier
            .fillMaxSize()
            // لمس‌ها به اپِ زیر صفحه‌ی قفل نمی‌رسند
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .background(colors.background)
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🔒", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.lock_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = authenticate,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.lock_unlock), fontWeight = FontWeight.Bold) }
    }
}
