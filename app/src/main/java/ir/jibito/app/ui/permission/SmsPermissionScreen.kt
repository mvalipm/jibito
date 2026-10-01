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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R

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
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Spacer(Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(colors.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("✉", fontSize = 34.sp, color = colors.onSecondaryContainer)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.perm_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.perm_body),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PromiseRow(stringResource(R.string.perm_promise_offline))
                    PromiseRow(stringResource(R.string.perm_promise_bank_only))
                    PromiseRow(stringResource(R.string.perm_promise_no_send))
                }
            }

            if (wasDenied) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.perm_denied),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onAllowClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(stringResource(R.string.perm_allow), fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(stringResource(R.string.perm_open_settings))
                }
            }
        }
    }
}

@Composable
private fun PromiseRow(text: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(colors.secondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", color = colors.onSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
    }
}
