package ir.jibito.app.data.parser

/**
 * «اسم فروشگاه کدومه؟»: کاربر در یک پیامک (رمز دوم یا خود برداشت) خطِ اسم فروشگاه را نشان می‌دهد.
 * اپ «شکل» آن پیامک را یاد می‌گیرد: همه‌ی خط‌ها با «#» به‌جای عددها، و خطِ اسم با [MARK].
 * پیامکی از همان بانک که خط‌به‌خط همین شکل را دارد، اسم فروشگاهش را از همان خط می‌گیرد، با هر فروشگاهی.
 *
 * نمونه (پاسارگاد): «پاسارگاد / خرید / اسنپ مارکت / مبلغ:12,321,000 / رمز: 04720 / 09:57:36»
 * ← «پاسارگاد / خرید / ⟨اسم⟩ / مبلغ:# / رمز: # / #».
 */
object MerchantRules {

    /** جای اسم فروشگاه در شکل پیامک */
    const val MARK = "⟨اسم⟩"

    private val digits = Regex("\\d[\\d,،.:/_\\-]*")

    /** خط‌های غیرخالی پیامک (بعد از یکسان‌سازی)؛ همان شماره‌گذاری‌ای که برگه‌ی «اسم فروشگاه کدومه؟» نشان می‌دهد */
    fun lines(rawBody: String): List<String> =
        SmsTextNormalizer.normalize(rawBody).split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    /** خطی که عدد دارد (مبلغ، رمز، ساعت، مانده) اسم فروشگاه نیست و انتخاب‌شدنی هم نیست */
    fun isPickable(line: String): Boolean = line.none { it.isDigit() } && MerchantExtractor.clean(line) != null

    private fun mask(line: String): String = digits.replace(line, "#")

    /** شکل پیامک با خطِ اسم در [lineIndex]؛ null اگر آن خط انتخاب‌شدنی نباشد */
    fun skeleton(rawBody: String, lineIndex: Int): String? {
        val ls = lines(rawBody)
        if (lineIndex !in ls.indices || !isPickable(ls[lineIndex])) return null
        return ls.mapIndexed { i, l -> if (i == lineIndex) MARK else mask(l) }.joinToString("\n")
    }

    /** اسم فروشگاه اگر پیامک دقیقاً شکل [skeleton] را داشته باشد؛ وگرنه null */
    fun match(rawBody: String, skeleton: String): String? {
        val ls = lines(rawBody)
        val sk = skeleton.split("\n")
        if (ls.size != sk.size) return null
        var name: String? = null
        for (i in ls.indices) {
            if (sk[i] == MARK) {
                if (!isPickable(ls[i])) return null
                name = MerchantExtractor.clean(ls[i])
            } else if (mask(ls[i]) != sk[i]) {
                return null
            }
        }
        return name
    }

    /** اولین شکلِ جور (تازه‌ترین یادگرفته اول) */
    fun find(rawBody: String?, skeletonsNewestFirst: List<String>): String? {
        if (rawBody.isNullOrBlank()) return null
        return skeletonsNewestFirst.firstNotNullOfOrNull { match(rawBody, it) }
    }
}
