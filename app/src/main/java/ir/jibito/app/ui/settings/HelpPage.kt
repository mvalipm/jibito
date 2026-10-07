package ir.jibito.app.ui.settings

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.ui.common.TipStore
import ir.jibito.app.ui.common.openSupportChat
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali

/** یک پرسش رایج و جوابش */
internal data class HelpQuestion(val question: String, val answer: String)

/** «راهنما»: پرسش‌های رایج (با لمس باز می‌شوند)، دوباره دیدن نکته‌ها، و پشتیبانی اگر جواب این‌جا نبود */
@Composable
internal fun HelpPageContent() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val store = remember { TipStore(context.applicationContext) }
    var tipsReset by rememberSaveable { mutableStateOf(false) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    HelpList(
        questions = helpQuestions(),
        tipsReset = tipsReset,
        onResetTips = {
            store.resetAll()
            tipsReset = true
        },
        onAsk = {
            openSupportChat(
                context,
                text = resources.getString(R.string.settings_feedback_body, version, Build.VERSION.RELEASE),
                copiedHint = resources.getString(R.string.settings_feedback_copied),
                chooserTitle = resources.getString(R.string.help_ask),
                subject = resources.getString(R.string.help_ask_subject),
            )
        },
    )
}

@Composable
internal fun helpQuestions(): List<HelpQuestion> {
    val banks = BankDirectory.banks.map { it.name.removePrefix("بانک ").trim() }
    return listOf(
        HelpQuestion(stringResource(R.string.help_q_missing), stringResource(R.string.help_a_missing)),
        HelpQuestion(
            stringResource(R.string.help_q_banks),
            Jalali.toPersianDigits(stringResource(R.string.help_a_banks, banks.size, banks.joinToString("، "))),
        ),
        HelpQuestion(stringResource(R.string.help_q_data), stringResource(R.string.help_a_data)),
        HelpQuestion(stringResource(R.string.help_q_learn), stringResource(R.string.help_a_learn)),
        HelpQuestion(stringResource(R.string.help_q_transfer), stringResource(R.string.help_a_transfer)),
        HelpQuestion(stringResource(R.string.help_q_one_off), stringResource(R.string.help_a_one_off)),
    )
}

/** محتوای صفحه، جدا از اپ (برای اسکرین‌شات). [initiallyOpen]: پرسشی که از اول باز است (-۱ یعنی هیچ‌کدام) */
@Composable
internal fun HelpList(
    questions: List<HelpQuestion>,
    tipsReset: Boolean,
    onResetTips: () -> Unit,
    onAsk: () -> Unit,
    initiallyOpen: Int = -1,
) {
    val colors = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableIntStateOf(initiallyOpen) }
    PageCard {
        questions.forEachIndexed { i, q ->
            if (i > 0) HorizontalDivider(color = JibitoTheme.colors.border)
            QuestionRow(q, expanded = open == i, onToggle = { open = if (open == i) -1 else i })
        }
    }
    Spacer(Modifier.height(14.dp))
    PageCard {
        Text(
            stringResource(if (tipsReset) R.string.help_tips_reset_done else R.string.help_tips_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onResetTips, enabled = !tipsReset, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.help_tips_reset), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAsk, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.help_ask), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuestionRow(q: HelpQuestion, expanded: Boolean, onToggle: () -> Unit) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val openText = stringResource(R.string.help_open)
    val closedText = stringResource(R.string.help_closed)
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) openText else closedText }
            .padding(vertical = 12.dp),
    ) {
        Row(Modifier.heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                q.question,
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                JibitoIcons.ChevronDown,
                contentDescription = null,
                tint = t.faint,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(q.answer, fontSize = 14.sp, lineHeight = 24.sp, color = t.muted)
        }
    }
}
