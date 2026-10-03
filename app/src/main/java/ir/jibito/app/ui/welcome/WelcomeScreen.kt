package ir.jibito.app.ui.welcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.common.MascotFace
import ir.jibito.app.ui.common.PocketMascot
import ir.jibito.app.ui.common.rememberMotionOff
import ir.jibito.app.ui.theme.JibitoIcons
import kotlinx.coroutines.delay

/**
 * خوش‌آمد: جیبی، اسم و جمله‌ی اپ، و یک نمونه‌ی کوچکِ قابل لمس از کار اصلی اپ:
 * پیامک بانک می‌آید ← تراکنش «مال چی بود؟» می‌شود ← کاربر یک دسته را می‌زند و ثبت می‌شود.
 * کاربر قبل از هر اجازه‌ای، یک بار خودش انجامش داده است.
 */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val motionOff = rememberMotionOff()
    // ۰: فقط پیامک، ۱: تراکنش هم آمده
    var phase by rememberSaveable { mutableStateOf(if (motionOff) 1 else 0) }
    var picked by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        if (phase == 0) {
            delay(1100)
            phase = 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .drawBehind {
                // یک هاله‌ی بزرگ و محو به رنگ اپ، پشت جیبی
                drawCircle(colors.primary.copy(alpha = 0.10f), radius = size.width * 0.55f, center = Offset(size.width * 0.85f, 0f))
            }
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        PocketMascot(MascotFace.HAPPY, size = 84.dp)
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = colors.onBackground,
        )
        Text(
            text = stringResource(R.string.welcome_tagline),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        SmsBubble()
        AnimatedVisibility(
            visible = phase >= 1,
            enter = fadeIn(tween(400)) + slideInVertically(tween(450)) { -it / 3 },
        ) {
            Column {
                Icon(
                    JibitoIcons.ArrowDown,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp).padding(start = 18.dp).size(20.dp),
                )
                DemoRow(picked)
                Spacer(Modifier.height(12.dp))
                DemoChips(picked, onPick = { picked = it })
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(if (picked == null) R.string.sample_hint else R.string.sample_done),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (picked == null) FontWeight.Normal else FontWeight.Bold,
                    color = if (picked == null) colors.onSurfaceVariant else colors.primary,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp),
            shape = RoundedCornerShape(50),
        ) {
            Text(stringResource(R.string.start_button), fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

/** پیامک نمونه‌ی بانک، شبیه حباب پیام */
@Composable
private fun SmsBubble() {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth(0.86f)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp))
            .background(colors.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            stringResource(R.string.sample_sms_sender),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.sample_sms_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
        )
    }
}

/** همان ردیف فهرست تراکنش‌ها: اول «؟» خط‌چین، بعد از انتخاب، ایموجی دسته با یک جهش */
@Composable
private fun DemoRow(picked: Int?) {
    val colors = MaterialTheme.colorScheme
    val category = picked?.let { DemoCategories[it] }
    val pop by animateFloatAsState(
        if (category != null) 1f else 0.94f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pop",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        val accent = colors.primary
        Box(
            Modifier
                .scale(pop)
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = if (category != null) 0.16f else 0.10f))
                .drawBehind {
                    if (category == null) {
                        val stroke = 2.dp.toPx()
                        drawRoundRect(
                            color = accent,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(16.dp.toPx() - stroke / 2),
                            style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (category != null) Text(category.first, fontSize = 22.sp)
            else Text("؟", fontSize = 22.sp, fontWeight = FontWeight.Black, color = accent)
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.sample_merchant),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
            )
            Text(
                if (category != null) stringResource(category.second) + " · ۲۰:۰۰"
                else stringResource(R.string.tx_ask_expense) + " · ۲۰:۰۰",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (category == null) FontWeight.Bold else null,
                color = if (category == null) colors.primary else colors.onSurfaceVariant,
            )
        }
        // علامت و عدد جدا (مثل فهرست تراکنش‌ها) تا راست‌به‌چپ جای «−» را عوض نکند
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("−", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = colors.onBackground)
            Spacer(Modifier.size(2.dp))
            Text(
                stringResource(R.string.sample_amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
            )
        }
    }
}

/** سه دسته‌ی نمونه؛ لمس یکی، ثبتش می‌کند */
@Composable
private fun DemoChips(picked: Int?, onPick: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 62.dp)) {
        DemoCategories.forEachIndexed { i, (emoji, label) ->
            val selected = picked == i
            val bg by animateColorAsState(if (selected) colors.primary else colors.surfaceVariant, label = "chip")
            val fg by animateColorAsState(if (selected) colors.onPrimary else colors.onSurface, label = "chipText")
            Text(
                "$emoji ${stringResource(label)}",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable(role = Role.Button) { onPick(i) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = fg,
            )
        }
    }
}

/** ایموجی و اسم دسته‌های نمونه */
private val DemoCategories = listOf(
    "🍽" to R.string.cat_food,
    "🚕" to R.string.cat_transport,
    "🛍" to R.string.cat_shopping,
)
