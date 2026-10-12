package ir.jibito.app.widget

import ir.jibito.app.data.wallet.AccountBalance
import ir.jibito.app.data.wallet.AccountLink
import ir.jibito.app.data.wallet.AccountUsageRow
import ir.jibito.app.data.wallet.BalancePointRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLinesTest {

    private val day = 24 * 60 * 60 * 1000L
    private val days = (0 until 7).map { 1_000 * day + it * day }
    private val todayStart = days.last()
    private val end = todayStart + day

    private fun balance(bankId: Int, account: String?, rial: Long, at: Long = todayStart + 1) =
        AccountBalance(bankId, account, null, rial, at)

    private var nextId = 1L
    private fun point(bankId: Int, account: String?, remain: Long, at: Long, withdrawal: Long) =
        BalancePointRow(nextId++, bankId, account, remain, at, flowType = 2, amount = withdrawal)

    private fun pick(
        balances: List<AccountBalance>,
        usage: List<AccountUsageRow> = emptyList(),
        points: List<BalancePointRow> = emptyList(),
        links: List<AccountLink> = emptyList(),
        excluded: Set<String> = emptySet(),
        limit: Int = 5,
    ) = CardLines.pick(balances, usage, points, links, excluded, days, end, limit)

    @Test
    fun mostUsedFirstThenNewest() {
        val lines = pick(
            balances = listOf(balance(11, "5678", 1), balance(15, "1209", 2), balance(40, "3341", 3, at = todayStart - 2 * day)),
            usage = listOf(AccountUsageRow(15, "1209", 20), AccountUsageRow(11, "5678", 4), AccountUsageRow(40, "3341", 4)),
        )
        assertEquals(listOf("1209", "5678", "3341"), lines.map { it.account })
    }

    @Test
    fun excludedAccountsAndLimit() {
        val lines = pick(
            balances = listOf(balance(11, "5678", 1), balance(15, "1209", 2), balance(40, "3341", 3)),
            excluded = setOf("15:1209"),
            limit = 1,
        )
        assertEquals(listOf("11:5678"), lines.map { it.key })
    }

    /** شماره‌هایی که کاربر گفته یکی‌اند، تراکنش‌هایشان روی هم شمرده می‌شود؛ بانک بی‌شماره همه‌ی تراکنش‌هایش را دارد */
    @Test
    fun usageFollowsAccountGroups() {
        val links = listOf(AccountLink(11, "9999", groupAccount = "5678", name = null, decided = true))
        val lines = pick(
            balances = listOf(balance(11, "5678", 1), balance(15, "1209", 2), balance(1, null, 3)),
            usage = listOf(
                AccountUsageRow(11, "5678", 3), AccountUsageRow(11, "9999", 5),
                AccountUsageRow(15, "1209", 7),
                AccountUsageRow(1, null, 6),
            ),
            links = links,
        )
        assertEquals(listOf("11:5678", "15:1209", "1:"), lines.map { it.key })
    }

    @Test
    fun trendCarriesLastBalanceOfEachDayAndTodayDelta() {
        val p1 = point(11, "5678", 900, days[2] + 1, withdrawal = 100) // پیش از آن: ۱۰۰۰
        val p2 = point(11, "5678", 700, days[4] + 1, withdrawal = 200)
        val p3 = point(11, "5678", 650, todayStart + 5, withdrawal = 50)
        val line = pick(listOf(balance(11, "5678", 650)), points = listOf(p1, p2, p3)).single()
        assertEquals(listOf(1_000L, 1_000, 900, 900, 700, 700, 650), line.trend)
        assertEquals(-50L, line.todayDeltaRial)
    }

    @Test
    fun noRecentSmsMeansFlatTrendAndNoDelta() {
        val line = pick(listOf(balance(15, "1209", 5_000, at = days.first() - 3 * day))).single()
        assertEquals(List(7) { 5_000L }, line.trend)
        assertNull(line.todayDeltaRial)
    }

    @Test
    fun staleAfterThirtyDays() {
        val now = todayStart + 10
        assertFalse(CardLines.isStale(pick(listOf(balance(1, null, 1, at = now - 29 * day))).single(), now))
        assertTrue(CardLines.isStale(pick(listOf(balance(1, null, 1, at = now - 31 * day))).single(), now))
    }
}
