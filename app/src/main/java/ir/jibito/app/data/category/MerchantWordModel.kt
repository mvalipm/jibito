package ir.jibito.app.data.category

import kotlin.math.exp
import kotlin.math.ln

/**
 * حافظه‌ی سطح کلمه برای اسم طرف حساب (Naive Bayes ساده): از دسته‌هایی که خود کاربر برای فروشگاه‌ها انتخاب کرده یاد می‌گیرد
 * که کدام کلمه‌ها به کدام دسته می‌خورند، تا فروشگاه تازه‌ای که کلمه‌ی مشترک دارد («کافه نادری» بعد از «کافه آرتا»)
 * از صفر شروع نکند. CategoryLearner فقط همان اسمِ دقیق را به یاد می‌آورد.
 *
 * محتاط است و در این حالت‌ها ساکت می‌ماند:
 * - هیچ‌کدام از کلمه‌های اسم در نمونه‌ها نیست،
 * - کمتر از [MIN_SUPPORT] فروشگاه با کلمه‌ی مشترک از همان دسته داریم،
 * - احتمال دسته‌ی برنده کمتر از [MIN_CONFIDENCE] است (کلمه‌ی عمومی که بین چند دسته پخش است).
 *
 * @param choices جفت‌های (اسم طرف حساب، دسته‌ی انتخاب‌شده‌ی کاربر)؛ هر جفت یک بار
 */
class MerchantWordModel(choices: List<Pair<String, Long>>) {

    private data class Sample(val words: Set<String>, val categoryId: Long)

    private val samples: List<Sample> = choices.asSequence()
        .filter { !CategoryLearner.isPersonTransfer(it.first) }
        .map { (merchant, category) -> Sample(wordsOf(merchant), category) }
        .filter { it.words.isNotEmpty() }
        .distinct()
        .toList()

    /** تعداد نمونه‌ی هر دسته */
    private val classSamples: Map<Long, Int> = samples.groupingBy { it.categoryId }.eachCount()

    /** کلمه ← (دسته ← در چند نمونه‌ی آن دسته آمده) */
    private val wordClassCount: Map<String, Map<Long, Int>> = HashMap<String, MutableMap<Long, Int>>().also { counts ->
        for (s in samples) for (w in s.words) {
            val perClass = counts.getOrPut(w) { HashMap() }
            perClass[s.categoryId] = (perClass[s.categoryId] ?: 0) + 1
        }
    }

    /** مجموع آمدن کلمه‌ها در هر دسته */
    private val classWordTotal: Map<Long, Int> = samples.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.words.size } }

    /** @return شناسه‌ی دسته، یا null اگر مطمئن نیست */
    fun predict(merchant: String): Long? {
        val known = wordsOf(merchant).filter { it in wordClassCount }
        if (known.isEmpty()) return null

        val vocabulary = wordClassCount.size
        val logScores = classSamples.mapValues { (category, count) ->
            var score = ln(count.toDouble() / samples.size)
            for (w in known) {
                val seen = wordClassCount.getValue(w)[category] ?: 0
                score += ln((seen + ALPHA) / (classWordTotal.getValue(category) + ALPHA * vocabulary))
            }
            score
        }
        val best = logScores.maxByOrNull { it.value } ?: return null
        val total = logScores.values.sumOf { exp(it - best.value) }
        if (1.0 / total < MIN_CONFIDENCE) return null

        val support = samples.count { it.categoryId == best.key && it.words.any { w -> w in known } }
        return best.key.takeIf { support >= MIN_SUPPORT }
    }

    companion object {
        /** دست‌کم این‌قدر فروشگاهِ هم‌کلمه از همان دسته لازم است */
        const val MIN_SUPPORT = 2

        /** احتمالِ دسته‌ی برنده (از ۱) */
        const val MIN_CONFIDENCE = 0.7

        private const val ALPHA = 0.5
        private const val MIN_WORD_LENGTH = 2

        /** کلمه‌های یکسان‌سازی‌شده‌ی اسم (بدون عدد و تک‌حرفی) */
        internal fun wordsOf(merchant: String): Set<String> =
            ReplyCategoryMatcher.normalize(merchant).split(' ')
                .filter { it.length >= MIN_WORD_LENGTH && it.any { ch -> !ch.isDigit() } }
                .toSet()
    }
}
