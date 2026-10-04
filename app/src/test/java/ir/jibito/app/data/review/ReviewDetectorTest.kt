package ir.jibito.app.data.review

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.SmsTextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewDetectorTest {

    private fun n(s: String) = SmsTextNormalizer.normalize(s)

    @Test
    fun `پیامک شارژ کیف پول - شبیه تراکنش است`() {
        val sms = n("کاربر عزیز\nحساب شما به مبلغ 5,511,800 تومان شارژ شد؛ برای تکمیل خرید وارد سایت شوید")
        assertTrue(ReviewDetector.isCandidate(sms))
        val g = ReviewDetector.guess(sms)
        assertEquals(5_511_800L, g.numbers[g.amountIndex!!].value)
        assertEquals(FlowType.DEPOSIT, g.type)
        assertTrue(g.inToman)
    }

    @Test
    fun `قالب ناشناخته با مبلغ و مانده`() {
        val sms = n("بانک ایکس\nکسر از حساب 1234567890\n۳۵۰,۰۰۰ ریال\nمانده ۱,۲۰۰,۰۰۰\n۱۴۰۵/۰۷/۰۹ ۱۲:۳۰")
        assertTrue(ReviewDetector.isCandidate(sms))
        val g = ReviewDetector.guess(sms)
        // شماره حساب ۱۰ رقمی، تاریخ و ساعت جزو عددها نیستند
        assertEquals(listOf(1_234_567_890L, 350_000L, 1_200_000L).filter { it != 1_234_567_890L }, g.numbers.map { it.value }.filter { it != 1_234_567_890L })
        assertEquals(350_000L, g.numbers[g.amountIndex!!].value)
        assertEquals(1_200_000L, g.numbers[g.balanceIndex!!].value)
        assertEquals(FlowType.WITHDRAWAL, g.type)
    }

    @Test
    fun `منفی کنار عدد یعنی برداشت`() {
        val g = ReviewDetector.guess(n("انتقال\n8,150,000-\n14:44"))
        assertEquals(FlowType.WITHDRAWAL, g.type)
        assertEquals(8_150_000L, g.numbers[g.amountIndex!!].value)
    }

    @Test
    fun `رمز و تبلیغ و پیامک بدون کلمه‌ی مالی وارد صندوق نمی‌شوند`() {
        assertFalse(ReviewDetector.isCandidate(n("رمز پویا: 482915\nمبلغ: 1,250,000")))
        assertFalse(ReviewDetector.isCandidate(n("جشنواره خرید با 500,000 تومان جایزه\nhttps://x.ir")))
        assertFalse(ReviewDetector.isCandidate(n("سلام، فردا ساعت 10 جلسه داریم")))
        assertFalse(ReviewDetector.isCandidate(n("بدهی پیشین: 149532 ریال\nقابل پرداخت: 278000 ریال\nhttps://my.mci.ir/bill")))
    }

    @Test
    fun `امتیاز وزن‌دار - کلمه‌ی ضعیف تنها کافی نیست`() {
        // فقط «خرید» و یک عدد: امتیاز کم ← به صندوق نمی‌رود
        assertFalse(ReviewDetector.isCandidate(n("سفارش خرید شما شماره 12345 ثبت شد")))
        // همان متن از فرستنده‌ای که قبلاً رمز پویا فرستاده هم هنوز کم است
        assertFalse(ReviewDetector.isCandidate(n("سفارش خرید شما شماره 12345 ثبت شد"), ReviewDetector.OTP_SENDER_BONUS))
    }

    @Test
    fun `امتیاز وزن‌دار - کارت ماسک‌شده و مبلغ علامت‌دار و تاریخ`() {
        val sms = n("کارت 6037***1234\n1,500,000-\n1405/07/09\n12:30")
        // کارت(۱) + سه‌رقمی(۱) + علامت(۲) + ماسک(۲) + تاریخ(۱) + ساعت(۱)
        assertTrue(ReviewDetector.contentScore(sms) >= ReviewDetector.REVIEW_THRESHOLD)
        assertTrue(ReviewDetector.isCandidate(sms))
    }

    @Test
    fun `امتیاز وزن‌دار - نشانه‌ی فرستنده پیامک بی‌عدد را نجات نمی‌دهد`() {
        assertEquals(0, ReviewDetector.score(n("واریز حقوق به زودی انجام می‌شود"), ReviewDetector.BANK_SENDER_BONUS))
        assertEquals(0, ReviewDetector.score(n("رمز پویا: 482915 مبلغ 1,250,000 ریال"), ReviewDetector.OTP_SENDER_BONUS))
    }

    @Test
    fun `امتیاز وزن‌دار - مرزی با نشانه‌ی فرستنده بالا می‌رود`() {
        // انتقال(۱) + ریال(۱) + سه‌رقمی(۱) = ۳
        val sms = n("انتقال 2,000,000 ریال انجام شد")
        assertEquals(3, ReviewDetector.contentScore(sms))
        assertFalse(ReviewDetector.isCandidate(sms))
        assertTrue(ReviewDetector.isCandidate(sms, ReviewDetector.BANK_SENDER_BONUS))
    }

    @Test
    fun `سرشماره‌ی خدماتی`() {
        assertTrue(ReviewDetector.isServiceNumber("10001234"))
        assertTrue(ReviewDetector.isServiceNumber("3000456789"))
        assertFalse(ReviewDetector.isServiceNumber("9121234567"))
        assertFalse(ReviewDetector.isServiceNumber("bankmellat"))
        assertFalse(ReviewDetector.isServiceNumber("1000"))
    }

    @Test
    fun `پوشاندن همه‌ی رقم‌ها برای ارسال`() {
        assertEquals("مبلغ: #,###,### کارت ####", ReviewDetector.mask("مبلغ: 1,250,000 کارت 6037"))
    }

    @Test
    fun `جای عددها در متن اصلی پیامک، با رقم فارسی`() {
        val body = "برداشت ۲٬۵۰۰٬۰۰۰ از ۱۲۳۴\nمانده: ۴۱٬۲۳۰٬۰۰۰"
        val g = ReviewDetector.guess(n(body))
        val ranges = ReviewDetector.locate(body, g.numbers)!!
        assertEquals(g.numbers.size, ranges.size)
        assertEquals("۲٬۵۰۰٬۰۰۰", body.substring(ranges.first().first, ranges.first().last + 1))
        assertEquals("۴۱٬۲۳۰٬۰۰۰", body.substring(ranges.last().first, ranges.last().last + 1))
    }

    @Test
    fun `تکه‌ای از عدد بلندتر حساب نمی‌شود و عدد ناپیدا یعنی null`() {
        val body = "کد 1250000 مبلغ 250000"
        val ranges = ReviewDetector.locate(body, listOf(NumberToken(250000, "250000")))!!
        assertEquals(body.lastIndexOf("250000"), ranges.single().first)
        assertEquals(null, ReviewDetector.locate("بدون عدد", listOf(NumberToken(5, "500"))))
    }
}
