package ir.jibito.app.ui.review

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.ui.theme.JibitoTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.review.NumberToken
import ir.jibito.app.data.repository.ReviewItem
import ir.jibito.app.util.Jalali
import androidx.compose.material3.Icon
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.common.rememberHaptics
import ir.jibito.app.ui.common.BobbingMascot
import ir.jibito.app.ui.common.Mascot
import ir.jibito.app.ui.common.MascotFace
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.sp

/**
 * «صندوق بررسی»: پیامک‌هایی که شبیه تراکنش‌اند ولی خودکار خوانده نشدند، یکی‌یکی.
 * کاربر با چند لمس تعیین تکلیف می‌کند: نوع + «روی مبلغ بزن» + ثبت، یا «تراکنش نیست».
 */
@Composable
fun ReviewScreen(onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val viewModel: ReviewViewModel = viewModel(
        factory = ReviewViewModel.factory(
            app.container.reviewRepository,
            // قالب تازه یاد گرفته شد ← کل صندوق دوباره خوانده می‌شود تا پیامک‌های قبلی همین فرستنده هم ثبت شوند
            onLearned = { app.container.transactionRepository.syncFromSms(forceFull = true) },
        )
    )
    val pending by viewModel.pending.collectAsState()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    // متن‌ها از LocalResources (با تغییر پیکربندی، مثلاً زبان یا چرخش، به‌روز می‌ماند)
    val resources = LocalResources.current
    val haptics = rememberHaptics()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
    ) {
        // سرصفحه: برگشت به «کارها»، و جیبی کوچک با یک جمله کنارش (جا برای خود پیامک بماند)
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(JibitoIcons.Back, contentDescription = stringResource(R.string.review_back), tint = colors.onBackground)
            }
            Text(
                stringResource(R.string.review_title),
                modifier = Modifier.weight(1f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
            )
        }
        pending?.takeIf { it.isNotEmpty() }?.let {
            // جیبی با چشم‌های گرد: «مطمئن نیستم»
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Mascot(44.dp, face = MascotFace.UNSURE)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        Jalali.toPersianDigits(stringResource(R.string.review_unsure, it.size)),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.onBackground,
                    )
                    Text(
                        stringResource(R.string.review_help_me) +
                            if (it.size > 1) "  ·  " + stringResource(R.string.review_swipe_card_hint) else "",
                        fontSize = 13.sp,
                        color = JibitoTheme.colors.muted,
                    )
                }
            }
        }

        val list = pending
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> AllDone(onClose)
            else -> ReviewPager(
                list = list,
                onConfirm = { item, type, amount, balance, bankId ->
                    haptics.confirm()
                    viewModel.confirm(item, type, amount, balance, bankId)
                },
                onDismiss = { item, ignore ->
                    haptics.reject()
                    viewModel.dismiss(item, ignore)
                },
                onAddInstitution = { name -> app.container.customInstitutions.add(name) },
                onShare = { item ->
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, viewModel.shareText(item))
                    context.startActivity(Intent.createChooser(send, resources.getString(R.string.review_share_title)))
                },
            )
        }
    }
}

/**
 * پیامک‌های منتظر بررسی، هر کدام یک «کارت»: کارت را به چپ و راست بکشید تا قبلی/بعدی بیاید.
 * کارت‌های کناری کمی پیدا هستند و با حرکت نرم (کوچک و کم‌رنگ شدن) جابه‌جا می‌شوند.
 * وقتی یکی تعیین تکلیف شد از فهرست بیرون می‌رود و کارت بعدی جایش می‌آید.
 */
@Composable
private fun ReviewPager(
    list: List<ReviewItem>,
    onConfirm: (ReviewItem, FlowType, NumberToken, NumberToken?, Int?) -> Unit,
    onDismiss: (ReviewItem, Boolean) -> Unit,
    onAddInstitution: (String) -> Int?,
    onShare: (ReviewItem) -> Unit,
) {
    val currentList by rememberUpdatedState(list)
    val pagerState = rememberPagerState(pageCount = { currentList.size })
    val colors = MaterialTheme.colorScheme
    val bottomSpace = LocalBottomBarSpace.current

    Column(Modifier.fillMaxSize()) {
        // نقطه‌های جای کارت
        if (list.size in 2..12) {
            val current = pagerState.currentPage.coerceIn(0, list.size - 1)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                list.indices.forEach { i ->
                    val active = i == current
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (active) colors.primary else colors.outlineVariant)
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = bottomSpace),
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 24.dp,
            key = { index -> currentList.getOrNull(index)?.smsId ?: -index.toLong() },
            verticalAlignment = Alignment.Top,
        ) { page ->
            val item = currentList.getOrNull(page) ?: return@HorizontalPager
            val target = BankDirectory.normalizeSender(item.sender)
            val sameSenderOthers = currentList.count {
                it.smsId != item.smsId && BankDirectory.normalizeSender(it.sender) == target
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    // موقعیت pager فقط در مرحله‌ی رسم خوانده می‌شود تا کشیدن کارت‌ها recomposition نسازد
                    .graphicsLayer {
                        // فاصله‌ی این کارت از کارت وسط (۰ = وسط، ۱ = یک کارت آن‌طرف‌تر)
                        val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        val distance = kotlin.math.abs(offset).coerceIn(0f, 1f)
                        val scale = 1f - 0.08f * distance
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.45f * distance
                        rotationZ = offset.coerceIn(-1f, 1f) * 2.5f
                    }

            ) {
                ReviewCard(
                    item = item,
                    sameSenderOthers = sameSenderOthers,
                    onConfirm = { type, amount, balance, bankId -> onConfirm(item, type, amount, balance, bankId) },
                    onDismiss = { ignore -> onDismiss(item, ignore) },
                    onAddInstitution = onAddInstitution,
                    onShare = { onShare(item) },
                )
            }
        }
    }
}

/** صندوق خالی: جیبی خوشحال با کاغذرنگی */
@Composable
private fun AllDone(onClose: () -> Unit) {
    val t = JibitoTheme.colors
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f)) }
    Box(Modifier.fillMaxSize()) {
        Confetti(Modifier.fillMaxWidth().height(260.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 30.dp, end = 30.dp, top = 70.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Mascot(150.dp, Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }, face = MascotFace.HAPPY)
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.review_all_done),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.review_all_done_body),
                fontSize = 15.sp,
                lineHeight = 27.sp,
                textAlign = TextAlign.Center,
                color = t.muted,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.review_back),
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(t.chip)
                    .clickable(onClick = onClose)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** ۱۸ تکه کاغذرنگی که یک بار از پشت جیبی بالا می‌پرند و محو می‌شوند */
@Composable
private fun Confetti(modifier: Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2100, easing = LinearEasing)) }
    val palette = listOf(Color(0xFFE4572E), Color(0xFF17BEBB), Color(0xFFF5B83D), Color(0xFF6D3FC0))
    Canvas(modifier) {
        val sx = size.width / 390f
        for (i in 0 until 18) {
            val delay = (i % 6) * 90f / 2100f
            val p = ((progress.value - delay) / (1600f / 2100f)).coerceIn(0f, 1f)
            if (p <= 0f || p >= 1f) continue
            val alpha = if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f
            val x = (40 + (i * 53) % 300) * sx
            val y = 150.dp.toPx() - 120.dp.toPx() * p
            val side = (6 + (i % 3) * 3).dp.toPx() * (0.4f + 0.6f * p)
            val color = palette[i % palette.size].copy(alpha = alpha.coerceIn(0f, 1f))
            rotate(200f * p, pivot = Offset(x, y)) {
                if (i % 2 == 1) drawCircle(color, radius = side / 2, center = Offset(x, y))
                else drawRect(color, topLeft = Offset(x - side / 2, y - side / 2), size = Size(side, side))
            }
        }
    }
}
