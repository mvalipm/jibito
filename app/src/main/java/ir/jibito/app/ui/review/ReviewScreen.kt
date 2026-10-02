package ir.jibito.app.ui.review

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.OutlinedTextField
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
import ir.jibito.app.util.Money
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.common.rememberHaptics

private val DepositGreen = Color(0xFF1E9E6A)

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
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.review_share_title)))
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
        Icon(JibitoIcons.Check, contentDescription = null, tint = DepositGreen, modifier = Modifier.size(56.dp))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(
    item: ReviewItem,
    /** چند پیامک منتظرِ دیگر از همین سرشماره هست */
    sameSenderOthers: Int,
    onConfirm: (FlowType, NumberToken, NumberToken?, Int?) -> Unit,
    onDismiss: (ignoreSender: Boolean) -> Unit,
    onAddInstitution: (String) -> Int?,
    onShare: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val g = item.guess
    // انتخاب‌ها برای هر پیامک جدا نگه داشته می‌شوند؛ مقدار اول = حدس اپ
    var type by rememberSaveable(item.smsId) { mutableStateOf(g.type) }
    var amountIndex by rememberSaveable(item.smsId) { mutableStateOf(g.amountIndex) }
    var balanceIndex by rememberSaveable(item.smsId) { mutableStateOf(g.balanceIndex) }
    var ignoreSender by rememberSaveable(item.smsId) { mutableStateOf(false) }
    var bankId by rememberSaveable(item.smsId) { mutableStateOf<Int?>(null) }
    var pickingBank by rememberSaveable(item.smsId) { mutableStateOf(false) }
    val needsBank = item.bankName == null
    val factor = if (g.inToman) 10 else 1

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {

        // متن پیامک
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.bankName ?: item.sender,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    Jalali.format(item.dateMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (item.bankName == null) {
                Text(
                    stringResource(R.string.review_unknown_sender),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(item.body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        }

        // فرستنده‌ی ناشناس: مال کدام بانک/موسسه است؟ (یک بار؛ از این به بعد خودکار شناخته می‌شود)
        if (needsBank) {
            SectionTitle(stringResource(R.string.review_which_bank))
            OutlinedButton(
                onClick = { pickingBank = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    BankDirectory.byId(bankId)?.name ?: stringResource(R.string.review_pick_bank),
                    fontWeight = if (bankId != null) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        // نوع
        SectionTitle(stringResource(R.string.review_type))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeChip(stringResource(R.string.tx_withdrawal), JibitoIcons.ArrowUp, type == FlowType.WITHDRAWAL, colors.primary) { type = FlowType.WITHDRAWAL }
            TypeChip(stringResource(R.string.tx_deposit), JibitoIcons.ArrowDown, type == FlowType.DEPOSIT, DepositGreen) { type = FlowType.DEPOSIT }
        }

        // مبلغ
        SectionTitle(stringResource(R.string.review_pick_amount))
        NumberChips(g.numbers.map { it.raw }, selected = amountIndex, disabled = balanceIndex) { i ->
            amountIndex = if (amountIndex == i) null else i
        }
        amountIndex?.let { i ->
            Text(
                "= " + Money.toman(g.numbers[i].value * factor),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (type == FlowType.DEPOSIT) DepositGreen else colors.primary,
            )
        }

        // مانده (اختیاری)
        if (g.numbers.size > 1) {
            SectionTitle(stringResource(R.string.review_pick_balance))
            NumberChips(g.numbers.map { it.raw }, selected = balanceIndex, disabled = amountIndex) { i ->
                balanceIndex = if (balanceIndex == i) null else i
            }
        }

        Spacer(Modifier.height(22.dp))
        val chosenType = type
        val chosenAmount = amountIndex
        val ready = chosenType != null && chosenAmount != null && (!needsBank || bankId != null)
        Button(
            onClick = {
                if (chosenType != null && chosenAmount != null) {
                    onConfirm(chosenType, g.numbers[chosenAmount], balanceIndex?.let { g.numbers[it] }, bankId)
                }
            },
            enabled = ready,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(stringResource(R.string.review_confirm), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            stringResource(R.string.review_learn_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(14.dp))
        // فرستنده‌ی ناشناس: «این سرشماره اصلاً بانکی نیست» ← همه‌ی پیامک‌هایش با یک لمس کنار می‌روند
        if (item.bankName == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .clickable { ignoreSender = !ignoreSender }
                    .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = ignoreSender, onCheckedChange = { ignoreSender = it })
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.review_ignore_sender),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Text(
                        if (sameSenderOthers > 0) {
                            Jalali.toPersianDigits(stringResource(R.string.review_ignore_sender_more, sameSenderOthers))
                        } else {
                            stringResource(R.string.review_ignore_sender_hint)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = { onDismiss(ignoreSender) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(stringResource(R.string.review_not_transaction))
        }

        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onShare, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.review_share))
        }
        Text(
            stringResource(R.string.review_share_hint),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.review_explain),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (pickingBank) {
        BankPickerDialog(
            senderHint = item.sender,
            onAdd = onAddInstitution,
            onPick = {
                bankId = it
                pickingBank = false
            },
            onDismiss = { pickingBank = false },
        )
    }
}

/**
 * انتخاب بانک یا موسسه: جست‌وجو، «+ افزودن موسسه‌ی جدید» (مثلاً «کارگزاری مفید»)، موسسه‌های کاربر، بانک‌ها، و «سایر».
 */
@Composable
private fun BankPickerDialog(
    senderHint: String,
    onAdd: (String) -> Int?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val options = (BankDirectory.custom.sortedBy { it.name } + BankDirectory.banks.sortedBy { it.name } + BankDirectory.OTHER)
        .filter { q.isEmpty() || it.name.contains(q) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.review_which_bank), fontWeight = FontWeight.Bold) },
        text = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column {
                    if (adding) {
                        // افزودن موسسه‌ی تازه
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it.take(40) },
                            singleLine = true,
                            label = { Text(stringResource(R.string.institution_name)) },
                            placeholder = { Text(stringResource(R.string.institution_name_hint, senderHint)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Button(
                                onClick = { onAdd(newName)?.let(onPick) },
                                enabled = newName.isNotBlank(),
                                shape = RoundedCornerShape(14.dp),
                            ) { Text(stringResource(R.string.institution_save), fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { adding = false }) { Text(stringResource(R.string.budget_dialog_cancel)) }
                        }
                    } else {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.institution_search_hint)) },
                            leadingIcon = { Icon(JibitoIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.institution_add),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    newName = q
                                    adding = true
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                        )
                        LazyColumn(Modifier.height(320.dp)) {
                            items(options, key = { it.id }) { bank ->
                                Text(
                                    bank.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(bank.id) }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (bank.id >= 1000) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.budget_dialog_cancel)) } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun TypeChip(label: String, icon: ImageVector, selected: Boolean, color: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Bold) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = RoundedCornerShape(14.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
            iconColor = color,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberChips(raws: List<String>, selected: Int?, disabled: Int?, onClick: (Int) -> Unit) {
    if (raws.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        raws.forEachIndexed { i, raw ->
            FilterChip(
                selected = selected == i,
                enabled = disabled != i,
                onClick = { onClick(i) },
                label = { Text(Jalali.toPersianDigits(raw), fontWeight = FontWeight.Bold) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
