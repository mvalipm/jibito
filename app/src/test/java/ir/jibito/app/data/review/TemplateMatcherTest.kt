package ir.jibito.app.data.review

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.SmsTextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateMatcherTest {

    private fun n(s: String) = SmsTextNormalizer.normalize(s)

    /** کاربر پیامک اول را تأیید می‌کند ← قالب یاد گرفته می‌شود */
    private fun learnFrom(sms: String, amountValue: Long, balanceValue: Long?, type: FlowType): LearnedTemplate {
        val text = n(sms)
        val nums = ReviewDetector.numbers(text)
        return TemplateMatcher.learn(
            text,
            nums.first { it.value == amountValue },
            balanceValue?.let { b -> nums.first { it.value == b } },
            type,
        )
    }

    private val first = "بانک ایکس\nکسر از حساب 1111222233\n350,000 ریال\nمانده 1,200,000\n1405/07/09 12:30"

    @Test
    fun `پیامک هم‌قالب بعدی خودکار خوانده می‌شود`() {
        val t = learnFrom(first, 350_000, 1_200_000, FlowType.WITHDRAWAL)
        val next = n("بانک ایکس\nکسر از حساب 1111222233\n2,750,000 ریال\nمانده 9,450,000\n1405/07/12 08:05")
        val tx = TemplateMatcher.match(next, listOf(t))
        assertNotNull(tx)
        assertEquals(FlowType.WITHDRAWAL, tx!!.type)
        assertEquals(2_750_000L, tx.amountRial)
        assertEquals(9_450_000L, tx.balanceRial)
    }

    @Test
    fun `قالب با علامت - نوع از روی علامت`() {
        val t = learnFrom("77XXXXXX\n8,150,000-\n14:44", 8_150_000, null, FlowType.WITHDRAWAL)
        assertEquals(LearnedTemplate.TYPE_BY_SIGN, t.typeMode)
        val deposit = TemplateMatcher.match(n("77XXXXXX\n2,000,000+\n09:10"), listOf(t))
        assertEquals(FlowType.DEPOSIT, deposit!!.type)
        assertEquals(2_000_000L, deposit.amountRial)
    }

    @Test
    fun `پیامک با قالب متفاوت جور نمی‌شود`() {
        val t = learnFrom(first, 350_000, 1_200_000, FlowType.WITHDRAWAL)
        assertNull(TemplateMatcher.match(n("کد تخفیف ویژه 50 درصد برای شما\nتا 1405/08/01"), listOf(t)))
    }

    @Test
    fun `تومانی به ریال تبدیل می‌شود`() {
        val t = learnFrom("حساب شما به مبلغ 5,511,800 تومان شارژ شد", 5_511_800, null, FlowType.DEPOSIT)
        val tx = TemplateMatcher.match(n("حساب شما به مبلغ 100,000 تومان شارژ شد"), listOf(t))
        assertEquals(FlowType.DEPOSIT, tx!!.type)
        assertEquals(1_000_000L, tx.amountRial)
    }
}
