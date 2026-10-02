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
        // سرصفحه
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.review_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = colors.onBackground,
                )
                pending?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        Jalali.toPersianDigits(stringResource(R.string.review_count, it.size)) +
                            if (it.size > 1) "  ·  " + stringResource(R.string.review_swipe_card_hint) else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.review_later)) }
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
            contentPadding = PaddingValues(horizontal = 22.dp),
            pageSpacing = 10.dp,
            key = { index -> currentList.getOrNull(index)?.smsId ?: -index.toLong() },
            verticalAlignment = Alignment.Top,
        ) { page ->
            val item = currentList.getOrNull(page) ?: return@HorizontalPager
            val target = BankDirectory.normalizeSender(item.sender)
            val sameSenderOthers = currentList.count {
                it.smsId != item.smsId && BankDirectory.normalizeSender(it.sender) == target
            }
            // فاصله‌ی این کارت از کارت وسط (۰ = وسط، ۱ = یک کارت آن‌طرف‌تر)
            val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            val distance = kotlin.math.abs(offset).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .graphicsLayer {
                        val scale = 1f - 0.08f * distance
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.45f * distance
                        rotationZ = offset.coerceIn(-1f, 1f) * 2.5f
                    }
                    .shadow(10.dp, RoundedCornerShape(28.dp), clip = false)
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.surface)
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

@Composable
private fun AllDone(onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(JibitoIcons.Check, contentDescription = null, tint = JibitoTheme.colors.income, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.review_all_done),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onClose, shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.review_back)) }
    }
}
