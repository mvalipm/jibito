package ir.jibito.app.data.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountGroupingTest {

    private val mellat = 11
    private fun row(account: String?, balance: Long, date: Long, bank: Int = mellat) = AccountBalanceRow(bank, account, balance, date)
    private fun seen(account: String, date: Long, bank: Int = mellat) = KnownAccountRow(bank, account, date)

    /** تغییرها را مثل دیتابیس اعمال می‌کند (جایگزینی با کلید بانک+حساب) */
    private fun List<AccountLink>.apply(changes: List<AccountLink>): List<AccountLink> =
        (associateBy { it.bankId to it.account } + changes.associateBy { it.bankId to it.account }).values.toList()

    @Test
    fun `bank without account numbers keeps one balance like before`() {
        val b = AccountGrouping.balances(listOf(row(null, 5_000, 1), row(null, 7_000, 2)), emptyList())
        assertEquals(listOf(AccountBalance(mellat, null, null, 7_000, 2)), b)
    }

    @Test
    fun `two accounts in one bank both count`() {
        val b = AccountGrouping.balances(listOf(row("1234", 214_000_000, 10), row("5678", 125_000_000, 5)), emptyList())
        assertEquals(listOf("1234", "5678"), b.map { it.account })
        assertEquals(339_000_000L, b.sumOf { it.balanceRial })
    }

    @Test
    fun `sms without account number is ignored once the bank has numbers`() {
        val b = AccountGrouping.balances(listOf(row("1234", 100, 1), row(null, 999, 9)), emptyList())
        assertEquals(listOf(AccountBalance(mellat, "1234", null, 100, 1)), b)
    }

    @Test
    fun `question asks about two numbers of one bank until answered`() {
        val known = listOf(seen("1234", 10), seen("5678", 5))
        assertNull(AccountGrouping.question(listOf(seen("1234", 10)), emptyList()))
        val q = AccountGrouping.question(known, emptyList())!!
        assertEquals(AccountQuestion(mellat, "1234", "5678"), q)

        val separate = emptyList<AccountLink>().apply(AccountGrouping.answer(q, same = false, emptyList()))
        assertNull(AccountGrouping.question(known, separate))
        val same = emptyList<AccountLink>().apply(AccountGrouping.answer(q, same = true, emptyList()))
        assertNull(AccountGrouping.question(known, same))
    }

    @Test
    fun `answering same merges balances into the latest one`() {
        val q = AccountQuestion(mellat, "6104XXXX1234", "0101234")
        val links = AccountGrouping.answer(q, same = true, emptyList())
        val b = AccountGrouping.balances(listOf(row("6104XXXX1234", 300, 20), row("0101234", 100, 10)), links)
        assertEquals(1, b.size)
        assertEquals(300L, b.single().balanceRial)
        assertEquals("0101234", b.single().account) // نماینده‌ی گروه همان حساب قبلی است
    }

    @Test
    fun `third number is asked about after the first two are decided`() {
        val known = listOf(seen("1111", 30), seen("2222", 20), seen("3333", 10))
        var links = emptyList<AccountLink>()
        links = links.apply(AccountGrouping.answer(AccountGrouping.question(known, links)!!, same = false, links))
        val next = AccountGrouping.question(known, links)!!
        assertEquals("3333", next.account)
        links = links.apply(AccountGrouping.answer(next, same = true, links))
        assertNull(AccountGrouping.question(known, links))
        assertEquals(2, AccountGrouping.groups(known, emptyList(), links).size)
    }

    @Test
    fun `merge then split restores two accounts and keeps the name`() {
        val known = listOf(seen("1234", 10), seen("5678", 5))
        var links = emptyList<AccountLink>()
        links = links.apply(listOf(AccountGrouping.rename(mellat, "1234", "  حقوق  ", links)))
        links = links.apply(AccountGrouping.merge(mellat, "1234", "5678", known, links))
        val merged = AccountGrouping.groups(known, emptyList(), links)
        assertEquals(1, merged.size)
        assertEquals("حقوق", merged.single().name) // مقصد اسم نداشت ← اسم این حساب
        assertEquals(setOf("1234", "5678"), merged.single().members.toSet())

        links = links.apply(AccountGrouping.split(mellat, "1234", known, links))
        val split = AccountGrouping.groups(known, emptyList(), links)
        assertEquals(2, split.size)
        assertEquals(setOf("1234", "5678"), split.mapNotNull { it.account }.toSet())
    }

    @Test
    fun `splitting the representative keeps the rest of the group together with its name`() {
        val known = listOf(seen("1", 3), seen("2", 2), seen("3", 1))
        val links = listOf(
            AccountLink(mellat, "1", "1", "مشترک", true),
            AccountLink(mellat, "2", "1", null, true),
            AccountLink(mellat, "3", "1", null, true),
        )
        val after = links.apply(AccountGrouping.split(mellat, "1", known, links))
        val groups = AccountGrouping.groups(known, emptyList(), after)
        assertEquals(2, groups.size)
        val rest = groups.single { it.members.size == 2 }
        assertEquals("مشترک", rest.name)
        assertEquals(setOf("2", "3"), rest.members.toSet())
    }

    @Test
    fun `blank name goes back to bank and last digits`() {
        assertNull(AccountGrouping.rename(mellat, "1234", "   ", emptyList()).name)
        assertFalse(AccountGrouping.rename(mellat, "1234", "x", emptyList()).decided) // اسم گذاشتن جواب سؤال نیست
    }

    @Test
    fun `short number is the last four digits`() {
        assertEquals("1234", AccountGrouping.shortNumber("6104XXXX1234"))
        assertEquals("0123", AccountGrouping.shortNumber("IR12-0123"))
    }

    @Test
    fun `whole bank excluded before this feature stays excluded per account`() {
        val legacy = setOf(WalletExclusion.wholeBank(mellat))
        assertTrue(WalletExclusion.isExcluded(mellat, "1234", legacy))
        assertTrue(WalletExclusion.isExcluded(mellat, "5678", legacy))
        // روشن کردن یکی ← فقط همان روشن می‌شود
        val next = WalletExclusion.toggle(mellat, "1234", include = true, excluded = legacy, sameBankAccounts = listOf("1234", "5678"))
        assertFalse(WalletExclusion.isExcluded(mellat, "1234", next))
        assertTrue(WalletExclusion.isExcluded(mellat, "5678", next))
    }

    @Test
    fun `excluding one account leaves the other in the total`() {
        val next = WalletExclusion.toggle(mellat, "5678", include = false, excluded = emptySet(), sameBankAccounts = listOf("1234", "5678"))
        assertTrue(WalletExclusion.isExcluded(mellat, "5678", next))
        assertFalse(WalletExclusion.isExcluded(mellat, "1234", next))
    }
}
