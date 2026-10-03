package ir.jibito.app.ui.permission

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.common.MascotFace
import ir.jibito.app.ui.common.PocketMascot
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme

/**
 * اجازه‌ی پیامک: جیبی کنجکاو، چرا لازم است، و چهار قول (هر کدام با آیکون خودش، بدون کارت).
 * اگر قبلاً رد شده، یک یادداشت نرم و دکمه‌ی تنظیمات اپ.
 */
@Composable
fun SmsPermissionScreen(
    wasDenied: Boolean,
    onAllowClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        PocketMascot(MascotFace.CURIOUS, size = 84.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.perm_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = colors.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.perm_body),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PromiseRow(JibitoIcons.Lock, stringResource(R.string.perm_promise_offline))
            PromiseRow(JibitoIcons.Bank, stringResource(R.string.perm_promise_bank_only))
            PromiseRow(JibitoIcons.Close, stringResource(R.string.perm_promise_no_send))
            PromiseRow(JibitoIcons.Bell, stringResource(R.string.perm_promise_notify))
        }

        if (wasDenied) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.perm_denied),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JibitoTheme.colors.warning.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground,
            )
        }

        Spacer(Modifier.height(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onAllowClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = RoundedCornerShape(50),
            ) {
                Text(stringResource(R.string.perm_allow), fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            if (wasDenied) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(stringResource(R.string.perm_open_settings), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** یک قول: آیکون در مربع گرد کم‌رنگ، و جمله */
@Composable
private fun PromiseRow(icon: ImageVector, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.onBackground)
    }
}
