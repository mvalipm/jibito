package ir.jibito.app.ui.smslist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.jibito.app.R
import ir.jibito.app.ui.main.LocalBottomBarSpace
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.CategoryTree
import ir.jibito.app.domain.Transaction
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import ir.jibito.app.domain.TransferSuggestion

/**
 * بدنه‌ی فهرست تراکنش‌ها: در حال خواندن، خالی، یا فهرست روزبه‌روز با پیشنهاد انتقال بالای آن.
 * فیلتر، جست‌وجو و گروه‌بندی روزانه در ViewModel انجام شده؛ این‌جا فقط نمایش است.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TransactionListBody(
    visible: VisibleTransactions?,
    isSyncing: Boolean,
    onlyUncategorized: Boolean,
    searchActive: Boolean,
    /** متن جست‌وجو برای پررنگ کردن در ردیف‌ها (فقط وقتی جست‌وجو باز است) */
    highlight: String?,
    listState: LazyListState,
    transferSuggestion: TransferSuggestion?,
    transferCount: Int,
    onConfirmTransfer: (TransferSuggestion) -> Unit,
    onRejectTransfer: (TransferSuggestion) -> Unit,
    categories: List<Category>,
    byId: Map<Long, Category>,
    displayDepth: Int,
    hiddenRoots: Set<Long>,
    onOpen: (Transaction) -> Unit,
    onPick: (Transaction, Long) -> Unit,
) {
    val t = JibitoTheme.colors
    val list = visible?.list
    val groups = visible?.groups.orEmpty()
    when {
        list == null || (list.isEmpty() && isSyncing) -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(text = stringResource(R.string.list_loading), fontSize = 14.sp, color = t.muted)
            }
        }
        list.isEmpty() && onlyUncategorized && !searchActive -> AllCategorized()
        list.isEmpty() -> Box(
            Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.list_empty),
                fontSize = 15.sp,
                color = t.muted,
                textAlign = TextAlign.Center,
            )
        }
        else -> LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp + LocalBottomBarSpace.current),
        ) {
            // پیشنهاد «انتقال بین حساب‌های خودم»: یکی‌یکی و فشرده، بالای فهرست
            transferSuggestion?.let { suggestion ->
                item(key = "transfer-suggestion") {
                    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                        TransferSuggestionCard(
                            suggestion = suggestion,
                            total = transferCount,
                            onYes = { onConfirmTransfer(suggestion) },
                            onNo = { onRejectTransfer(suggestion) },
                        )
                    }
                }
            }
            // روزبه‌روز: سرتیتر چسبان «امروز ━━━ ۶۰۵ هزار» و تراکنش‌های آن روز
            val maxDay = groups.maxOfOrNull { it.spendRial }?.coerceAtLeast(1L) ?: 1L
            val spendDays = groups.filter { it.spendRial > 0 }
            val avgDay = if (spendDays.isEmpty()) 0L else spendDays.sumOf { it.spendRial } / spendDays.size
            groups.forEach { group ->
                stickyHeader(key = "day-${group.dayStartMillis}") {
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        DayHeader(
                            group,
                            fraction = group.spendRial.toFloat() / maxDay,
                            heavy = spendDays.size > 1 && group.spendRial > avgDay * 5 / 4,
                        )
                    }
                }
                items(group.items, key = { it.id }) { sms ->
                    // پیشنهاد اپ در عمق و با دسته‌های اصلی‌ای که کاربر در تنظیمات گذاشته («سوخت» ← «حمل‌ونقل»)
                    val suggestion = CategoryTree.suggestionAt(
                        sms.suggestedCategory, sms.transaction.type.code, categories, byId, displayDepth, hiddenRoots,
                    )
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        TransactionRow(
                            sms = sms,
                            tint = tintOf(sms, byId),
                            onClick = { onOpen(sms) },
                            onAcceptSuggestion = suggestion?.let { c -> { onPick(sms, c.id) } },
                            suggestionName = suggestion?.name,
                            highlight = highlight,
                        )
                    }
                }
            }
        }
    }
}
