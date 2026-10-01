package ir.jibito.app.data.parser

import ir.jibito.app.data.bank.BankDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * هر پارسر اختصاصی با یک نمونه‌ی ساختگی در همان قالبی که کد اپ قدیمی انتظار داشت.
 * (هدف: مطمئن شویم برگردان Kotlin همان منطق را دارد. نمونه‌های واقعی کاربرها بعداً اضافه می‌شوند.)
 */
class AllBankParsersTest {

    private fun check(parser: SmsParser, sms: String, type: FlowType, amount: Long, balance: Long? = null) {
        val t = parser.parse(SmsTextNormalizer.normalize(sms))
        assertNotNull("پارسر چیزی برنگرداند:\n$sms", t)
        assertEquals(type, t!!.type)
        assertEquals(amount, t.amountRial)
        if (balance != null) assertEquals(balance, t.balanceRial)
    }

    @Test
    fun `همه‌ی parserKey های فهرست بانک‌ها پارسر دارند`() {
        val missing = BankDirectory.banks.map { it.parserKey }.toSet() - TransactionParser.bankParsers.keys - setOf("smart")
        assertTrue("بدون پارسر: $missing", missing.isEmpty())
    }

    @Test fun `ملی - قالب علامت‌دار`() =
        check(MelliParser, "بانک ملی\nحساب:0101XXXX\nبرداشت:1,500,000-\nمانده:9,000,000\n0709-12:30", FlowType.WITHDRAWAL, 1_500_000, 9_000_000)

    @Test fun `ملی - قالب کلید و مقدار`() =
        check(MelliParser, "بانک ملی ایران\nبرداشت\nمبلغ:250,000\nمانده:1,000,000\nحساب:0101XXXX\n14050709", FlowType.WITHDRAWAL, 250_000, 1_000_000)

    @Test fun `صادرات - قالب سطر اول علامت‌دار`() =
        check(SaderatParser, "برداشت:250,000-\nحساب:0101XXXX\nمانده:1,234,567\n0423-12:30", FlowType.WITHDRAWAL, 250_000, 1_234_567)

    @Test fun `تجارت - کلید و مقدار`() =
        check(TejaratParser, "حساب:1234\nبرداشت:1,000,000\nمانده:5,000,000\nتاریخ:1405/07/09\nساعت:12:30", FlowType.WITHDRAWAL, 1_000_000, 5_000_000)

    @Test fun `سامان - تاریخ و ساعت`() =
        check(SamanParser, "بانک سامان\nبرداشت از حساب 123\nمبلغ 350,000\nمانده 2,000,000\nتاریخ 1405/07/09\nساعت 12:30", FlowType.WITHDRAWAL, 350_000, 2_000_000)

    @Test fun `سپه - علامت‌دار`() =
        check(SepahParser, "بانک سپه\n+3,000,000\nحساب 123\nمانده 4,000,000\n12:30 1405,07,09", FlowType.DEPOSIT, 3_000_000, 4_000_000)

    @Test fun `رفاه - پارسر عمومی`() =
        check(GenericParser, "بانک رفاه\n2,000,000+\nمانده:7,000,000\n0709-12:30", FlowType.DEPOSIT, 2_000_000, 7_000_000)

    @Test fun `کشاورزی - قالب سطر اول`() =
        check(KeshavarziParser, "واریز 5,000,000\nمانده 6,000,000\n1405/07/09-12:30", FlowType.DEPOSIT, 5_000_000, 6_000_000)

    @Test fun `دی - کلید و مقدار`() =
        check(DeyParser, "بانک دی\nبرداشت از حساب:123\nمبلغ:800,000\nموجودی:1,200,000", FlowType.WITHDRAWAL, 800_000, 1_200_000)

    @Test fun `اقتصاد نوین`() =
        check(EghtesadNovinParser, "بانک اقتصاد نوین\nبرداشت:123\n700,000\nزمان\n-\nمانده:900,000", FlowType.WITHDRAWAL, 700_000, 900_000)

    @Test fun `مهر - چهار خط`() =
        check(MehrParser, "موسسه\nبرداشت\nحساب\nمبلغ 120,000\nمانده 880,000\n1405،07", FlowType.WITHDRAWAL, 120_000, 880_000)

    @Test fun `مسکن`() =
        check(MaskanParser, "بانک مسکن\nخرید 450,000\nمبلغ:450,000\nx\ny\nمانده 2,500,000", FlowType.WITHDRAWAL, 450_000, 2_500_000)

    @Test fun `شهر`() =
        check(ShahrParser, "بانک شهر\nواریز:123\nمبلغ:600,000\nموجودی:1,600,000", FlowType.DEPOSIT, 600_000, 1_600_000)

    @Test fun `پارسیان`() =
        check(ParsianParser, "بانک پارسیان\nخرید:کارت 1234\nمبلغ:90,000\nمانده:910,000", FlowType.WITHDRAWAL, 90_000, 910_000)

    @Test fun `کارآفرین - مبلغ با منفی`() =
        check(KarafarinParser, "کارت:1234\nمبلغ:-75,000\nمانده:925,000", FlowType.WITHDRAWAL, 75_000, 925_000)

    @Test fun `انصار - مبلغ با ریال`() =
        check(AnsarParser, "انصار\nبرداشت از حساب\n300,000 ریال\nمانده:700,000", FlowType.WITHDRAWAL, 300_000, 700_000)

    @Test fun `قوامین - علامت‌دار`() =
        check(GhavaminParser, "قوامین\n1405/07/09 12:30\n-150,000\nحساب\nمانده\n850,000", FlowType.WITHDRAWAL, 150_000, 850_000)

    @Test fun `آینده`() =
        check(AyandehParser, "بانک آینده\nواریز به حساب\nحساب:123\nمبلغ:1,000,000\nمانده:3,000,000", FlowType.DEPOSIT, 1_000_000, 3_000_000)

    @Test fun `پست بانک`() =
        check(PostBankParser, "پست بانک\nبرداشت\nمبلغ:200,000\nمانده:800,000\n1405/07/09\n12:30", FlowType.WITHDRAWAL, 200_000, 800_000)

    @Test fun `سینا`() =
        check(SinaParser, "بانک سینا\nبرداشت از 1-2-3\nمبلغ 50,000\nمانده 950,000", FlowType.WITHDRAWAL, 50_000, 950_000)

    @Test fun `کوثر`() =
        check(KosarParser, "کوثر\nبرداشت از 123\nمبلغ 40,000\n1405/07/09 12:30\nموجودی 60,000", FlowType.WITHDRAWAL, 40_000, 60_000)

    @Test fun `عسکریه`() =
        check(AskariehParser, "عسکریه\nبرداشت 25,000\nمانده 75,000", FlowType.WITHDRAWAL, 25_000, 75_000)

    @Test fun `حکمت`() =
        check(HekmatParser, "حکمت\nواریز به حساب 123\nمبلغ:400,000\nمانده:1,400,000", FlowType.DEPOSIT, 400_000, 1_400_000)

    @Test fun `سرمایه`() =
        check(SarmayehParser, "سرمایه\nواریز سود\nمبلغ:12,000\nمانده:1,012,000", FlowType.DEPOSIT, 12_000, 1_012_000)

    @Test fun `ایران زمین`() =
        check(IranZaminParser, "ایران زمین\nبرداشت\nمبلغ 330,000\nمانده 670,000", FlowType.WITHDRAWAL, 330_000, 670_000)

    @Test fun `توسعه صادرات`() =
        check(ToseehSaderatParser, "توسعه صادرات\nواریز\nحساب:123\nمبلغ:5,000,000\nمانده:8,000,000", FlowType.DEPOSIT, 5_000_000, 8_000_000)

    @Test fun `خاورمیانه`() =
        check(KhavarMianehParser, "خاورمیانه\nبرداشت\n123\nمبلغ 60,000\n1405/07/09\n12:30\nمانده 40,000\nخرید", FlowType.WITHDRAWAL, 60_000, 40_000)

    @Test fun `افضل توس`() =
        check(AfzalToosParser, "افضل توس\nبرداشت مبلغ 15,000\nموجودی 85,000", FlowType.WITHDRAWAL, 15_000, 85_000)

    @Test fun `مهر ایران - علامت در خط مبلغ`() =
        check(MehrIranParser, "مهر ایران\nحساب:123\nمبلغ:+20,000\nمانده:120,000", FlowType.DEPOSIT, 20_000, 120_000)

    @Test fun `گردشگری`() =
        check(GardeshgariParser, "گردشگری\nx\nبرداشت\nمبلغ:70,000\n12:30_0709\nموجودی:30,000", FlowType.WITHDRAWAL, 70_000, 30_000)
}
