package ir.jibito.app.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.common.MascotFace
import ir.jibito.app.ui.common.PocketMascot
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali

/** کارت «جیبت رو مرتب کنیم»: عنوان، «۱ از ۳»، نوار پیشرفت و قدم‌ها (انجام‌نشده‌ها لمس‌شدنی‌اند)؛ یا جشن «جیبت آماده‌ست» */
@Composable
fun FirstStepsCard(ui: FirstStepsUi, modifier: Modifier = Modifier) {
    val t = JibitoTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.sheet)
            .border(1.dp, t.border, shape),
    ) {
        if (ui.celebrating) Celebration(ui.onHide) else Steps(ui)
    }
}

@Composable
private fun Steps(ui: FirstStepsUi) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val doneCount = ui.steps.count { it.done }
    val total = ui.steps.size
    Column(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.steps_title),
                modifier = Modifier.semantics { heading() },
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = colors.onBackground,
            )
            Spacer(Modifier.width(8.dp))
            ProgressPill(Jalali.toPersianDigits(stringResource(R.string.steps_progress, doneCount, total)))
            Spacer(Modifier.weight(1f))
            val hide = stringResource(R.string.steps_hide)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = ui.onHide)
                    .semantics { contentDescription = hide },
                contentAlignment = Alignment.Center,
            ) {
                Icon(JibitoIcons.Close, contentDescription = null, tint = t.faint, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        // نوار پیشرفت
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(t.chip),
        ) {
            if (doneCount > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(doneCount.toFloat() / total)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(t.teal),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // اول کارهای مانده، بعد انجام‌شده‌ها
        ui.steps.sortedBy { it.done }.forEach { StepRow(it) }
    }
}

@Composable
private fun ProgressPill(text: String) {
    val t = JibitoTheme.colors
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(t.tealTint)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = t.tealTintFg,
    )
}

@Composable
private fun StepRow(step: FirstStep) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val doneText = stringResource(R.string.steps_done_a11y)
    // قدمی که داخل خودش دکمه دارد (فروشگاه‌ها، پیشنهاد بودجه) خودش لمس‌شدنی نیست تا دو کار روی هم نیفتند
    val hasInner = step.chips.isNotEmpty() || step.offer != null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (step.done || hasInner) Modifier else Modifier.clickable(role = Role.Button, onClick = step.onClick))
            .semantics { if (step.done) stateDescription = doneText }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = if (!step.done && hasInner) Alignment.Top else Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .then(
                    if (step.done) Modifier.background(t.teal)
                    else Modifier.border(2.dp, t.border, CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (step.done) Icon(JibitoIcons.Check, contentDescription = null, tint = t.onFg, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // برچسب پیشرفت کنار خط اول عنوان، حتی اگر عنوان دو خط شود
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    step.label,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = 15.sp,
                    fontWeight = if (step.done) FontWeight.Medium else FontWeight.Bold,
                    color = if (step.done) t.faint else colors.onBackground,
                    textDecoration = if (step.done) TextDecoration.LineThrough else null,
                )
                if (!step.done && step.progress != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.padding(top = 2.dp)) { ProgressPill(step.progress) }
                }
            }
            if (!step.done) {
                Text(step.detail, fontSize = 13.sp, lineHeight = 21.sp, color = t.muted)
                if (step.chips.isNotEmpty()) MerchantChips(step.chips)
                step.offer?.let { OfferButtons(it) }
            }
        }
        if (!step.done && !hasInner) {
            Spacer(Modifier.width(8.dp))
            Icon(JibitoIcons.ChevronForward, contentDescription = null, tint = t.faint, modifier = Modifier.size(20.dp))
        }
    }
}

/** فروشگاه‌های پرتکرار: یادداده‌ها با تیک فیروزه‌ای، بقیه با تعداد خرج بی‌دسته؛ هر کدام برگه‌ی «مال چی بود؟» را باز می‌کند */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MerchantChips(chips: List<StepChip>) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    FlowRow(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chips.forEach { chip ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (chip.done) t.tealTint else t.chip)
                    .clickable(role = Role.Button, onClick = chip.onClick)
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chip.done) {
                    Icon(JibitoIcons.Check, contentDescription = null, tint = t.tealTintFg, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    chip.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (chip.done) t.tealTintFg else colors.onBackground,
                )
                if (!chip.done && chip.count > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        Jalali.toPersianDigits(stringResource(R.string.steps_merchant_times, chip.count)),
                        fontSize = 12.sp,
                        color = t.muted,
                    )
                }
            }
        }
    }
}

/** پیشنهاد یک‌لمسی («آره، ۲۲ میلیون») و راه دیگرش، کنار هم */
@Composable
private fun OfferButtons(offer: StepOffer) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = offer.onAccept,
            modifier = Modifier.heightIn(min = 40.dp),
            shape = RoundedCornerShape(50),
            contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) {
            Text(offer.accept, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
        }
        OutlinedButton(
            onClick = offer.onOther,
            modifier = Modifier.heightIn(min = 40.dp),
            shape = RoundedCornerShape(50),
            contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) {
            Text(offer.other, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}

/** همه انجام شد: جیبیِ خندان و «جیبت آماده‌ست»؛ «باشه» کارت را برای همیشه می‌برد */
@Composable
private fun Celebration(onOk: () -> Unit) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PocketMascot(MascotFace.HAPPY, size = 64.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.steps_done_title),
            modifier = Modifier.semantics { heading() },
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.steps_done_body),
            fontSize = 13.sp,
            lineHeight = 22.sp,
            color = t.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOk, modifier = Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(50)) {
            Text(stringResource(R.string.steps_done_ok), fontWeight = FontWeight.Bold, color = colors.onBackground)
        }
    }
}
