package ir.jibito.app.data.category

import ir.jibito.app.data.local.entity.CategoryEntity
import ir.jibito.app.domain.Category

/**
 * «ماهیت خرج»: اجباری (تعهدی که باید پرداخت شود)، ضروری (لازم، ولی مقدارش دست توست)، دلخواه (می‌شود نخرید).
 * [code] همان عددی است که در CategoryEntity.nature ذخیره می‌شود.
 */
enum class Nature(val code: Int) {
    MUST(1),
    NEED(2),
    WANT(3);

    companion object {
        fun of(code: Int): Nature? = entries.firstOrNull { it.code == code }
    }
}

/**
 * ماهیت هر دسته: انتخاب خود کاربر، وگرنه پیش‌فرضِ همان شناسه (اول زیردسته، بعد دسته‌ی بالاترش)،
 * وگرنه ماهیت دسته‌ی بالاتر (زیردسته‌ی شخصی زیر یک دسته‌ی پیش‌فرض). دسته‌ی شخصیِ بی‌انتخاب: null («هنوز معلوم نیست»).
 */
object SpendNature {

    /** CategoryEntity.nature = ۰: پیش‌فرض */
    const val DEFAULT = 0

    /** پیش‌فرض‌ها؛ زیردسته‌ای که این‌جا نیست، ماهیت نزدیک‌ترین بالاتر را می‌گیرد («transport.car.fuel» ← «transport») */
    private val DEFAULTS: Map<String, Nature> = mapOf(
        "food" to Nature.NEED,
        "food.sweets" to Nature.WANT,
        "food.nuts" to Nature.WANT,
        "food.coffee" to Nature.WANT,
        "dining" to Nature.WANT,
        "transport" to Nature.NEED,
        "transport.rent" to Nature.WANT,
        "clothing" to Nature.WANT,
        "clothing.underwear" to Nature.NEED,
        "home" to Nature.NEED,
        "home.rent" to Nature.MUST,
        "home.furniture" to Nature.WANT,
        "health" to Nature.NEED,
        "education" to Nature.NEED,
        "education.school" to Nature.MUST,
        "fun" to Nature.WANT,
        "finance" to Nature.MUST,
        "finance.gifts" to Nature.WANT,
        "internet" to Nature.NEED,
        "internet.home" to Nature.MUST,
        "pets" to Nature.NEED,
        "beauty" to Nature.WANT,
        "beauty.hygiene" to Nature.NEED,
        "sport" to Nature.WANT,
        "electronics" to Nature.WANT,
    )

    /** پیش‌فرضِ یک شناسه؛ null برای دسته‌ی شخصی یا شناسه‌ی ناشناس */
    fun defaultFor(code: String?): Nature? {
        var c = code ?: return null
        while (true) {
            DEFAULTS[c]?.let { return it }
            val cut = c.lastIndexOf('.')
            if (cut < 0) return null
            c = c.substring(0, cut)
        }
    }

    fun of(cat: CategoryEntity, byId: Map<Long, CategoryEntity>): Nature? =
        resolve(cat.nature, cat.code, cat.parentId) { id -> byId[id]?.let { it.nature to it.code to it.parentId } }

    fun of(cat: Category, byId: Map<Long, Category>): Nature? =
        resolve(cat.nature, cat.code, cat.parentId) { id -> byId[id]?.let { it.nature to it.code to it.parentId } }

    /** پیش‌فرضی که اگر کاربر «پیش‌فرض» را انتخاب کند به کار می‌رود (بدون انتخاب خود این دسته) */
    fun defaultOf(cat: Category, byId: Map<Long, Category>): Nature? = of(cat.copy(nature = DEFAULT), byId)

    private fun resolve(
        explicit: Int,
        code: String?,
        parentId: Long?,
        lookup: (Long) -> Pair<Pair<Int, String?>, Long?>?,
    ): Nature? {
        var nature = explicit
        var c = code
        var parent = parentId
        // حلقه‌ی اشتباهی در درخت دسته‌ها نباید گیر بیندازد
        repeat(MAX_DEPTH) {
            Nature.of(nature)?.let { return it }
            defaultFor(c)?.let { return it }
            val next = parent?.let(lookup) ?: return null
            nature = next.first.first
            c = next.first.second
            parent = next.second
        }
        return null
    }

    private const val MAX_DEPTH = 10
}
