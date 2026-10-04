package ir.jibito.app.data.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsArchiveMergeTest {

    private fun a(id: Long, date: Long, body: String) = ArchivedSms(id, "Bank Mellat", 1, body, date)

    @Test
    fun `پیامکی که هنوز در صندوق هست از کپی خوانده نمی‌شود`() {
        val archived = listOf(a(1, 1_000, "A"), a(2, 2_000, "B"))
        val left = SmsArchiveMerge.notInInbox(archived, mapOf("A" to listOf(1_000L)))
        assertEquals(listOf(2L), left.map { it.archiveId })
    }

    @Test
    fun `همه از گوشی پاک شده‌اند ← همه از کپی`() {
        val archived = listOf(a(1, 1_000, "A"), a(2, 2_000, "B"))
        assertEquals(listOf(1L, 2L), SmsArchiveMerge.notInInbox(archived, emptyMap()).map { it.archiveId })
    }

    @Test
    fun `زمان جابه‌جا شده در گوشی تازه ← باز هم همان پیامک حساب می‌شود`() {
        val archived = listOf(a(1, 1_000, "A"))
        assertEquals(emptyList<Long>(), SmsArchiveMerge.notInInbox(archived, mapOf("A" to listOf(1_000L + 3_600_000))).map { it.archiveId })
    }

    @Test
    fun `دو پیامک کاملاً یکسان که یکی پاک شده ← آن یکی از کپی`() {
        val archived = listOf(a(1, 1_000, "same"), a(2, 5_000, "same"))
        val left = SmsArchiveMerge.notInInbox(archived, mapOf("same" to listOf(5_000L)))
        // پیامک صندوق کپیِ هم‌زمانِ خودش را می‌پوشاند، نه اولی را
        assertEquals(listOf(1L), left.map { it.archiveId })
    }
}
