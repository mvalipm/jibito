package ir.jibito.app.ui.smslist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.parser.MerchantExtractor
import ir.jibito.app.data.parser.MerchantRules
import ir.jibito.app.data.repository.MerchantLesson
import ir.jibito.app.domain.Transaction
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import android.widget.Toast

/** نوار «اسم فروشگاه رو نخوندم، یادم بده ›» زیر سربرگ برگه‌ی دسته (فقط خرجِ پیامکیِ بی‌طرف‌حساب) */
@Composable
internal fun TeachMerchantBar(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val jt = JibitoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.primaryContainer.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).background(jt.sheet, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(DesignIcons.Tag, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.teach_bar_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
            Text(stringResource(R.string.teach_bar_hint), fontSize = 11.5.sp, color = jt.muted, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.teach_bar_action), fontSize = 13.sp, fontWeight = FontWeight.Black, color = colors.primary)
    }
}

/**
 * «اسم فروشگاه کدومه؟» و بعد از ثبت، پیام کوتاه «یاد گرفتم» (پیام سیستم، چون برگه‌ی دسته روی صفحه باز است).
 * [teach]: اسم، درس، و خبرِ «چند تراکنش دیگر هم اسم گرفتند».
 */
@Composable
internal fun TeachMerchantFlow(
    transaction: Transaction,
    teach: (String, MerchantLesson?, (Int) -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    TeachMerchantSheet(
        transaction = transaction,
        onConfirm = { name, lesson ->
            teach(name, lesson) { others ->
                if (lesson != null) {
                    val text = if (others > 0) {
                        context.getString(R.string.teach_learned_more, Jalali.toPersianDigits(others.toString()))
                    } else {
                        context.getString(R.string.teach_learned)
                    }
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                }
            }
            onClose()
        },
        onDismiss = onClose,
    )
}

/** خطی که کاربر انتخاب کرده: از پیامک رمز دوم یا خود پیامک تراکنش */
private data class PickedLine(val fromOtp: Boolean, val index: Int)

/**
 * «اسم فروشگاه کدومه؟»: خط‌به‌خطِ پیامک رمز دوم و پیامک تراکنش. خط‌های عددی (مبلغ، رمز، ساعت، مانده) کم‌رنگ‌اند و انتخاب نمی‌شوند.
 * یا «خودم می‌نویسم» (فقط برای همین تراکنش). با تیک «پیامک‌های بعدی…» شکل پیامک هم یاد گرفته می‌شود.
 * [onConfirm]: اسم، و درس (null یعنی فقط همین تراکنش).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachMerchantSheet(
    transaction: Transaction,
    onConfirm: (merchant: String, lesson: MerchantLesson?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val jt = JibitoTheme.colors
    val otpLines = transaction.otpBody?.let(MerchantRules::lines).orEmpty()
    val smsLines = MerchantRules.lines(transaction.body)
    var picked by rememberSaveable(transaction.id) { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var typing by rememberSaveable(transaction.id) { mutableStateOf(otpLines.isEmpty() && smsLines.none(MerchantRules::isPickable)) }
    var typed by rememberSaveable(transaction.id) { mutableStateOf("") }
    var learnShape by rememberSaveable(transaction.id) { mutableStateOf(true) }

    val pick = picked?.let { PickedLine(it.first, it.second) }
    val pickedName = pick?.let { (if (it.fromOtp) otpLines else smsLines).getOrNull(it.index) }?.let(MerchantExtractor::clean)
    val name = if (typing) typed.trim().takeIf { it.isNotEmpty() } else pickedName

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = jt.sheet,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Text(stringResource(R.string.teach_title), fontSize = 19.sp, fontWeight = FontWeight.Black, color = colors.onSurface)
                Text(stringResource(R.string.teach_subtitle), fontSize = 12.5.sp, color = jt.muted)

                if (!typing) {
                    if (otpLines.isNotEmpty()) {
                        SectionLabel(stringResource(R.string.teach_otp_section))
                        LineList(otpLines, maskCode = true, selected = pick?.takeIf { it.fromOtp }?.index) { picked = true to it }
                    }
                    if (smsLines.isNotEmpty()) {
                        SectionLabel(stringResource(R.string.teach_sms_section))
                        LineList(smsLines, maskCode = false, selected = pick?.takeIf { !it.fromOtp }?.index) { picked = false to it }
                    }
                    Text(
                        stringResource(R.string.teach_manual_link),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { typing = true }
                            .padding(vertical = 6.dp),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.take(40) },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.teach_manual_hint)) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // یاد گرفتن شکل پیامک فقط وقتی خطی انتخاب شده (اسمِ نوشته‌شده فقط برای همین تراکنش است)
                if (!typing && transaction.bank != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(jt.chip)
                            .toggleable(value = learnShape, role = Role.Checkbox, onValueChange = { learnShape = it })
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = learnShape, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.teach_remember, shortBankName(transaction.bank.name)),
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            color = colors.onSurface,
                        )
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.teach_cancel), color = jt.muted)
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val lesson = pick?.takeIf { !typing && learnShape }?.let { MerchantLesson(it.fromOtp, it.index) }
                            name?.let { onConfirm(it, lesson) }
                        },
                        enabled = name != null,
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    ) {
                        Text(
                            name?.let { stringResource(R.string.teach_confirm, it) } ?: stringResource(R.string.teach_confirm_empty),
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = JibitoTheme.colors.muted,
    )
}

/** فهرست خط‌ها با دکمه‌ی رادیویی؛ خط عددی کم‌رنگ و غیرفعال. در پیامک رمز، عددِ خطِ «رمز/کد» پوشیده می‌شود */
@Composable
private fun LineList(lines: List<String>, maskCode: Boolean, selected: Int?, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val jt = JibitoTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, jt.border, shape)) {
        lines.forEachIndexed { i, line ->
            val pickable = MerchantRules.isPickable(line)
            val on = i == selected
            val shown = if (maskCode && (line.contains("رمز") || line.contains("کد"))) line.replace(Regex("\\d"), "#") else line
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (on) colors.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
                    .selectable(selected = on, enabled = pickable, role = Role.RadioButton, onClick = { onSelect(i) })
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .border(2.dp, if (on) colors.primary else if (pickable) jt.border else Color.Transparent, CircleShape)
                        .padding(4.dp)
                        .background(if (on) colors.primary else Color.Transparent, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    shown,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(textDirection = TextDirection.Content),
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Black else FontWeight.Normal,
                    color = if (pickable) colors.onSurface else jt.faint,
                )
            }
            if (i < lines.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(jt.border))
        }
    }
}
