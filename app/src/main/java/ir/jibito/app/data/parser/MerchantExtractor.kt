package ir.jibito.app.data.parser

/**
 * اسم «طرف حساب» را از متن پیامک بیرون می‌کشد: فروشگاه/پذیرنده در خرید، یا کارت/حساب مقصد در انتقال.
 * هم برای پیامک رمز دوم استفاده می‌شود، هم برای خود پیامک برداشت/واریز.
 * همین اسم پایه‌ی «یادگیری» دسته است: یک بار دسته‌ی «اسنپ» را بگویی، دفعه‌های بعد خودکار می‌شود.
 */
object MerchantExtractor {

    /** کلیدهایی که بعدشان اسم طرف حساب می‌آید. ترتیب مهم است: دقیق‌ترها اول. */
    private val keys = listOf(
        "نام پذیرنده", "پذیرنده", "نام فروشگاه", "فروشگاه", "خرید از", "پرداخت به",
        // «به نام / در وجه» (اسم گیرنده) پیش از «مقصد»: در رمز «انتقال بین بانکی پل» مفید، خط مقصد فقط
        // «شبا + اسم بانک» است و اسم بانک برای همه‌ی گیرنده‌های آن بانک یکی است
        "انتقال به", "بابت", "در وجه", "به نام", "مقصد", "از طرف", "واریز کننده", "واریزکننده",
    )

    /** اگر مقدار با این‌ها شروع شود، اسم طرف حساب نیست. */
    private val notMerchantStarts = listOf(
        "مبلغ", "رمز", "کد", "زمان", "تاریخ", "اعتبار", "مهلت", "مانده", "موجودی", "ساعت", "ریال", "تومان",
    )

    /** متن باید از قبل نرمال شده باشد (SmsTextNormalizer). */
    fun find(text: String): String? {
        val lines = text.split("\n").map { it.trim() }
        for (key in keys) {
            for ((i, line) in lines.withIndex()) {
                val at = line.indexOf(key)
                if (at < 0) continue
                var value = line.substring(at + key.length).trim().trimStart(':', '：', '-').trim()
                if (value.isEmpty()) value = lines.getOrNull(i + 1).orEmpty()
                clean(value)?.let { return it }
            }
        }
        return null
    }

    /** کلمه‌های اضافه‌ی اول مقدار: «انتقال به حساب 1234» ← «1234» */
    private val leadingFillers = listOf("حساب", "کارت", "شماره", "سپرده", ":")

    internal fun clean(raw: String): String? {
        var v = raw.trim().trimEnd('.', '،', ',')
        var changed = true
        while (changed) {
            changed = false
            for (f in leadingFillers) {
                if (v.startsWith(f)) {
                    v = v.removePrefix(f).trim()
                    changed = true
                }
            }
        }
        if (v.isEmpty() || notMerchantStarts.any { v.startsWith(it) }) return null
        // فقط شماره کارت/حساب (مثلاً انتقال به 6037XXXX1234) ← «کارت/حساب …1234»
        val compact = v.replace(Regex("[\\s\\-]"), "")
        if (compact.matches(Regex("[\\dXx*]{6,}"))) {
            return "کارت/حساب …" + compact.takeLast(4)
        }
        // تکه‌هایی که عدد دارند (تاریخ، ساعت، شماره پیگیری) حذف می‌شوند تا اسم همیشه یکسان بماند
        val tokens = v.split(Regex("\\s+"))
        val words = tokens.filter { w -> w.none { it.isDigit() } }
        val name = words.joinToString(" ").trim().trimEnd(':', '-', '.', '،').trim()
        // «IR720*74001 بانک اقتصاد نوین»: اسم بانک گیرنده را مشخص نمی‌کند ← شماره‌ی حساب
        val account = tokens.firstOrNull { it.count(Char::isDigit) >= 4 && it.matches(Regex("(?:IR)?[\\dXx*]+", RegexOption.IGNORE_CASE)) }
        if (account != null && name.startsWith("بانک")) {
            return "کارت/حساب …" + account.filter { it.isDigit() }.takeLast(4)
        }
        return name.takeIf { it.length >= 2 }?.take(40)
    }
}
