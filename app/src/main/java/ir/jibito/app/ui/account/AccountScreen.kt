package ir.jibito.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.wallet.AccountRef
import ir.jibito.app.data.wallet.BalanceHistory
import ir.jibito.app.data.wallet.BalanceStats
import ir.jibito.app.data.wallet.DepositRhythm
import ir.jibito.app.ui.common.LocalHideAmounts
import ir.jibito.app.ui.common.amount
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.ui.reports.AccountRows
import ir.jibito.app.ui.reports.BalanceChart
import ir.jibito.app.ui.reports.ChartCard
import ir.jibito.app.ui.reports.EmptyNote
import ir.jibito.app.ui.reports.Note
import ir.jibito.app.ui.reports.SegmentedTabs
import ir.jibito.app.ui.reports.dateLabel
import ir.jibito.app.ui.smslist.DayHeader
import ir.jibito.app.ui.smslist.TransactionRow
import ir.jibito.app.ui.smslist.accountLabel
import ir.jibito.app.ui.smslist.bankColor
import ir.jibito.app.ui.smslist.groupByDay
import ir.jibito.app.ui.smslist.tintOf
import ir.jibito.app.ui.summary.SectionHeader
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali
import ir.jibito.app.util.Money

/** بیشتر از این تعداد تراکنش در صفحه‌ی جزئیات نشان داده نمی‌شود (بقیه در تب «تراکنش‌ها») */
private const val MAX_TRANSACTIONS = 60

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * صفحه‌ی جزئیات یک حساب (یا «همه‌ی حساب‌ها»)، از کارت «موجودی حساب‌ها» در تب «گزارش‌ها»:
 * روند موجودی در ۳۰ روز / ۳ ماه / ۱ سال اخیر با کف، سقف و تغییر، یک جمله از جیبی درباره‌ی ریتم بعد از واریز بزرگ،
 * و زیرش تراکنش‌های همان حساب (برای «همه‌ی حساب‌ها»: فهرست حساب‌ها).
 * وقتی مبلغ‌ها پنهان‌اند، نمودار و عددها پنهان‌اند و دلیلش گفته می‌شود.
 *
 * @param key کلید حساب (AccountRef.key)؛ هر چیز دیگر یعنی «همه‌ی حساب‌ها»
 * @param onOpenTransaction لمس یک تراکنش ← همان تراکنش در تب «تراکنش‌ها»
 * @param onOpenAccount لمس یک حساب در «همه‌ی حساب‌ها» ← جزئیات همان حساب
 */
@Composable
fun AccountScreen(
    key: String,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenAccount: (AccountRef) -> Unit,
) {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val ref = remember(key) { AccountRef.parse(key) }
    val viewModel: AccountViewModel = viewModel(
        key = "account-$key",
        factory = AccountViewModel.factory(ref, app.container.balanceRepository, app.container.transactionRepository),
    )
    val range by viewModel.range.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    AccountContent(
        ref = ref,
        range = range,
        onRange = viewModel::setRange,
        detail = detail,
        transactions = transactions,
        onBack = onBack,
        onOpenTransaction = onOpenTransaction,
        onOpenAccount = onOpenAccount,
    )
}

/** محتوای صفحه، جدا از ViewModel (برای اسکرین‌شات‌ها) */
@Composable
fun AccountContent(
    /** null یعنی «همه‌ی حساب‌ها» */
    ref: AccountRef?,
    range: AccountRange,
    onRange: (AccountRange) -> Unit,
    detail: AccountDetail?,
    transactions: AccountTransactions?,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenAccount: (AccountRef) -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    val t = JibitoTheme.colors
    val hidden = LocalHideAmounts.current

    val bank = ref?.let { BankDirectory.byId(it.bankId) }
    val title = when {
        ref == null -> stringResource(R.string.balance_title_all)
        bank != null -> accountLabel(bank.name, ref.account, detail?.account?.name)
        else -> stringResource(R.string.balance_title)
    }
    val lastSms = detail?.account?.lastSmsMillis

    LazyColumn(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 16.dp + LocalBottomBarSpace.current),
    ) {
        item(key = "header") {
            Header(
                title = title,
                subtitle = lastSms?.let { stringResource(R.string.account_last_sms, Jalali.dayTitle(it, now)) },
                dot = ref?.let { bankColor(it.bankId, t.dark) },
                onBack = onBack,
            )
        }
        item(key = "range") {
            SegmentedTabs(
                labels = listOf(
                    stringResource(R.string.account_range_month),
                    stringResource(R.string.account_range_quarter),
                    stringResource(R.string.account_range_year),
                ),
                selected = range.ordinal,
                onSelect = { onRange(AccountRange.entries[it]) },
            )
        }
        item(key = "chart") {
            ChartCard {
                val d = detail
                when {
                    hidden -> EmptyNote(stringResource(R.string.balance_hidden))
                    d == null -> Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    ref != null && d.account == null -> EmptyNote(stringResource(R.string.account_gone))
                    ref == null && d.accounts.isEmpty() -> EmptyNote(stringResource(R.string.balance_empty))
                    else -> {
                        BalanceChart(
                            series = d.series,
                            label = stringResource(R.string.balances_today),
                            lineColor = if (ref == null) t.btnBg else bankColor(ref.bankId, t.dark),
                        )
                        val stats = d.series.stats()
                        if (stats != null && d.series.hasTrend) StatsRow(stats)
                        d.rhythm?.let { RhythmNote(it) }
                        if (lastSms != null && BalanceHistory.isStale(lastSms, now)) {
                            Note(
                                stringResource(R.string.account_stale_note, ((now - lastSms) / DAY_MILLIS).toInt()).let(Jalali::toPersianDigits),
                                bg = t.amberTint,
                                fg = t.amberTintFg,
                            )
                        }
                    }
                }
            }
        }

        if (ref == null) {
            val accounts = detail?.accounts.orEmpty()
            if (accounts.isNotEmpty()) {
                item(key = "accounts-title") {
                    Column(Modifier.padding(top = 24.dp)) { SectionHeader(title = stringResource(R.string.account_accounts)) }
                }
                item(key = "accounts") {
                    Box(Modifier.padding(top = 10.dp)) {
                        ChartCard { AccountRows(accounts, onOpenAccount, now) }
                    }
                }
            }
        } else {
            val tx = transactions
            item(key = "tx-title") {
                Column(Modifier.padding(top = 24.dp)) { SectionHeader(title = stringResource(R.string.account_transactions)) }
            }
            when {
                tx == null -> item(key = "tx-loading") {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                tx.items.isEmpty() -> item(key = "tx-empty") {
                    Box(Modifier.padding(horizontal = 16.dp)) { EmptyNote(stringResource(R.string.account_no_transactions)) }
                }
                else -> {
                    val byId = tx.categories.associateBy { it.id }
                    val nonSpend = tx.categories.filterNot { it.countsAsSpend }.map { it.id }.toSet()
                    val groups = groupByDay(tx.items.take(MAX_TRANSACTIONS), nonSpend)
                    // مثل تب «تراکنش‌ها»: نوار هر روز نسبت به پرخرج‌ترین روز، و حاشیه‌ی دو طرف
                    val maxDay = groups.maxOfOrNull { it.spendRial }?.coerceAtLeast(1L) ?: 1L
                    val spendDays = groups.filter { it.spendRial > 0 }
                    val avgDay = if (spendDays.isEmpty()) 0L else spendDays.sumOf { it.spendRial } / spendDays.size
                    groups.forEach { group ->
                        item(key = "day-${group.dayStartMillis}") {
                            Box(Modifier.padding(horizontal = 20.dp)) {
                                DayHeader(
                                    group,
                                    now,
                                    fraction = group.spendRial.toFloat() / maxDay,
                                    heavy = spendDays.size > 1 && group.spendRial > avgDay * 5 / 4,
                                )
                            }
                        }
                        items(group.items, key = { "tx-${it.id}" }) { sms ->
                            Box(Modifier.padding(horizontal = 20.dp)) {
                                TransactionRow(sms = sms, tint = tintOf(sms, byId), onClick = { onOpenTransaction(sms.id) })
                            }
                        }
                    }
                    val more = tx.items.size - MAX_TRANSACTIONS
                    if (more > 0) {
                        item(key = "tx-more") {
                            Text(
                                Jalali.toPersianDigits(stringResource(R.string.account_more_transactions, more)),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                fontSize = 12.sp,
                                color = t.muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String?, dot: androidx.compose.ui.graphics.Color?, onBack: () -> Unit) {
    val t = JibitoTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(JibitoIcons.Back, contentDescription = stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.onBackground)
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = t.muted, maxLines = 1)
        }
        if (dot != null) Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
    }
}

/** سه عدد زیر نمودار: کف، سقف و تغییر در بازه */
@Composable
private fun StatsRow(stats: BalanceStats) {
    val t = JibitoTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(stringResource(R.string.account_min), amount(Money.compact(stats.minRial)), dateLabel(stats.minDay), t.amberTintFg, Modifier.weight(1f))
        StatTile(stringResource(R.string.account_max), amount(Money.compact(stats.maxRial)), dateLabel(stats.maxDay), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        val change = stats.changeRial
        StatTile(
            stringResource(R.string.account_change),
            // با فلش جا نمی‌شود: «۳۱٫۳ م»
            amount((if (change > 0) "▲ " else if (change < 0) "▼ " else "") + Money.short(kotlin.math.abs(change))),
            dateLabel(stats.startDay),
            if (change >= 0) t.income else t.amber,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, note: String, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val t = JibitoTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(t.chip)
            .border(1.dp, t.border, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 11.sp, color = t.muted, maxLines = 1)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = valueColor, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(note, fontSize = 10.sp, color = t.faint, maxLines = 1)
    }
}

/** «جیبی: بعد از واریز حقوق، ۹ روزه نصفش خرج شد» */
@Composable
private fun RhythmNote(r: DepositRhythm) {
    val t = JibitoTheme.colors
    val amountText = amount(Money.compact(r.amountRial))
    val day = dateLabel(Jalali.startOfDay(r.dateMillis))
    val text = when {
        r.halfSpentDays == 0 -> stringResource(R.string.account_rhythm_same_day, amountText, day)
        r.halfSpentDays != null -> stringResource(R.string.account_rhythm_half, amountText, day, r.halfSpentDays)
        r.daysSince > 0 -> stringResource(R.string.account_rhythm_kept, amountText, day, r.daysSince)
        else -> return
    }
    Note(Jalali.toPersianDigits(text), bg = t.sugBg, fg = t.sugFg)
}
