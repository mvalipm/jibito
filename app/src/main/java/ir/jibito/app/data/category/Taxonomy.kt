package ir.jibito.app.data.category

/**
 * تعریف یک دسته‌ی پیش‌فرض (و زیردسته‌هایش).
 * @param code شناسه‌ی ثابت انگلیسی (اسم فارسی ممکن است بعداً عوض شود، این نه)
 * @param countsAsSpend false یعنی «خرج» حساب نمی‌شود (پس‌انداز، قرض دادن) — مثل انتقال به خودم
 */
data class CategoryDef(
    val code: String,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val children: List<CategoryDef> = emptyList(),
    val countsAsSpend: Boolean = true,
)

/**
 * ساختار دسته‌بندی خرج‌ها (سند «معماری دسته‌بندی هزینه‌ها — MVP»، با اصلاحات تأییدشده):
 * - ۱۳ دسته‌ی اصلی (بودجه فقط روی این‌ها) + «پس‌انداز و قرض» که خرج حساب نمی‌شود.
 * - لایه‌ی ۲ برای همه؛ لایه‌ی ۳ فقط جایی که از روی فروشنده قابل تشخیص و ارزشمند است.
 * - «سایر» نداریم؛ تراکنش می‌تواند بی‌دسته بماند (و بعداً: + دسته‌ی شخصی).
 */
object Taxonomy {

    private fun c(code: String, name: String, vararg children: CategoryDef) =
        CategoryDef(code, name, children = children.toList())

    val expense: List<CategoryDef> = listOf(
        CategoryDef(
            "food", "خوراک", "🛒", "#F2A541",
            listOf(
                c("food.market", "سوپرمارکت"),
                c("food.produce", "میوه و سبزیجات"),
                c("food.protein", "پروتئین"),
                c("food.bread", "نان"),
                c("food.sweets", "شیرینی"),
                c("food.nuts", "آجیل و خشکبار"),
                c("food.coffee", "قهوه و چای"),
                c("food.herbal", "ادویه و عطاری"),
            ),
        ),
        CategoryDef(
            "dining", "رستوران و کافه", "🍽", "#E4572E",
            listOf(
                c("dining.restaurant", "رستوران و فست‌فود"),
                c("dining.cafe", "کافه"),
                c("dining.catering", "غذای آماده و کترینگ"),
                c("dining.juice", "آبمیوه و بستنی"),
            ),
        ),
        CategoryDef(
            "transport", "حمل‌ونقل", "🚗", "#17BEBB",
            listOf(
                c(
                    "transport.car", "خودرو شخصی",
                    c("transport.car.fuel", "سوخت"),
                    c("transport.car.service", "تعمیر و سرویس خودرو"),
                    c("transport.car.parts", "قطعات خودرو"),
                    c("transport.car.parking", "پارکینگ"),
                    c("transport.car.wash", "کارواش"),
                ),
                c("transport.taxi", "تاکسی اینترنتی"),
                c("transport.public", "حمل‌ونقل عمومی"),
                c("transport.rent", "اجاره خودرو"),
            ),
        ),
        CategoryDef(
            "clothing", "پوشاک", "👕", "#C73E8B",
            listOf(
                c("clothing.clothes", "لباس"),
                c("clothing.shoes", "کفش"),
                c("clothing.bags", "کیف و اکسسوری"),
                c("clothing.underwear", "لباس زیر و راحتی"),
            ),
        ),
        CategoryDef(
            "home", "خانه و خانواده", "🏠", "#8D6A9F",
            listOf(
                c("home.rent", "اجاره و مسکن"),
                c("home.furniture", "اثاث و لوازم خانه"),
                c("home.repair", "تعمیر و نگهداری خانه"),
                c(
                    "home.services", "خدمات منزل",
                    c("home.services.building", "شارژ ساختمان"),
                    c("home.services.cleaning", "نظافت"),
                    c("home.services.gardener", "باغبان"),
                ),
                c(
                    "home.family", "پرداخت به خانواده",
                    c("home.family.allowance", "پول توجیبی"),
                    c("home.family.spouse", "خرجی همسر"),
                ),
            ),
        ),
        CategoryDef(
            "health", "سلامت", "💊", "#D1495B",
            listOf(
                c("health.doctor", "پزشک و ویزیت"),
                c("health.pharmacy", "دارو و مکمل"),
                c("health.lab", "آزمایش و تصویربرداری"),
                c("health.dental", "دندانپزشکی"),
                c("health.hospital", "بیمارستان و درمان"),
                c("health.optics", "عینک و لنز"),
                c("health.physio", "فیزیوتراپی"),
            ),
        ),
        CategoryDef(
            "education", "آموزش", "📚", "#3F88C5",
            listOf(
                c("education.school", "مدرسه و دانشگاه"),
                c("education.course", "کلاس و دوره"),
                c("education.books", "کتاب آموزشی و لوازم تحریر"),
            ),
        ),
        CategoryDef(
            "fun", "تفریح و سرگرمی", "🎬", "#7FB069",
            listOf(
                c(
                    "fun.travel", "سفر و گردش",
                    c("fun.travel.stay", "اقامت"),
                    c("fun.travel.ticket", "بلیط"),
                    c("fun.travel.activities", "تفریحات سفر"),
                ),
                c("fun.cinema", "سینما و تئاتر"),
                c("fun.events", "کنسرت و رویداد"),
                c("fun.games", "بازی"),
                c("fun.books", "کتاب و مجله"),
            ),
        ),
        CategoryDef(
            "finance", "مالی و پرداخت", "💳", "#2E86AB",
            listOf(
                c(
                    "finance.bills", "قبض و شارژ",
                    c("finance.bills.electricity", "برق"),
                    c("finance.bills.water", "آب"),
                    c("finance.bills.gas", "گاز"),
                    c("finance.bills.phone", "تلفن ثابت"),
                    c("finance.bills.internet", "اینترنت"),
                    c("finance.bills.mobile", "شارژ موبایل"),
                ),
                c("finance.loan", "وام و اقساط"),
                c("finance.fees", "کارمزد بانکی"),
                c(
                    "finance.insurance", "بیمه",
                    c("finance.insurance.home", "بیمه خانه"),
                    c("finance.insurance.car", "بیمه ماشین"),
                    c("finance.insurance.life", "بیمه عمر"),
                ),
                c("finance.tax", "مالیات و عوارض"),
                c("finance.fines", "جریمه"),
                c("finance.gifts", "هدیه و کمک مالی"),
            ),
        ),
        CategoryDef(
            "pets", "حیوان خانگی", "🐾", "#B5835A",
            listOf(
                c("pets.food", "غذای حیوان"),
                c("pets.supplies", "لوازم حیوان"),
                c("pets.care", "بهداشت حیوان"),
                c("pets.vet", "دامپزشکی"),
            ),
        ),
        CategoryDef(
            "beauty", "آرایشی و بهداشتی", "💄", "#E26D9B",
            listOf(
                c("beauty.skin", "مراقبت پوست"),
                c("beauty.hair", "مراقبت مو"),
                c("beauty.makeup", "آرایش"),
                c("beauty.hygiene", "بهداشت شخصی"),
                c("beauty.perfume", "عطر و ادکلن"),
            ),
        ),
        CategoryDef(
            "sport", "ورزش", "🏋", "#4CAF7A",
            listOf(
                c("sport.gym", "باشگاه و مربی"),
                c("sport.gear", "تجهیزات ورزشی"),
                c("sport.supplements", "مکمل ورزشی"),
            ),
        ),
        CategoryDef(
            "electronics", "الکترونیک و لوازم برقی", "📱", "#5C6BC0",
            listOf(
                c("electronics.mobile", "موبایل و تبلت"),
                c("electronics.computer", "کامپیوتر و لپ‌تاپ"),
                c("electronics.accessories", "لوازم جانبی دیجیتال"),
                c("electronics.appliances", "لوازم خانگی برقی"),
                c("electronics.av", "صوتی و تصویری"),
                c("electronics.smart", "تجهیزات هوشمند"),
            ),
        ),
        // بیرون از خرج: پول از دست نرفته، فقط جابه‌جا شده
        CategoryDef(
            "savings", "پس‌انداز و قرض", "💰", "#8C8C8C",
            listOf(
                CategoryDef("savings.invest", "پس‌انداز و سرمایه‌گذاری", countsAsSpend = false),
                CategoryDef("savings.lend", "قرض دادم", countsAsSpend = false),
            ),
            countsAsSpend = false,
        ),
    )

    /** دسته‌های درآمد (یک لایه) */
    val income: List<CategoryDef> = listOf(
        CategoryDef("income.salary", "حقوق", "💼", "#1E9E6A"),
        CategoryDef("income.sales", "حاصل فروش محصول", "🏷", "#2E86AB"),
        CategoryDef("income.borrowed", "قرض گرفتم", "🤝", "#8D6A9F"),
        CategoryDef("income.repaid", "طلبم رو گرفتم", "↩", "#17BEBB"),
        CategoryDef("income.interest", "سود بانکی", "🏦", "#F2A541"),
        CategoryDef("income.gift", "هدیه", "🎁", "#C73E8B"),
        CategoryDef("income.other", "سایر درآمد", "•", "#8C8C8C"),
    )

    /**
     * دسته‌های نسخه‌ی قبلی اپ ← دسته‌ی جدید (برای انتقال یک‌باره‌ی تراکنش‌ها و بودجه‌ها).
     * null یعنی «بی‌دسته» (برای «سایر» که دیگر نداریم).
     * «خرید» جای مشخصی ندارد؛ به‌عنوان دسته‌ی شخصی کاربر نگه داشته می‌شود (OLD_KEEP_AS_CUSTOM).
     */
    val OLD_TO_NEW: Map<String, String?> = mapOf(
        "غذا" to "dining.restaurant",
        "سوپرمارکت" to "food.market",
        "رفت‌وآمد" to "transport",
        "سوخت" to "transport.car.fuel",
        "قبض و شارژ" to "finance.bills",
        "سرگرمی" to "fun",
        "سفر" to "fun.travel",
        "درمان" to "health",
        "سایر" to null,
    )
    const val OLD_KEEP_AS_CUSTOM = "خرید"

    /** همه‌ی دسته‌ها (هر سه لایه)، به ترتیب درخت */
    fun flatten(defs: List<CategoryDef>): List<CategoryDef> =
        defs.flatMap { listOf(it) + flatten(it.children) }

    fun byCode(code: String): CategoryDef? = flatten(expense + income).firstOrNull { it.code == code }
}
