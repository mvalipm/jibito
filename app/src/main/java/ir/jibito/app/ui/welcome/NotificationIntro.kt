package ir.jibito.app.ui.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.common.MascotFace
import ir.jibito.app.ui.common.PocketMascot
import ir.jibito.app.ui.common.rememberMotionOff
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Money

/** صفحه‌ی توضیح نوتیفیکیشن لازم است؟ اندروید ۱۳ به بعد اجازه می‌خواهد؛ قبل از آن، یا اگر داده شده، نه */
fun notificationIntroNeeded(sdkInt: Int, granted: Boolean): Boolean = sdkInt >= 33 && !granted

/**
 * پیش از پنجره‌ی اجازه‌ی نوتیفیکیشن اندروید: چرا نوتیف؟ چون «مال چی بود؟» همان لحظه و بدون باز کردن اپ جواب داده می‌شود.
 * یک نوتیف نمونه (با همان متن نوتیف واقعی) از بالا پایین می‌آید و کاربر می‌تواند یک دسته‌اش را بزند، مثل نمونه‌ی خوش‌آمد.
 * بعد از «بزن بریم» در «جیبت رو شناختم» (یا اولین خواندن پیامک‌ها بی آن) جای پنجره‌ی خشک اندروید می‌آید.
 * «بعداً» هم‌قد دکمه‌ی اصلی است؛ دکمه‌ی «برگشت» گوشی هم همان «بعداً» است.
 *
 * @param initialPick فقط برای اسکرین‌شات: دسته‌ی از پیش زده‌شده
 */
@Composable
fun NotificationIntroScreen(onAllow: () -> Unit, onLater: () -> Unit, initialPick: Int? = null) {
    val colors = MaterialTheme.colorScheme
    val motionOff = rememberMotionOff()
    var picked by rememberSaveable { mutableStateOf(initialPick) }
    var arrived by rememberSaveable { mutableStateOf(motionOff) }
    LaunchedEffect(Unit) { arrived = true }
    BackHandler(onBack = onLater)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
    ) {
        BoxWithConstraints(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(Modifier.height(12.dp))
                PocketMascot(if (picked == null) MascotFace.CURIOUS else MascotFace.HAPPY, size = 84.dp)
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.notif_intro_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = colors.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.notif_intro_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                // نوتیف نمونه آرام از بالا پایین می‌آید؛ جایش از اول هست تا بقیه‌ی صفحه جابه‌جا نشود
                val shown by animateFloatAsState(if (arrived) 1f else 0f, tween(500), label = "notif")
                Box(
                    Modifier.graphicsLayer {
                        alpha = shown
                        translationY = (1f - shown) * -40.dp.toPx()
                    },
                ) {
                    SampleNotification(picked, onPick = { picked = it })
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(if (picked == null) R.string.sample_hint else R.string.notif_intro_done),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (picked == null) FontWeight.Normal else FontWeight.Bold,
                    color = if (picked == null) colors.onSurfaceVariant else colors.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        Text(
            stringResource(R.string.notif_intro_promise),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = JibitoTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 16.dp)) {
            Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = RoundedCornerShape(50),
            ) {
                Text(stringResource(R.string.notif_intro_allow), fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onLater,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = RoundedCornerShape(50),
            ) {
                Text(stringResource(R.string.notif_intro_later), color = colors.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** شبیه نوتیف واقعی «مال چی بود؟»: سرِ اپ، مبلغ و فروشگاه، سؤال و سه دکمه‌ی دسته؛ بعد از لمس، همان «رفت تو …» نوتیف واقعی */
@Composable
private fun SampleNotification(picked: Int?, onPick: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val t = JibitoTheme.colors
    val merchant = stringResource(R.string.sample_merchant)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp))
            .background(t.sheet)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_stat_jibito), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.size(6.dp))
            Text(
                stringResource(R.string.app_name) + " · " + stringResource(R.string.notif_intro_now),
                style = MaterialTheme.typography.labelMedium,
                color = t.muted,
            )
        }
        Spacer(Modifier.height(6.dp))
        AnimatedContent(picked, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "notifText") { pick ->
            Column {
                val category = pick?.let { stringResource(DemoCategories[it].second) }
                Text(
                    // همان عنوان نوتیف واقعی: «−۲۵۰ هزار · کافه لمیز» و بعد از لمس «رفت تو غذا»
                    if (category == null) Money.compact(-SAMPLE_AMOUNT_RIAL) + " · " + merchant
                    else stringResource(R.string.notif_picked_title, category),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = colors.onBackground,
                )
                Text(
                    if (category == null) stringResource(R.string.notif_question)
                    else stringResource(R.string.notif_picked_text, merchant),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (picked == null) {
            Spacer(Modifier.height(10.dp))
            DemoChips(picked = null, onPick = onPick)
        }
    }
}

/** همان مبلغ نمونه‌ی خوش‌آمد (۲٬۵۰۰٬۰۰۰ ریال) */
private const val SAMPLE_AMOUNT_RIAL = 2_500_000L
