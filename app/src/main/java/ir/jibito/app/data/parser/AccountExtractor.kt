package ir.jibito.app.data.parser

/**
 * شماره‌ی حساب/کارتِ خودِ پیامک (حسابی که پول از آن کم یا به آن اضافه شده)، اگر در متن باشد.
 *
 * فقط جاهایی که قطعاً حساب خود کاربر است:
 * - سطری که با «حساب»، «سپرده» یا «کارت» شروع می‌شود: «حساب:41007»، «کارت 6104»
 * - «از حساب …» / «کسر از حساب …» / «واریز به حساب …» / «مانده حساب …»
 * «انتقال به حساب …» (مقصدِ انتقال، نه حساب خود کاربر) حساب نمی‌شود.
 *
 * خروجی همان شکل متن است (ارقام و X و ستاره)، تا دو پیامکِ یک بانک دقیقاً با هم مقایسه شوند.
 */
object AccountExtractor {

    private const val NUMBER = """((?:IR)?[0-9][0-9Xx*.\-]{2,}|[Xx*]+[0-9][0-9Xx*.\-]+)"""

    private val patterns = listOf(
        // فاصله فقط در همان سطر ([ \t])، تا مبلغِ سطر بعد شماره‌حساب خوانده نشود
        Regex("""^[ \t]*(?:حساب|سپرده|کارت)[ \t]*[:：]?[ \t]*$NUMBER""", RegexOption.MULTILINE),
        Regex("""(?:برداشت از|کسر از|خرید از|واریز به|مانده|موجودی)[ \t]+(?:حساب|سپرده|کارت)[ \t]*[:：]?[ \t]*$NUMBER"""),
    )

    /** [normalizedBody]: متنِ یکدست‌شده با SmsTextNormalizer */
    fun find(normalizedBody: String): String? =
        patterns.firstNotNullOfOrNull { it.find(normalizedBody)?.groupValues?.get(1) }
            ?.trimEnd('.', '-')
            ?.uppercase()
}
