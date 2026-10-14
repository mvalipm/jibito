package ir.jibito.app.ui.pro

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.pro.ProFeature
import ir.jibito.app.data.pro.ProStatus
import ir.jibito.app.data.pro.PurchaseResult
import ir.jibito.app.data.pro.RestoreResult
import ir.jibito.app.ui.common.Mascot
import ir.jibito.app.ui.common.MascotFace
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import kotlinx.coroutines.launch

/**
 * برگه‌ی «جیبیتوی کامل»: چه چیزی دارد، چند روز از ماه رایگان مانده، خرید از کافه‌بازار و «قبلاً خریدم».
 * @param feature امکانی که کاربر رویش زده (در فهرست پررنگ می‌شود)؛ null از تنظیمات
 * @param trialEnded یک بار، خودش بعد از تمام شدن ماه رایگان باز شده
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProSheet(feature: ProFeature?, trialEnded: Boolean, onDismiss: () -> Unit) {
    val pro = (LocalContext.current.applicationContext as JibitoApplication).container.pro
    val status by pro.status.collectAsState()
    val registryOwner = LocalActivityResultRegistryOwner.current
    // خرید ادامه پیدا می‌کند حتی اگر برگه بسته شود؛ نتیجه‌ی موفق در ProAccess ثبت می‌شود
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    var price by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (status != ProStatus.Owned && pro.storeAvailable) price = pro.price()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = JibitoTheme.colors.sheet,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        // فقط بالای برگه (توضیح و فهرست امکانات) اسکرول می‌شود؛ دکمه‌ها پایین ثابت می‌مانند.
        // اسکرولِ کل برگه با کشیدنِ خود برگه (بستن با کشیدن) درگیر می‌شد و برگه می‌پرید.
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            ProSheetHeader(
                status = status,
                feature = feature,
                trialEnded = trialEnded,
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(16.dp))
            ProSheetActions(
                status = status,
                price = price,
                busy = busy,
                message = message,
                onBuy = {
                    val registry = registryOwner?.activityResultRegistry
                    if (!pro.storeAvailable || registry == null) {
                        message = R.string.pro_msg_not_ready
                    } else {
                        busy = true
                        message = null
                        scope.launch {
                            message = when (pro.purchase(registry)) {
                                PurchaseResult.BOUGHT, PurchaseResult.CANCELED -> null
                                PurchaseResult.NO_STORE -> R.string.pro_msg_no_store
                                PurchaseResult.FAILED -> R.string.pro_msg_failed
                            }
                            busy = false
                        }
                    }
                },
                onRestore = {
                    busy = true
                    message = null
                    scope.launch {
                        message = when (pro.restore()) {
                            RestoreResult.FOUND -> null
                            RestoreResult.NOT_FOUND -> R.string.pro_msg_not_found
                            RestoreResult.UNAVAILABLE -> if (pro.storeAvailable) R.string.pro_msg_unavailable else R.string.pro_msg_not_ready
                        }
                        busy = false
                    }
                },
                onClose = onDismiss,
            )
        }
    }
}

/** محتوای برگه (جدا، برای اسکرین‌شات). [message]: متن خطای خرید یا «قبلاً خریدم» */
@Composable
internal fun ProSheetContent(
    status: ProStatus,
    feature: ProFeature?,
    trialEnded: Boolean,
    price: String?,
    busy: Boolean,
    message: Int?,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ProSheetHeader(status, feature, trialEnded)
        Spacer(Modifier.height(16.dp))
        ProSheetActions(status, price, busy, message, onBuy, onRestore, onClose)
    }
}

/** بالای برگه: جیبی، اسم، توضیح (چند روز مانده) و فهرست امکانات */
@Composable
private fun ProSheetHeader(status: ProStatus, feature: ProFeature?, trialEnded: Boolean, modifier: Modifier = Modifier) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val owned = status == ProStatus.Owned
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Mascot(56.dp, face = MascotFace.HAPPY)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                when {
                    owned -> R.string.pro_title_owned
                    trialEnded && status == ProStatus.Free -> R.string.pro_title_ended
                    else -> R.string.pro_name
                }
            ),
            modifier = Modifier.semantics { heading() },
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        val body = when (status) {
            ProStatus.Owned -> stringResource(R.string.pro_body_owned)
            is ProStatus.Trial -> stringResource(R.string.pro_body_trial, status.daysLeft)
            ProStatus.Free -> stringResource(if (trialEnded) R.string.pro_body_ended else R.string.pro_body_free)
        }
        Text(
            Jalali.toPersianDigits(body),
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 14.sp,
            lineHeight = 23.sp,
            color = t.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ProFeature.entries.forEach { FeatureRow(it, highlighted = it == feature) }
        }
    }
}

/** پایین برگه: «باشه» برای صاحب، وگرنه خرید و «قبلاً خریدم» */
@Composable
private fun ProSheetActions(
    status: ProStatus,
    price: String?,
    busy: Boolean,
    message: Int?,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (status == ProStatus.Owned) {
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(50)) {
                Text(stringResource(R.string.pro_close), fontSize = 17.sp, fontWeight = FontWeight.Black)
            }
        } else {
            BuyButtons(price, busy, message, onBuy, onRestore, onClose)
        }
    }
}

/** خرید، پیام خطا، «قبلاً خریدم» / «فعلاً نه» و یادداشت حریم خصوصی */
@Composable
private fun BuyButtons(
    price: String?,
    busy: Boolean,
    message: Int?,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onBuy,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(50),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), color = colors.onPrimary, strokeWidth = 2.5.dp)
            } else {
                Text(
                    if (price != null) stringResource(R.string.pro_buy_price, price) else stringResource(R.string.pro_buy),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
        }
        message?.let {
            Text(
                stringResource(it),
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 13.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Bold,
                color = t.warning,
                textAlign = TextAlign.Center,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onRestore, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.pro_restore), fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.pro_later), fontWeight = FontWeight.Bold, color = t.muted)
            }
        }
        Text(
            stringResource(R.string.pro_note),
            fontSize = 12.sp,
            lineHeight = 19.sp,
            color = t.muted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * یک امکان «جیبیتوی کامل» در فهرست برگه، یک‌خطی تا دکمه‌ی خرید بی‌اسکرول دیده شود؛
 * امکانی که کاربر رویش زده زمینه‌ی رنگی و توضیح دارد
 */
@Composable
private fun FeatureRow(feature: ProFeature, highlighted: Boolean) {
    val t = JibitoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) t.tealTint else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = if (highlighted) 10.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (highlighted) t.sheet else t.tealTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(feature.icon, contentDescription = null, tint = t.tealTintFg, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(feature.title), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            if (highlighted) {
                Text(stringResource(feature.description), fontSize = 13.sp, lineHeight = 20.sp, color = t.muted)
            }
        }
    }
}
