package ir.jibito.app.data.category

/**
 * متنی که کاربر در جعبه‌ی «بنویس» نوتیفیکیشن نوشته ← دسته (فقط وقتی مطمئنیم).
 *
 * به ترتیب:
 * ۱. خود اسم دسته («کافه»)
 * ۲. یکی از تکه‌های اسم دسته («فست فود» ← «رستوران و فست‌فود»)
 * ۳. همان دو، با یک حرف غلط تایپی («رستوان»)؛ فقط برای متن‌های بلندتر
 * ۴. اسم کامل یک دسته داخل متن («خرید لباس بچه» ← «لباس»)
 * ۵. اسم چندکلمه‌ای یک دسته داخل متن با یک حرف غلط یا محاوره‌ای («شارژ ساختمون مهر» ← «شارژ ساختمان»)؛
 *    اسم تک‌کلمه‌ای نه، چون «اینترنتی» هم با یک حرف «اینترنت» می‌شود
 * ۶. فرهنگ کلمه‌های CategorySuggester («اسنپ تا فرودگاه» ← «تاکسی اینترنتی»)
 *
 * اگر چند دسته‌ی بی‌ربط جور شوند (مثلاً دو «سایر» زیر دو دسته‌ی اصلی) چیزی انتخاب نمی‌شود؛
 * ولی اگر همه روی یک شاخه باشند (دسته‌ی اصلی و زیردسته‌اش) دقیق‌ترین انتخاب می‌شود.
 * متن همیشه یادداشت هم می‌شود، پس «انتخاب نکردن» چیزی را گم نمی‌کند.
 */
object ReplyCategoryMatcher {

    data class Candidate(val id: Long, val name: String, val parentId: Long?)

    private const val MIN_KEY_LENGTH = 2
    private const val TYPO_MIN_LENGTH = 5

    /** شناسه‌ی دسته‌ی جورشده، یا null */
    fun match(text: String, candidates: List<Candidate>): Long? {
        val spaced = normalize(text)
        val key = compact(spaced)
        if (key.length < MIN_KEY_LENGTH || candidates.isEmpty()) return null
        val byId = candidates.associateBy { it.id }
        val named = candidates.map { it to normalize(it.name) }.filter { compact(it.second).length >= MIN_KEY_LENGTH }

        fun pickWhere(test: (String) -> Boolean): Long? = pick(named.filter { test(it.second) }.map { it.first }, byId)

        pickWhere { compact(it) == key }?.let { return it }
        pickWhere { name -> parts(name).any { it == key } }?.let { return it }
        if (key.length >= TYPO_MIN_LENGTH) {
            pickWhere { name -> (listOf(compact(name)) + parts(name)).any { withinOneEdit(it, key) } }?.let { return it }
        }
        val contained = named.filter { (_, name) -> " $spaced ".contains(" $name ") }
        if (contained.isNotEmpty()) {
            val longest = contained.maxOf { compact(it.second).length }
            pick(contained.filter { compact(it.second).length == longest }.map { it.first }, byId)?.let { return it }
        }
        val words = spaced.split(' ')
        val nearly = named.filter { (_, name) ->
            val size = name.split(' ').size
            val target = compact(name)
            size > 1 && target.length >= TYPO_MIN_LENGTH &&
                words.windowed(size).any { withinOneEdit(target, it.joinToString("")) }
        }
        if (nearly.isNotEmpty()) {
            val longest = nearly.maxOf { compact(it.second).length }
            pick(nearly.filter { compact(it.second).length == longest }.map { it.first }, byId)?.let { return it }
        }
        val target = CategorySuggester.suggest(spaced) ?: return null
        val targetKey = compact(normalize(target))
        return pickWhere { compact(it) == targetKey }
    }

    /** یک جواب، یا اگر همه روی یک شاخه‌اند، عمیق‌ترینشان؛ وگرنه null */
    private fun pick(found: List<Candidate>, byId: Map<Long, Candidate>): Long? {
        val distinct = found.distinctBy { it.id }
        if (distinct.size <= 1) return distinct.firstOrNull()?.id
        val deepest = distinct.maxBy { depth(it, byId) }
        val chain = generateSequence(deepest) { c -> c.parentId?.let { byId[it] } }.map { it.id }.toSet()
        return if (distinct.all { it.id in chain }) deepest.id else null
    }

    private fun depth(c: Candidate, byId: Map<Long, Candidate>): Int =
        generateSequence(c) { x -> x.parentId?.let { byId[it] } }.take(10).count()

    /** تکه‌های اسمی مثل «رستوران و فست‌فود» ← [رستوران، فستفود] (فقط اسم‌های چندتکه) */
    private fun parts(name: String): List<String> {
        val pieces = name.split(" و ").map { compact(it) }.filter { it.length >= MIN_KEY_LENGTH }
        return if (pieces.size > 1) pieces else emptyList()
    }

    /**
     * یکسان‌سازی: «ي/ك» عربی، نیم‌فاصله، رقم‌های فارسی، علامت‌ها، حروف بزرگ لاتین.
     * خروجی کلمه‌ها را با یک فاصله جدا می‌کند.
     */
    internal fun normalize(text: String): String = buildString(text.length) {
        for (ch in text.lowercase()) {
            append(
                when (ch) {
                    'ي', 'ى' -> 'ی'
                    'ك' -> 'ک'
                    'ة' -> 'ه'
                    'ـ' -> continue // کشیده
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    else -> when {
                        ch.isLetterOrDigit() -> ch
                        Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> continue // اعراب
                        else -> ' '
                    }
                }
            )
        }
    }.trim().replace(Regex(" +"), " ")

    private fun compact(s: String): String = s.replace(" ", "")

    /** فاصله‌ی ویرایشی حداکثر ۱ (یک حرف اضافه، کم یا عوض) */
    internal fun withinOneEdit(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        val (s, l) = if (a.length <= b.length) a to b else b to a
        var i = 0
        var j = 0
        var edits = 0
        while (i < s.length && j < l.length) {
            if (s[i] == l[j]) {
                i++; j++
                continue
            }
            if (++edits > 1) return false
            if (s.length == l.length) i++
            j++
        }
        return edits + (l.length - j) + (s.length - i) <= 1
    }
}
