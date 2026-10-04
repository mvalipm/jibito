package ir.jibito.app.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.common.PocketMascot
import ir.jibito.app.ui.common.MascotFace
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
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

    // شبیه صفحه‌ی آغاز اپ: رنگ اصلی پوسته، جیبی با چشم‌های بسته، و یک هدف بزرگ اثر انگشت زیر شست
    val t = JibitoTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            // لمس‌ها به اپِ زیر صفحه‌ی قفل نمی‌رسند
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .background(t.btnBg)
            .safeDrawingPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        PocketMascot(MascotFace.SLEEPY, size = 110.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = t.btnFg,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.lock_title),
            style = MaterialTheme.typography.bodyLarge,
            color = t.btnFg.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(t.btnFg.copy(alpha = 0.16f))
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.lock_unlock), onClick = authenticate),
            contentAlignment = Alignment.Center,
        ) {
            Icon(DesignIcons.Fingerprint, contentDescription = null, tint = t.btnFg, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.lock_body),
            style = MaterialTheme.typography.bodyMedium,
            color = t.btnFg.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
    }
}
