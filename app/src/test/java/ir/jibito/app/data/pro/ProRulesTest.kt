package ir.jibito.app.data.pro

import ir.jibito.app.ui.theme.AppThemeStyle
import ir.jibito.app.ui.theme.shownWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** «جیبیتوی کامل»: ماه رایگان، خرید، و پوسته‌ای که بعد از ماه رایگان دیده می‌شود */
class ProRulesTest {

    private val start = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `روز اول کل ماه رایگان مانده`() {
        assertEquals(ProStatus.Trial(30), ProRules.status(owned = false, trialStartedAt = start, now = start))
        assertEquals(ProStatus.Trial(30), ProRules.status(owned = false, trialStartedAt = start, now = start + day - 1))
        assertEquals(ProStatus.Trial(29), ProRules.status(owned = false, trialStartedAt = start, now = start + day))
    }

    @Test
    fun `تا آخرین لحظه یک روز مانده و بعدش بسته است`() {
        val end = start + ProRules.TRIAL_DAYS * day
        assertEquals(ProStatus.Trial(1), ProRules.status(owned = false, trialStartedAt = start, now = end - 1))
        assertEquals(ProStatus.Free, ProRules.status(owned = false, trialStartedAt = start, now = end))
        assertEquals(ProStatus.Free, ProRules.status(owned = false, trialStartedAt = start, now = end + 400 * day))
    }

    @Test
    fun `خریده همیشه باز است، حتی وسط ماه رایگان`() {
        assertEquals(ProStatus.Owned, ProRules.status(owned = true, trialStartedAt = start, now = start))
        assertEquals(ProStatus.Owned, ProRules.status(owned = true, trialStartedAt = start, now = start + 400 * day))
    }

    @Test
    fun `ساعت گوشی عقب رفته ماه رایگان را بیشتر از یک ماه نمی‌کند`() {
        assertEquals(ProStatus.Trial(ProRules.TRIAL_DAYS), ProRules.status(owned = false, trialStartedAt = start, now = start - 90 * day))
    }

    @Test
    fun `نوتیف دعوت روزی یک بار، و بعد از دیدن «ماه رایگانت تموم شد» هفته‌ای یک بار`() {
        assertTrue(ProRules.notifyInviteDue(lastInviteAt = 0L, endNoticeSeen = false, now = start))
        assertFalse(ProRules.notifyInviteDue(lastInviteAt = start, endNoticeSeen = false, now = start + day - 1))
        assertTrue(ProRules.notifyInviteDue(lastInviteAt = start, endNoticeSeen = false, now = start + day))
        assertFalse(ProRules.notifyInviteDue(lastInviteAt = start, endNoticeSeen = true, now = start + 6 * day))
        assertTrue(ProRules.notifyInviteDue(lastInviteAt = start, endNoticeSeen = true, now = start + 7 * day))
    }

    @Test
    fun `فقط بعد از ماه رایگان و بدون خرید بسته است`() {
        assertTrue(ProStatus.Owned.unlocked)
        assertTrue(ProStatus.Trial(3).unlocked)
        assertFalse(ProStatus.Free.unlocked)
    }

    @Test
    fun `پوسته‌ی رنگی بعد از ماه رایگان پیش‌فرض دیده می‌شود و سه پوسته‌ی اول همیشه خودشان`() {
        assertEquals(AppThemeStyle.DEFAULT, AppThemeStyle.MATCHA.shownWith(proUnlocked = false))
        assertEquals(AppThemeStyle.MATCHA, AppThemeStyle.MATCHA.shownWith(proUnlocked = true))
        for (free in listOf(AppThemeStyle.DEFAULT, AppThemeStyle.WARM, AppThemeStyle.COOL)) {
            assertFalse(free.pro)
            assertEquals(free, free.shownWith(proUnlocked = false))
        }
        assertEquals(4, AppThemeStyle.entries.count { it.pro })
    }
}
