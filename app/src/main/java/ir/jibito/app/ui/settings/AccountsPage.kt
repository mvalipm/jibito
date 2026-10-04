package ir.jibito.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.wallet.AccountGroup
import ir.jibito.app.data.wallet.AccountGrouping
import ir.jibito.app.domain.BankBalance
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.smslist.accountLabel
import ir.jibito.app.ui.smslist.isExcluded
import ir.jibito.app.ui.smslist.shortBankName
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money
import kotlinx.coroutines.launch

/**
 * «حساب‌های من»: همه‌ی حساب‌ها بانک‌به‌بانک، با اسم، شماره‌ها و آخرین مانده.
 * این‌جا کاربر هر وقت بخواهد تصمیمش را عوض می‌کند: اسم، یکی کردن دو حساب، جدا کردن، و آمدن در جمع موجودی.
 * (تصمیم‌ها قانون جدا هستند و به تراکنش‌ها دست نمی‌زنند؛ همیشه برگشت‌پذیر.)
 */
@Composable
internal fun AccountsPageContent() {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val accounts = app.container.accountRepository
    val wallet = app.container.walletSettings
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val groupsFlow = remember { accounts.observeGroups() }
    val groups by groupsFlow.collectAsState(initial = emptyList())
    val excluded by wallet.excluded.collectAsState()
    var renaming by remember { mutableStateOf<AccountGroup?>(null) }
    var merging by remember { mutableStateOf<AccountGroup?>(null) }

    // همان شکل کارت کیف پول (برای جمع موجودی)
    val balances = groups.mapNotNull { g -> g.toBalance() }

    PageCard {
        Text(
            stringResource(if (groups.isEmpty()) R.string.accounts_empty else R.string.accounts_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
    groups.groupBy { it.bankId }.forEach { (bankId, bankGroups) ->
        val bank = BankDirectory.byId(bankId) ?: return@forEach
        Spacer(Modifier.height(12.dp))
        PageCard {
            Text(shortBankName(bank.name), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = JibitoTheme.colors.muted)
            bankGroups.forEach { g ->
                val label = accountLabel(bank.name, g.account, g.name)
                val balance = g.toBalance()
                val out = balance != null && isExcluded(balance, excluded)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).alpha(if (out) 0.5f else 1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Text(
                            if (g.account == null) {
                                stringResource(R.string.accounts_no_number)
                            } else {
                                Jalali.toPersianDigits(
                                    stringResource(R.string.accounts_numbers, g.members.joinToString("، ") { AccountGrouping.shortNumber(it) })
                                )
                            },
                            fontSize = 12.sp,
                            color = JibitoTheme.colors.muted,
                        )
                    }
                    Text(
                        if (g.balanceRial != null) amount(Money.short(g.balanceRial)) else stringResource(R.string.accounts_no_balance),
                        fontSize = 13.sp,
                        fontWeight = if (g.balanceRial != null) FontWeight.Black else FontWeight.Normal,
                        color = if (g.balanceRial != null) colors.onSurface else JibitoTheme.colors.muted,
                        textDecoration = if (out) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(DesignIcons.Dots, contentDescription = stringResource(R.string.accounts_more, label), tint = JibitoTheme.colors.muted)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (g.account != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.accounts_rename)) },
                                    onClick = { menuOpen = false; renaming = g },
                                )
                                if (bankGroups.count { it.account != null } > 1) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.accounts_merge)) },
                                        onClick = { menuOpen = false; merging = g },
                                    )
                                }
                                if (g.members.size > 1) {
                                    g.members.forEach { member ->
                                        DropdownMenuItem(
                                            text = { Text(Jalali.toPersianDigits(stringResource(R.string.accounts_split, AccountGrouping.shortNumber(member)))) },
                                            onClick = {
                                                menuOpen = false
                                                scope.launch { accounts.split(bankId, member) }
                                            },
                                        )
                                    }
                                }
                            }
                            if (balance != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (out) R.string.accounts_include else R.string.accounts_exclude)) },
                                    onClick = {
                                        menuOpen = false
                                        wallet.setIncluded(balance, out, balances)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { g ->
        val bankName = BankDirectory.byId(g.bankId)?.name.orEmpty()
        AccountNameDialog(
            title = stringResource(R.string.accounts_rename_title, accountLabel(bankName, g.account, null)),
            initial = g.name.orEmpty(),
            onSave = { name ->
                g.account?.let { rep -> scope.launch { accounts.rename(g.bankId, rep, name) } }
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    merging?.let { g ->
        val bankName = BankDirectory.byId(g.bankId)?.name.orEmpty()
        val targets = groups.filter { it.bankId == g.bankId && it.account != null && it.account != g.account }
        AlertDialog(
            onDismissRequest = { merging = null },
            title = { Text(stringResource(R.string.accounts_merge_title, accountLabel(bankName, g.account, g.name)), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    targets.forEach { target ->
                        TextButton(
                            onClick = {
                                val from = g.account
                                val into = target.account
                                if (from != null && into != null) scope.launch { accounts.merge(g.bankId, from, into) }
                                merging = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(accountLabel(bankName, target.account, target.name)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { merging = null }) { Text(stringResource(R.string.budget_dialog_cancel)) } },
        )
    }
}

/** گروهی که مانده دارد، به شکل کپسول کارت کیف پول */
private fun AccountGroup.toBalance(): BankBalance? {
    val bank = BankDirectory.byId(bankId) ?: return null
    val rial = balanceRial ?: return null
    return BankBalance(bank, rial, dateMillis ?: 0L, account, name)
}

@Composable
private fun AccountNameDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(AccountGrouping.MAX_NAME) },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.accounts_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.settings_save), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.budget_dialog_cancel)) } },
    )
}

/** برای ردیف تنظیمات: چند حساب در چند بانک */
internal fun accountsSummary(groups: List<AccountGroup>): Pair<Int, Int> =
    groups.size to groups.map { it.bankId }.distinct().size
