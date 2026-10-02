package ir.jibito.app.data.linking

import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.data.parser.ParsedTransaction
import ir.jibito.app.data.parser.PurchaseOtp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseLinkerTest {

    private val mellat = 11
    private val saman = 15
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

    @Test
    fun `خروجی از جدید به قدیم مرتب است`() {
        val txs = listOf(withdraw(2, t0), deposit(3, t0 + 10 * min, amount = 5))
        val result = PurchaseLinker.link(txs, emptyList())
        assertEquals(3L, result[0].record.id)
    }
}
