package ir.jibito.app.ui.permission

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.common.MascotFace
import ir.jibito.app.ui.common.PocketMascot
import ir.jibito.app.ui.common.StepDots
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme

/**
 * اجازه‌ی پیامک (قدم ۲ از ۲): جیبی کنجکاو وسط صفحه، یک سؤال کوتاه، و دو قول اصلی در یک کارت
 * (آیکون فیروزه‌ای = حال خوب، نه رنگ هشدار).
 * نقطه‌های قدم و دکمه‌ها همیشه پایین صفحه‌اند؛ کسی که هنوز اعتماد نکرده می‌تواند با دکمه‌ی هم‌قدِ «فعلاً دستی» وارد اپ شود.
 * اجازه‌ی نوتیفیکیشن اینجا پرسیده نمی‌شود: بعد از «جیبت رو شناختم»، با صفحه‌ی خودش ([ir.jibito.app.ui.welcome.NotificationIntroScreen]).
 * اگر قبلاً رد شده، یک یادداشت نرم و دکمه‌ی تنظیمات اپ.
 *
 * @param onManualClick بدون اجازه وارد اپ شود (فقط ثبت دستی)؛ null یعنی این راه نشان داده نشود
 */
@Composable
fun SmsPermissionScreen(
    wasDenied: Boolean,
    onAllowClick: () -> Unit,
    onManualClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val t = JibitoTheme.colors
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(44.dp))
            PocketMascot(MascotFace.CURIOUS, size = 84.dp)
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.perm_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.perm_body),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            // دو قول اصلی، در یک کارت
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(t.sheet, RoundedCornerShape(22.dp))
                    .border(1.dp, t.border, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                PromiseRow(DesignIcons.Shield, stringResource(R.string.perm_promise_offline), stringResource(R.string.perm_promise_no_internet))
                HorizontalDivider(color = t.border)
                PromiseRow(DesignIcons.Message, stringResource(R.string.perm_promise_bank_only), stringResource(R.string.perm_promise_bank_only_sub))
            }

            if (wasDenied) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.perm_denied),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(t.warning.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onBackground,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // نقطه‌های قدم و دکمه‌ها همیشه پایین صفحه، مثل خوش‌آمد
        StepDots(
            current = 2,
            total = 2,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp, bottom = 4.dp),
        )
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 16.dp)) {
            Button(
                onClick = onAllowClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = RoundedCornerShape(50),
            ) {
                Text(stringResource(R.string.perm_allow), fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            if (wasDenied) {
                Spacer(Modifier.height(10.dp))
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
            // «فعلاً دستی» هم‌قد دکمه‌ی اصلی: کسی که هنوز اعتماد نکرده، نباید فکر کند راه دیگری ندارد
            if (onManualClick != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onManualClick,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(stringResource(R.string.perm_manual), color = colors.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** یک قول: آیکون فیروزه‌ای در مربع گرد، عنوان و توضیح کوتاه */
@Composable
private fun PromiseRow(icon: ImageVector, title: String, body: String) {
    val colors = MaterialTheme.colorScheme
    val t = JibitoTheme.colors
    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(t.tealTint, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = t.tealTintFg, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.onBackground)
            Text(body, style = MaterialTheme.typography.bodySmall, color = t.muted)
        }
    }
}
