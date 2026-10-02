package ir.jibito.app.data.parser

import ir.jibito.app.data.parser.FlowType.DEPOSIT
import ir.jibito.app.data.parser.FlowType.WITHDRAWAL
import org.junit.Assert.assertEquals
import org.junit.Test

class EventKindClassifierTest {

    private fun kind(sms: String, type: FlowType, otp: PurchaseOtp? = null) =
        EventKindClassifier.classify(SmsTextNormalizer.normalize(sms), type, otp)

    @Test
    fun `خودپرداز`() {
        assertEquals(EventKind.CASH_WITHDRAWAL, kind("بانک ملت\nبرداشت از خودپرداز\n2,000,000\nمانده 9,000,000", WITHDRAWAL))
        assertEquals(EventKind.CASH_WITHDRAWAL, kind("برداشت وجه نقد ATM\nمبلغ:1,000,000", WITHDRAWAL))
        assertEquals(EventKind.CASH_WITHDRAWAL, kind("برداشت از عابر‌بانک 500,000", WITHDRAWAL))
    }

    @Test
    fun `قبض`() {
        assertEquals(EventKind.BILL_PAYMENT, kind("پرداخت قبض برق\nمبلغ:450,000\nمانده:1,000,000", WITHDRAWAL))
    }

    @Test
    fun `انتقال حتی وقتی کارمزد هم دارد`() {
        assertEquals(EventKind.TRANSFER, kind("انتقال کارت به کارت\nمبلغ:10,000,000\nکارمزد:11,000", WITHDRAWAL))
        assertEquals(EventKind.TRANSFER, kind("واریز پایا\nمبلغ:3,000,000", DEPOSIT))
        assertEquals(EventKind.TRANSFER, kind("کارت‌به‌کارت 200,000", WITHDRAWAL))
    }

    @Test
    fun `خرید کارتی`() {
        assertEquals(EventKind.PURCHASE, kind("بانک مسکن\nخرید 450,000\nمانده 2,500,000", WITHDRAWAL))
        assertEquals(EventKind.PURCHASE, kind("برداشت پوز\n120,000", WITHDRAWAL))
    }

    @Test
    fun `کارمزد تنها`() {
        assertEquals(EventKind.FEE, kind("کسر کارمزد پیامک\nمبلغ:12,000\nمانده:900,000", WITHDRAWAL))
    }

    @Test
    fun `برگشت پول`() {
        assertEquals(EventKind.REFUND, kind("برگشت وجه تراکنش\nمبلغ:300,000\nمانده:1,300,000", DEPOSIT))
        assertEquals(EventKind.REFUND, kind("واریز بابت عودت خرید 300,000", DEPOSIT))
    }

    @Test
    fun `رمز دوم صریح‌ترین نشانه است`() {
        assertEquals(EventKind.TRANSFER, kind("برداشت 10,011,000", WITHDRAWAL, PurchaseOtp(10_000_000, null, isTransfer = true)))
        assertEquals(EventKind.PURCHASE, kind("برداشت 350,000", WITHDRAWAL, PurchaseOtp(350_000, "دیجی کالا")))
    }

    // ---- قانون‌های «حدس نزن» (سند EventTaxonomy)

    @Test
    fun `نوع ناسازگار با جهت نامعلوم می‌ماند`() {
        assertEquals(EventKind.UNKNOWN, kind("واریز خودپرداز 500,000", DEPOSIT))
        assertEquals(EventKind.UNKNOWN, kind("برگشت وجه\nبرداشت 300,000", WITHDRAWAL))
        assertEquals(EventKind.UNKNOWN, kind("واریز بابت خرید 300,000", DEPOSIT))
        assertEquals(EventKind.UNKNOWN, kind("واریز قبض 100,000", DEPOSIT))
    }

    @Test
    fun `کلمه‌ی کلی نوع نمی‌سازد`() {
        assertEquals(EventKind.UNKNOWN, kind("بانک ملت\nبرداشت\n2,000,000\nمانده 9,000,000", WITHDRAWAL))
        assertEquals(EventKind.UNKNOWN, kind("واریز\n5,000,000", DEPOSIT))
    }

    @Test
    fun `کلمه‌ی کامل، نه تکه‌ای از کلمه‌ی دیگر`() {
        // «پایان»، «شبانه» و «پوزش» نباید انتقال یا خرید شوند
        assertEquals(EventKind.UNKNOWN, kind("برداشت 100,000\nپایان اعتبار کارت 1406", WITHDRAWAL))
        assertEquals(EventKind.UNKNOWN, kind("برداشت 100,000\nخدمات شبانه روزی", WITHDRAWAL))
        assertEquals(EventKind.UNKNOWN, kind("برداشت 100,000\nپوزش بابت اختلال", WITHDRAWAL))
    }

    @Test
    fun `برگشت چک برگشت پول نیست`() {
        assertEquals(EventKind.UNKNOWN, kind("واریز 1,000,000\nبرگشت چک", DEPOSIT))
    }

    @Test
    fun `کدها پایدارند`() {
        // در دیتابیس ذخیره می‌شوند؛ عوض شدنشان داده‌ی کاربر را خراب می‌کند
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), EventKind.entries.map { it.code })
        assertEquals(EventKind.UNKNOWN, EventKind.of(99))
    }
}
