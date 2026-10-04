package ir.jibito.app.data.parser

import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.review.ReviewDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست پارسرها با نمونه‌پیامک‌ها.
 * (شماره‌حساب/کارت‌ها ساختگی‌اند.)
 */
class TransactionParserTest {

    private val mellat = BankDirectory.banks.first { it.parserKey == "mellat" }
    private val pasargad = BankDirectory.banks.first { it.parserKey == "pasargad" && it.id == 12 }
    private val saderat = BankDirectory.banks.first { it.parserKey == "saderat" }
    private val blu = BankDirectory.banks.first { it.parserKey == "smart" }

    // ---------- پیامک‌هایی که نباید تراکنش حساب شوند (از عکس‌های کاربر) ----------

    @Test
    fun `کد فعال‌سازی ملت تراکنش نیست`() {
        val sms = "کد فعالسازی شما در دیما\ncode: 884716\nلطفاً این کد را با دیگران به اشتراک نگذارید.\nکد شما تا 19:35:24 معتبر است."
        assertNull(TransactionParser.parse(mellat, sms))
    }

    @Test
    fun `تبلیغ ملت تراکنش نیست`() {
        val sms = "مشتری گرامی\nسلام\nدر جشنواره کارتهای جدید و متنوع بانک ملت میتوانید کارت دلخواه خود را سفارش دهید.\nhttps://dima.bankmellat.ir"
        assertNull(TransactionParser.parse(mellat, sms))
    }

    @Test
    fun `خوش‌آمد ملت تراکنش نیست`() {
        val sms = "بانک ملت\nبه سامانه همراه بانک ملت خوش آمدید.\n18:31 - 1405/04/23"
        assertNull(TransactionParser.parse(mellat, sms))
    }

    @Test
    fun `رمز انتقال پاسارگاد تراکنش نیست با اینکه مبلغ دارد`() {
        val sms = "پاسارگاد\nانتقال به\n2051XXXXXX\nمبلغ:20,000,000\nرمز: 41926\n08:07:46"
        assertNull(TransactionParser.parse(pasargad, sms))
    }

    // ---------- تراکنش‌های واقعی ----------

    @Test
    fun `برداشت پاسارگاد با منفی بعد از عدد`() {
        val sms = "77XXXXXX\n8,150,000-\n14:44_04/23"
        val t = TransactionParser.parse(pasargad, sms)
        assertNotNull(t)
        assertEquals(FlowType.WITHDRAWAL, t!!.type)
        assertEquals(8_150_000L, t.amountRial)
    }

    @Test
    fun `واریز ملت قالب خط‌به‌خط`() {
        val sms = "بانک ملت\nواریز به 1234567890\nمبلغ:1,500,000\nموجودی:12,345,678\n0423-18:31"
        val t = TransactionParser.parse(mellat, sms)!!
        assertEquals(FlowType.DEPOSIT, t.type)
        assertEquals(1_500_000L, t.amountRial)
        assertEquals(12_345_678L, t.balanceRial)
    }

    @Test
    fun `برداشت ملت قالب جایگاهی`() {
        val sms = "بانک ملت\nخرید 1234567890\n2,500,000\nموجودی 9,000,000\n1405/04/23 18:31"
        val t = TransactionParser.parse(mellat, sms)!!
        assertEquals(FlowType.WITHDRAWAL, t.type)
        assertEquals(2_500_000L, t.amountRial)
        assertEquals(9_000_000L, t.balanceRial)
    }

    @Test
    fun `صادرات با پارسر هوشمند و ارقام فارسی`() {
        val sms = "بانک صادرات\nبرداشت:۲۵۰,۰۰۰-\nحساب:۰۱۰۱XXXX\nمانده:۱,۲۳۴,۵۶۷\n۰۴۲۳-۱۲:۳۰"
        val t = TransactionParser.parse(saderat, sms)!!
        assertEquals(FlowType.WITHDRAWAL, t.type)
        assertEquals(250_000L, t.amountRial)
        assertEquals(1_234_567L, t.balanceRial)
    }

    @Test
    fun `حروف عربی خاص هم خوانده می‌شوند`() {
        val sms = "ﻭﺍﺭﻳﺰ\nﻣﺒﻠﻎ:3,000,000\nﻣﺎﻧﺪﻩ:4,000,000"
        val t = TransactionParser.parse(saderat, sms)!!
        assertEquals(FlowType.DEPOSIT, t.type)
        assertEquals(3_000_000L, t.amountRial)
    }

    @Test
    fun `مبلغ تومانی به ریال تبدیل می‌شود`() {
        val sms = "بلو\nواریز +1,000,000 تومان\nموجودی 5,000,000 تومان"
        val t = TransactionParser.parse(blu, sms)!!
        assertEquals(FlowType.DEPOSIT, t.type)
        assertEquals(10_000_000L, t.amountRial)
        assertEquals(50_000_000L, t.balanceRial)
    }

    @Test
    fun `کد پیگیری داخل تراکنش باعث رد شدن نمی‌شود`() {
        val sms = "بانک ملت\nبرداشت از 1234567890\nمبلغ:700,000\nمانده:1,300,000\nکد پیگیری: 123456"
        val t = TransactionParser.parse(mellat, sms)!!
        assertEquals(FlowType.WITHDRAWAL, t.type)
        assertEquals(700_000L, t.amountRial)
    }

    @Test
    fun `ملت با مبلغ چسبیده به برداشت، مانده مبلغ خوانده نمی‌شود`() {
        // از گزارش کاربر: «مانده» به‌جای مبلغ خوانده می‌شد
        val sms = "حساب1234567890\nبرداشت150,000\nمانده2,345,000\n05/07/04-18:24"
        val t = TransactionParser.parse(mellat, sms)!!
        assertEquals(FlowType.WITHDRAWAL, t.type)
        assertEquals(150_000L, t.amountRial)
        assertEquals(2_345_000L, t.balanceRial)
    }

    @Test
    fun `ملت واریز با مبلغ در همان خط`() {
        val sms = "حساب1234567890\nواریز:3,000,000\nمانده5,000,000\n05/07/04-18:24"
        val t = TransactionParser.parse(mellat, sms)!!
        assertEquals(FlowType.DEPOSIT, t.type)
        assertEquals(3_000_000L, t.amountRial)
        assertEquals(5_000_000L, t.balanceRial)
    }

    // ---------- نوع نامعلوم: حدس «واریز» نه ----------

    @Test
    fun `بدون کلمه‌ی نوع و بدون علامت خودکار ثبت نمی‌شود و به صندوق بررسی می‌رود`() {
        val sms = "بانک نمونه\nمبلغ: 1,500,000 ریال\nمانده: 3,000,000 ریال"
        assertNull(SmartParser.parse(SmsTextNormalizer.normalize(sms)))
        assertNull(TransactionParser.parse(blu, sms))
        assertTrue(ReviewDetector.isCandidate(SmsTextNormalizer.normalize(sms), ReviewDetector.BANK_SENDER_BONUS))
    }

    @Test
    fun `بدون کلمه‌ی نوع ولی با علامت منفی برداشت است`() {
        val t = SmartParser.parse("1,500,000-\nمانده: 3,000,000")!!
        assertEquals(FlowType.WITHDRAWAL, t.type)
        assertEquals(1_500_000L, t.amountRial)
    }

    @Test
    fun `کلمه‌ی واریز هنوز واریز است`() {
        val t = SmartParser.parse("واریز به حساب شما\nمبلغ: 1,500,000\nمانده: 3,000,000")!!
        assertEquals(FlowType.DEPOSIT, t.type)
    }

    // ---------- سقف مبلغ نداریم ----------

    @Test
    fun `مبلغ بالای یک میلیارد تومان ثبت می‌شود`() {
        val t = TransactionParser.parse(blu, "برداشت از حساب\nمبلغ: 150,000,000,000 ریال\nمانده: 20,000,000,000 ریال")!!
        assertEquals(FlowType.WITHDRAWAL, t.type)
        assertEquals(150_000_000_000L, t.amountRial)
        assertEquals(20_000_000_000L, t.balanceRial)
    }

    @Test
    fun `مبلغ تومانی بزرگ به ریال درست تبدیل می‌شود`() {
        val t = TransactionParser.parse(blu, "واریز به حساب\nمبلغ: 5,000,000,000 تومان\nموجودی: 6,000,000,000 تومان")!!
        assertEquals(50_000_000_000L, t.amountRial)
        assertEquals(60_000_000_000L, t.balanceRial)
    }

    @Test
    fun `شماره کارت ۱۶ رقمی هیچ‌وقت مبلغ خوانده نمی‌شود`() {
        assertNull(digitsToLong("6037991234565678"))
    }
}
