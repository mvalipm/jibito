package ir.jibito.app.data.parser

import ir.jibito.app.data.bank.Bank

/**
 * نقطه‌ی ورود همه‌ی پارس‌ها:
 * ۱. متن یکدست می‌شود.
 * ۲. اگر رمز/کد/تبلیغ بود، کنار گذاشته می‌شود.
 * ۳. اول پارسر اختصاصی بانک (اگر داشته باشیم)، بعد پارسر هوشمند عمومی.
 * ۴. اگر مبلغ به «تومان» بود، به ریال تبدیل می‌شود (همه‌چیز داخل اپ ریال است).
 */
object TransactionParser {

    /** پارسر اختصاصی هر بانک (کلیدها همان parserKey در BankDirectory). «smart» یعنی فقط پارسر هوشمند. */
    internal val bankParsers: Map<String, SmsParser> = mapOf(
        "afzal_toos" to AfzalToosParser,
        "ansar" to AnsarParser,
        "arman" to ArmanParser,
        "askarieh" to AskariehParser,
        "ayandeh" to AyandehParser,
        "dey" to DeyParser,
        "eghtesad_novin" to EghtesadNovinParser,
        "etebari_toseeh" to EtebariToseehParser,
        "gardeshgari" to GardeshgariParser,
        "ghavamin" to GhavaminParser,
        "hekmat" to HekmatParser,
        "iran_zamin" to IranZaminParser,
        "karafarin" to KarafarinParser,
        "keshavarzi" to KeshavarziParser,
        "khavar_mianeh" to KhavarMianehParser,
        "kosar" to KosarParser,
        "maskan" to MaskanParser,
        "mehr" to MehrParser,
        "mehr_imam_reza" to MehrImamRezaParser,
        "mehr_iran" to MehrIranParser,
        "mellat" to MellatParser,
        "melli" to MelliParser,
        "mizan" to MizanParser,
        "parsian" to ParsianParser,
        "pasargad" to PasargadParser,
        "pecco" to PeccoParser,
        "post_bank" to PostBankParser,
        "refah" to GenericParser, // اپ قدیمی هم برای رفاه ParseGeneric را صدا می‌زد
        "saderat" to SaderatParser,
        "saman" to SamanParser,
        "samen" to SamenParser,
        "sarmayeh" to SarmayehParser,
        "sepah" to SepahParser,
        "shahr" to ShahrParser,
        "sina" to SinaParser,
        "tejarat" to TejaratParser,
        "toseeh_saderat" to ToseehSaderatParser,
    )

    fun parse(bank: Bank, rawBody: String): ParsedTransaction? {
        val text = SmsTextNormalizer.normalize(rawBody)
        if (text.isEmpty() || NonTransactionFilter.isNotTransaction(text)) return null

        val result = bankParsers[bank.parserKey]?.parse(text) ?: SmartParser.parse(text) ?: return null

        val inToman = text.contains("تومان") && !text.contains("ریال")
        return if (inToman) {
            result.copy(amountRial = result.amountRial * 10, balanceRial = result.balanceRial?.times(10))
        } else {
            result
        }
    }

    /** اگر پیامک «رمز دوم» خرید بود، مبلغ و مقصدش را برمی‌گرداند. */
    fun parseOtp(rawBody: String): PurchaseOtp? = OtpParser.parse(SmsTextNormalizer.normalize(rawBody))
}
