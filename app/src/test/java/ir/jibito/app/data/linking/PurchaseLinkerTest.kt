package ir.jibito.app.data.linking

import ir.jibito.app.data.bank.BankDirectory
import ir.jibito.app.data.parser.AccountExtractor
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.data.parser.PurchaseOtp
import ir.jibito.app.data.parser.SmsTextNormalizer
import ir.jibito.app.data.parser.TransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseLinkerTest {

    private val mellat = 11
    private val saman = 15
    private val melli = 1
    private val min = 60_000L
    private val t0 = 1_700_000_000_000L

    private fun otp(id: Long, at: Long, amount: Long? = 1_250_000, merchant: String? = "اسنپ", bank: Int = mellat) =
        OtpRecord(id, at, bank, PurchaseOtp(amount, merchant))

    private fun withdraw(id: Long, at: Long, amount: Long = 1_250_000, bank: Int = mellat) =
        TxRecord(id, at, bank, ParsedTransaction(FlowType.WITHDRAWAL, amount))

    private fun deposit(id: Long, at: Long, amount: Long = 1_250_000, bank: Int = mellat) =
        TxRecord(id, at, bank, ParsedTransaction(FlowType.DEPOSIT, amount))

    @Test
    fun `رمز دوم بدون برداشت هیچ چیزی ثبت نمی‌کند`() {
        val result = PurchaseLinker.link(emptyList(), listOf(otp(1, t0)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `برداشت تا ۳ دقیقه بعد از رمز دوم مقصد خرید را می‌گیرد`() {
        val result = PurchaseLinker.link(listOf(withdraw(2, t0 + 2 * min)), listOf(otp(1, t0)))
        assertEquals(1, result.size)
        assertEquals("اسنپ", result[0].merchant)
        assertFalse(result[0].isFailedPurchase)
    }

    @Test
    fun `برداشت بعد از ۳ دقیقه به رمز منقضی وصل نمی‌شود`() {
        val result = PurchaseLinker.link(listOf(withdraw(2, t0 + 4 * min)), listOf(otp(1, t0)))
        assertNull(result[0].merchant)
    }

    @Test
    fun `رمز منقضی و درخواست دوباره - برداشت به رمز آخر وصل می‌شود`() {
        val otps = listOf(otp(1, t0, merchant = "دیجی کالا"), otp(3, t0 + 4 * min, merchant = "اسنپ"))
        val result = PurchaseLinker.link(listOf(withdraw(4, t0 + 5 * min)), otps)
        assertEquals("اسنپ", result[0].merchant)
    }

    @Test
    fun `مبلغ متفاوت یا بانک متفاوت وصل نمی‌شود`() {
        val r1 = PurchaseLinker.link(listOf(withdraw(2, t0 + min, amount = 999_000)), listOf(otp(1, t0)))
        assertNull(r1[0].merchant)
        val r2 = PurchaseLinker.link(listOf(withdraw(2, t0 + min, bank = saman)), listOf(otp(1, t0)))
        assertNull(r2[0].merchant)
    }

    @Test
    fun `رمز بدون مبلغ به هیچ برداشتی وصل نمی‌شود`() {
        val result = PurchaseLinker.link(listOf(withdraw(2, t0 + min)), listOf(otp(1, t0, amount = null)))
        assertNull(result[0].merchant)
    }

    @Test
    fun `برداشت کمتر از مبلغ رمز، حتی یک ریال، وصل نمی‌شود`() {
        val result = PurchaseLinker.link(listOf(withdraw(2, t0 + min, amount = 1_249_999)), listOf(otp(1, t0)))
        assertNull(result[0].merchant)
    }

    @Test
    fun `برداشت کمی بیشتر از رمز (زیر ۲٪) = انتقال با کارمزد`() {
        val result = PurchaseLinker.link(listOf(withdraw(2, t0 + min, amount = 1_250_001)), listOf(otp(1, t0)))
        assertEquals("اسنپ", result[0].merchant)
        assertEquals(1L, result[0].feeRial)
    }

    @Test
    fun `مبلغ تومانی رمز با مبلغ ریالی برداشت برابر حساب می‌شود`() {
        // رمز: 125,000 تومان (= 1,250,000 ریال بعد از تبدیل در پارسر) ، برداشت: 1,250,000 ریال
        val result = PurchaseLinker.link(listOf(withdraw(2, t0 + min, amount = 1_250_000)), listOf(otp(1, t0, amount = 1_250_000)))
        assertEquals("اسنپ", result[0].merchant)
    }

    @Test
    fun `برگشت همان مبلغ تا ۳ دقیقه بعد یعنی خرید ناموفق`() {
        val txs = listOf(withdraw(2, t0 + min), deposit(3, t0 + 3 * min))
        val result = PurchaseLinker.link(txs, listOf(otp(1, t0)))
        assertEquals(1, result.size) // واریزِ برگشتی جدا نشان داده نمی‌شود
        assertTrue(result[0].isFailedPurchase)
        assertEquals(3L, result[0].refund!!.id)
    }

    @Test
    fun `واریز همان مبلغ بعد از ۳ دقیقه برگشت حساب نمی‌شود`() {
        val txs = listOf(withdraw(2, t0 + min), deposit(3, t0 + 5 * min))
        val result = PurchaseLinker.link(txs, listOf(otp(1, t0)))
        assertEquals(2, result.size)
        assertFalse(result.any { it.isFailedPurchase })
    }

    @Test
    fun `واریز با مبلغ متفاوت برگشت حساب نمی‌شود`() {
        val txs = listOf(withdraw(2, t0 + min), deposit(3, t0 + 2 * min, amount = 500_000))
        val result = PurchaseLinker.link(txs, listOf(otp(1, t0)))
        assertEquals(2, result.size)
        assertFalse(result.any { it.isFailedPurchase })
    }

    @Test
    fun `برداشت عادی بدون رمز دوم با واریز هم‌مبلغ قاطی نمی‌شود`() {
        val txs = listOf(withdraw(2, t0), deposit(3, t0 + min))
        val result = PurchaseLinker.link(txs, emptyList())
        assertEquals(2, result.size)
    }

    private fun tx(
        id: Long, at: Long, type: FlowType, amount: Long, balance: Long?,
        isCorrection: Boolean = false, bank: Int = melli, account: String? = null,
    ) = TxRecord(id, at, bank, ParsedTransaction(type, amount, balance), isCorrection, account)

    @Test
    fun `برداشت بدون رمز و واریز هم‌مبلغ روی همان حساب تا ۳ دقیقه ← پول برگشته`() {
        val txs = listOf(
            tx(1, t0, FlowType.WITHDRAWAL, 15_012_200, balance = 2_204_355),
            tx(2, t0 + min, FlowType.DEPOSIT, 15_012_200, balance = 17_216_555),
        )
        val result = PurchaseLinker.link(txs, emptyList())
        assertEquals(1, result.size)
        assertTrue(result[0].isFailedPurchase)
        assertEquals(2L, result[0].refund!!.id)
    }

    @Test
    fun `همان حساب ولی بعد از ۳ دقیقه، یا حساب دیگر، یا مانده‌ی نامعلوم ← برگشت نیست`() {
        val w = tx(1, t0, FlowType.WITHDRAWAL, 15_012_200, balance = 2_204_355)
        val late = tx(2, t0 + 4 * min, FlowType.DEPOSIT, 15_012_200, balance = 17_216_555)
        val otherAccount = tx(2, t0 + min, FlowType.DEPOSIT, 15_012_200, balance = 40_000_000)
        val noBalance = tx(2, t0 + min, FlowType.DEPOSIT, 15_012_200, balance = null)
        val otherBank = tx(2, t0 + min, FlowType.DEPOSIT, 15_012_200, balance = 17_216_555, bank = mellat)
        for (d in listOf(late, otherAccount, noBalance, otherBank)) {
            val result = PurchaseLinker.link(listOf(w, d), emptyList())
            assertEquals(d.toString(), 2, result.size)
            assertFalse(result.any { it.isFailedPurchase })
        }
    }

    @Test
    fun `اصلاحیه‌ی هم‌مبلغ به همان بانک تا ۷۲ ساعت ← پول برگشته، حتی با تراکنش‌های وسطش`() {
        val w = tx(1, t0, FlowType.WITHDRAWAL, 15_012_200, balance = 2_204_355)
        val fix = tx(2, t0 + 70 * 60 * min, FlowType.DEPOSIT, 15_012_200, balance = 5_000_000, isCorrection = true)
        val result = PurchaseLinker.link(listOf(w, fix), emptyList())
        assertEquals(1, result.size)
        assertEquals(2L, result[0].refund!!.id)

        val tooLate = fix.copy(timeMillis = t0 + 73 * 60 * min)
        assertEquals(2, PurchaseLinker.link(listOf(w, tooLate), emptyList()).size)
        val otherAmount = fix.copy(tx = fix.tx.copy(amountRial = 15_000_000))
        assertEquals(2, PurchaseLinker.link(listOf(w, otherAmount), emptyList()).size)
    }

    @Test
    fun `تشخیص اصلاحیه از متن، حتی با کشیده و ی عربی`() {
        assertTrue(PurchaseLinker.isCorrectionText(SmsTextNormalizer.normalize("بانك ملي ايران\nاصـلاحيه:15,012,200+")))
        assertTrue(PurchaseLinker.isCorrectionText("اصلاحیه:1,000+"))
        assertFalse(PurchaseLinker.isCorrectionText("واریز:1,000+"))
    }

    @Test
    fun `پیامک‌های واقعی ملی - انتقال و اصلاحیه‌ی همان دقیقه ← یک تراکنش ناموفق`() {
        val bank = BankDirectory.byId(melli)!!
        val bodies = listOf(
            "بانك ملي ايران\nانتقال:15,012,200-\nحساب:41007\nمانده:2,204,355\n0528-19:26",
            "بانك ملي ايران\nاصـلاحيه:15,012,200+\nحساب:41007\nمانده:17,216,555\n0528-19:26",
        )
        val txs = bodies.mapIndexed { i, body ->
            TxRecord(
                i + 1L, t0, melli, TransactionParser.parse(bank, body)!!,
                isCorrection = PurchaseLinker.isCorrectionText(SmsTextNormalizer.normalize(body)),
                account = AccountExtractor.find(SmsTextNormalizer.normalize(body)),
            )
        }
        val result = PurchaseLinker.link(txs, emptyList())
        assertEquals(1, result.size)
        assertEquals(1L, result[0].record.id)
        assertTrue(result[0].isFailedPurchase)
    }

    @Test
    fun `پیامک‌های واقعی ملی - دو برداشت و یک اصلاحیه ← اصلاحیه برگشتِ برداشتِ دوم است`() {
        val bank = BankDirectory.byId(melli)!!
        val bodies = listOf(
            "بانك ملي ايران\nبرداشت:1,000,000-\nحساب:41007\nمانده:21,814,555\n0524-19:00",
            "بانك ملي ايران\nبرداشت:1,000,000-\nحساب:41007\nمانده:20,814,555\n0524-19:01",
            "بانك ملي ايران\nاصـلاحيه:1,000,000+\nحساب:41007\nمانده:21,814,555\n0524-19:02",
        )
        val txs = bodies.mapIndexed { i, body ->
            TxRecord(
                i + 1L, t0 + i * min, melli, TransactionParser.parse(bank, body)!!,
                isCorrection = PurchaseLinker.isCorrectionText(SmsTextNormalizer.normalize(body)),
                account = AccountExtractor.find(SmsTextNormalizer.normalize(body)),
            )
        }
        val result = PurchaseLinker.link(txs, emptyList()).associateBy { it.record.id }
        assertEquals(setOf(1L, 2L), result.keys) // اصلاحیه جدا نشان داده نمی‌شود
        assertFalse(result.getValue(1).isFailedPurchase) // برداشت اول واقعاً خرج شده
        assertEquals(3L, result.getValue(2).refund!!.id)
    }

    @Test
    fun `چند برداشتِ ممکن بدون زنجیره‌ی مانده ← نزدیک‌ترین برداشت`() {
        val txs = listOf(
            tx(1, t0, FlowType.WITHDRAWAL, 1_000_000, balance = null),
            tx(2, t0 + min, FlowType.WITHDRAWAL, 1_000_000, balance = null),
            tx(3, t0 + 2 * min, FlowType.DEPOSIT, 1_000_000, balance = null, isCorrection = true),
        )
        val result = PurchaseLinker.link(txs, emptyList()).associateBy { it.record.id }
        assertFalse(result.getValue(1).isFailedPurchase)
        assertEquals(3L, result.getValue(2).refund!!.id)
    }

    @Test
    fun `اصلاحیه به حسابِ دیگرِ همان بانک ← برگشت نیست`() {
        val w = tx(1, t0, FlowType.WITHDRAWAL, 1_000_000, balance = null, account = "41007")
        val fix = tx(2, t0 + min, FlowType.DEPOSIT, 1_000_000, balance = null, isCorrection = true, account = "55555")
        val result = PurchaseLinker.link(listOf(w, fix), emptyList())
        assertEquals(2, result.size)
        assertFalse(result.any { it.isFailedPurchase })
    }

    @Test
    fun `چند برداشت از حساب‌های مختلف ← اصلاحیه مال برداشتِ همان شماره‌حساب`() {
        val txs = listOf(
            tx(1, t0, FlowType.WITHDRAWAL, 1_000_000, balance = null, account = "41007"),
            tx(2, t0 + min, FlowType.WITHDRAWAL, 1_000_000, balance = null, account = "99999"),
            tx(3, t0 + 2 * min, FlowType.DEPOSIT, 1_000_000, balance = null, isCorrection = true, account = "41007"),
        )
        val result = PurchaseLinker.link(txs, emptyList()).associateBy { it.record.id }
        assertEquals(3L, result.getValue(1).refund!!.id)
        assertFalse(result.getValue(2).isFailedPurchase)
    }

    @Test
    fun `خروجی از جدید به قدیم مرتب است`() {
        val txs = listOf(withdraw(2, t0), deposit(3, t0 + 10 * min, amount = 5))
        val result = PurchaseLinker.link(txs, emptyList())
        assertEquals(3L, result[0].record.id)
    }
}
