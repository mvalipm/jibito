package ir.jibito.app.data.bank

/**
 * یک بانک یا موسسه‌ی مالی که پیامکش را می‌شناسیم.
 *
 * @param id همان شناسه‌ی اپ قدیمی (template_id) — ثابت می‌ماند.
 * @param parserKey کلید پایدار پارسرِ این بانک (به‌جای اسم کلاس Java در اپ قدیمی).
 * @param senders سرشماره‌ها به شکل «نرمال‌شده» (بدون +98 و صفر اول؛ اسم‌های انگلیسی با حروف کوچک و بدون فاصله).
 */
data class Bank(
    val id: Int,
    val name: String,
    val parserKey: String,
    val senders: Set<String>,
)

/**
 * فهرست سرشماره‌های بانکی، ساخته‌شده از فایل اکسل سرشماره‌ها.
 *
 * اصلاح نسبت به اپ قدیمی: سرشماره‌ی 20003304 مال «بانک ملت» است (در اپ قدیمی به‌اشتباه به «بانک ملی» وصل بود).
 */
object BankDirectory {

    val banks: List<Bank> = listOf(
        Bank(id = 1, name = "بانک ملی", parserKey = "melli", senders = setOf("20004000", "200044", "700717")),
        Bank(id = 2, name = "بانک شهر", parserKey = "shahr", senders = setOf("200035")),
        Bank(id = 3, name = "بانک تجارت", parserKey = "tejarat", senders = setOf("100080", "50001080", "tejaratbank")),
        Bank(id = 4, name = "بانک سرمایه", parserKey = "sarmayeh", senders = setOf("300058")),
        Bank(id = 5, name = "بانک اقتصاد نوین", parserKey = "eghtesad_novin", senders = setOf("200050")),
        Bank(id = 6, name = "بانک کشاورزی", parserKey = "keshavarzi", senders = setOf("300081301", "5000181301", "keshavarzi")),
        Bank(id = 7, name = "بانک صادرات", parserKey = "saderat", senders = setOf("200040", "20004008", "200060", "700719", "banksaderat")),
        Bank(id = 8, name = "بانک انصار", parserKey = "ansar", senders = setOf("200036")),
        Bank(id = 9, name = "موسسه مهر", parserKey = "mehr", senders = setOf("1000089", "200089", "mehreqtesad")),
        Bank(id = 10, name = "بانک مسکن", parserKey = "maskan", senders = setOf("10002503", "bankmaskan")),
        Bank(id = 11, name = "بانک ملت", parserKey = "mellat", senders = setOf("200033", "20003304", "30007501", "30007505", "bankmellat")),
        Bank(id = 12, name = "بانک پاسارگاد", parserKey = "pasargad", senders = setOf("1000900", "20009000", "30009000", "500019000", "bpasargad")),
        Bank(id = 13, name = "بانک توسعه تعاون", parserKey = "pasargad", senders = setOf("20006438")),
        Bank(id = 14, name = "بانک پارسیان", parserKey = "parsian", senders = setOf("200082", "300054", "500024")),
        Bank(id = 15, name = "بانک سامان", parserKey = "saman", senders = setOf("20000", "samanbank")),
        Bank(id = 16, name = "بانک رفاه", parserKey = "refah", senders = setOf("300066")),
        Bank(id = 17, name = "موسسه کوثر", parserKey = "kosar", senders = setOf("50002477")),
        Bank(id = 18, name = "بانک سینا", parserKey = "sina", senders = setOf("300028", "500048", "sinabank")),
        Bank(id = 19, name = "بانک قوامین", parserKey = "ghavamin", senders = setOf("1000222", "20000222")),
        Bank(id = 20, name = "موسسه ثامن", parserKey = "samen", senders = setOf("20006901")),
        Bank(id = 21, name = "بانک دی", parserKey = "dey", senders = setOf("20004002", "200043", "30002726", "daybank")),
        Bank(id = 22, name = "بانک آینده", parserKey = "ayandeh", senders = setOf("20004001", "200042")),
        Bank(id = 23, name = "بانک گردشگری", parserKey = "gardeshgari", senders = setOf("2000300")),
        Bank(id = 24, name = "بانک سپه", parserKey = "sepah", senders = setOf("200015")),
        Bank(id = 25, name = "بانک مهرایران", parserKey = "mehr_iran", senders = setOf("30008528")),
        Bank(id = 26, name = "بانک قرض الحسنه رسالت", parserKey = "pasargad", senders = setOf("20004747", "resalatbank")),
        Bank(id = 27, name = "بانک توسعه صادرات", parserKey = "toseeh_saderat", senders = setOf("200048")),
        Bank(id = 28, name = "صندوق مهر امام رضا", parserKey = "mehr_imam_reza", senders = setOf("20006858")),
        Bank(id = 29, name = "بانک کارآفرین", parserKey = "karafarin", senders = setOf("30004321")),
        Bank(id = 30, name = "پست بانک", parserKey = "post_bank", senders = setOf("100029", "200029")),
        Bank(id = 31, name = "بانک ایران زمین", parserKey = "iran_zamin", senders = setOf("300069000")),
        Bank(id = 32, name = "موسسه اعتباری توسعه", parserKey = "etebari_toseeh", senders = setOf("30005816")),
        Bank(id = 33, name = "تجارت الکترونیک پارسیان", parserKey = "pecco", senders = setOf("50002318")),
        Bank(id = 34, name = "موسسه عسکریه", parserKey = "askarieh", senders = setOf("200074374")),
        Bank(id = 35, name = "موسسه آرمان", parserKey = "arman", senders = setOf("30008514")),
        Bank(id = 36, name = "بانک حکمت ایرانیان", parserKey = "hekmat", senders = setOf("30008955")),
        Bank(id = 37, name = "موسسه میزان", parserKey = "mizan", senders = setOf("3000700500")),
        Bank(id = 38, name = "بانک خاورمیانه", parserKey = "khavar_mianeh", senders = setOf("20004860")),
        Bank(id = 39, name = "موسسه افضل توس", parserKey = "afzal_toos", senders = setOf("30008878")),
        Bank(id = 40, name = "بلوبانک", parserKey = "smart", senders = setOf("999987641")),
    )

    private val bySender: Map<String, Bank> =
        banks.flatMap { bank -> bank.senders.map { it to bank } }.toMap()

    /** بانک صاحب این فرستنده، یا null اگر ناشناس باشد. */
    fun findBySender(rawSender: String?): Bank? {
        if (rawSender.isNullOrBlank()) return null
        return bySender[normalizeSender(rawSender)]
    }

    /**
     * شکل‌های مختلف یک سرشماره را یکی می‌کند:
     * "+9820009000"، "9820009000" و "20009000" همه می‌شوند "20009000".
     * اسم‌های انگلیسی مثل "Bank Mellat" می‌شوند "bankmellat".
     */
    fun normalizeSender(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.any { it in 'A'..'Z' || it in 'a'..'z' }) {
            return trimmed.lowercase().replace(" ", "").replace(".", "")
        }
        val digits = trimmed.filter { it.isDigit() }.map { ch ->
            // ارقام فارسی/عربی را هم به انگلیسی تبدیل کن
            when (ch) {
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                else -> ch
            }
        }.joinToString("")
        return when {
            digits.startsWith("0098") -> digits.substring(4)
            digits.startsWith("98") -> digits.substring(2)
            digits.startsWith("0") -> digits.substring(1)
            else -> digits
        }
    }
}
