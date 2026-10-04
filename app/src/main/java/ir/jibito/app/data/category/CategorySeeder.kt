package ir.jibito.app.data.category

import androidx.room.withTransaction
import ir.jibito.app.data.local.AppDatabase
import ir.jibito.app.data.local.entity.BudgetEntity
import ir.jibito.app.data.local.entity.CategoryEntity

/**
 * دسته‌های پیش‌فرض را می‌سازد (درخت جدید)، و فقط یک بار، دسته‌های نسخه‌ی قبلی اپ را به آن منتقل می‌کند:
 * - تراکنش‌های هر دسته‌ی قدیمی ← دسته‌ی جدید معادلش (Taxonomy.OLD_TO_NEW).
 * - بودجه‌ی دسته‌ی قدیمی ← بودجه‌ی «دسته‌ی اصلیِ» معادلش (اگر چند تا روی یکی افتادند، جمع می‌شوند).
 * - «سایر» ← بی‌دسته. «خرید» ← دسته‌ی شخصی کاربر (جای مشخصی در ساختار جدید ندارد).
 * همه داخل یک تراکنش دیتابیس: یا کامل انجام می‌شود یا هیچ.
 */
class CategorySeeder(private val db: AppDatabase) {

    suspend fun ensure() {
        val categoryDao = db.categoryDao()
        // قبلاً انجام شده؟ (رنگ‌ها هر بار هماهنگ می‌شوند؛ ارزان است)
        if (categoryDao.byCode(Taxonomy.expense.first().code) != null) {
            addNewDefaults()
            mergeDuplicates()
            syncColors()
            return
        }

        db.withTransaction {
            val existing = categoryDao.all()
            val idByCode = HashMap<String, Long>()
            var order = 0

            suspend fun insertTree(defs: List<CategoryDef>, parentId: Long?, flowType: Int, inheritedSpend: Boolean) {
                for (def in defs) {
                    val counts = inheritedSpend && def.countsAsSpend
                    val id = categoryDao.insert(
                        CategoryEntity(
                            name = def.name,
                            icon = def.icon,
                            colorHex = def.color,
                            flowType = flowType,
                            parentId = parentId,
                            sortOrder = order++,
                            countsAsSpend = counts,
                            code = def.code,
                        )
                    )
                    idByCode[def.code] = id
                    insertTree(def.children, id, flowType, counts)
                }
            }

            insertTree(Taxonomy.expense, null, flowType = 2, inheritedSpend = true)
            // نصب تازه: دسته‌های درآمد هم ساخته می‌شوند (کاربرهای قبلی از Migration_3_4 دارندشان)
            if (existing.none { it.flowType == 1 }) {
                insertTree(Taxonomy.income, null, flowType = 1, inheritedSpend = true)
            }

            migrateOldExpenseCategories(existing, idByCode)
        }
        syncColors()
    }

    /**
     * کاربرهایی که درخت را قبلاً ساخته‌اند: دسته‌های پیش‌فرضِ تازه (مثل «اینترنت») اضافه می‌شوند،
     * و دسته‌هایی که جایشان عوض شده (Taxonomy.MOVED) تراکنش و پیشنهادشان را به جای جدید می‌دهند.
     * دسته‌ی اصلیِ تازه کنار دسته‌ی اصلیِ قبلی‌اش در ترتیب می‌نشیند.
     * اگر کاربر قبلاً دسته‌ی شخصی هم‌اسمی ساخته باشد، mergeDuplicates یکی‌شان می‌کند.
     */
    private suspend fun addNewDefaults() {
        val categoryDao = db.categoryDao()
        val flowDao = db.transactionFlowDao()
        val existing = categoryDao.all()
        val codes = existing.mapNotNull { it.code }.toSet()
        val missing = Taxonomy.flatten(Taxonomy.expense).any { it.code !in codes }
        val moved = Taxonomy.MOVED.keys.filter { code -> existing.any { it.code == code && !it.isArchived } }
        if (!missing && moved.isEmpty()) return

        db.withTransaction {
            val byCode = existing.filter { it.code != null }.associateBy { it.code!! }

            suspend fun insertMissing(defs: List<CategoryDef>, parentId: Long?, sortOrder: Int, counts: Boolean) {
                for (def in defs) {
                    val c = counts && def.countsAsSpend
                    val id = byCode[def.code]?.id ?: categoryDao.insert(
                        CategoryEntity(
                            name = def.name,
                            icon = def.icon,
                            colorHex = def.color,
                            flowType = 2,
                            parentId = parentId,
                            sortOrder = sortOrder,
                            countsAsSpend = c,
                            code = def.code,
                        )
                    )
                    insertMissing(def.children, id, sortOrder, c)
                }
            }

            var prevOrder = 0
            for (root in Taxonomy.expense) {
                val have = byCode[root.code]
                if (have != null) prevOrder = have.sortOrder
                insertMissing(listOf(root), null, prevOrder, true)
            }

            val now = categoryDao.all().filter { it.code != null }.associateBy { it.code!! }
            for (oldCode in moved) {
                val old = now.getValue(oldCode)
                val new = now[Taxonomy.MOVED.getValue(oldCode)] ?: continue
                flowDao.moveCategory(old.id, new.id)
                flowDao.renameSuggestion(old.name, new.name)
                categoryDao.reparent(old.id, new.id)
                categoryDao.archive(old.id)
            }
        }
    }

    /**
     * خودترمیمی: اگر دو دسته‌ی فعال با یک اسم، زیر یک والد و از یک نوع وجود داشته باشند
     * (مثلاً «الکترونیک و لوازم برقی» دو بار)، تراکنش‌ها، بودجه و زیردسته‌های تکراری
     * به دسته‌ی «اصلی» منتقل می‌شوند و تکراری بایگانی می‌شود. هیچ داده‌ای حذف نمی‌شود.
     * دسته‌ی اصلی: آن که شناسه‌ی ثابت (code) دارد؛ وگرنه قدیمی‌ترین.
     */
    private suspend fun mergeDuplicates() {
        val categoryDao = db.categoryDao()
        val flowDao = db.transactionFlowDao()
        val summaryDao = db.summaryDao()
        val active = categoryDao.all().filter { !it.isArchived }
        val groups = active.groupBy { Triple(it.flowType, it.parentId, normalizeName(it.name)) }.values.filter { it.size > 1 }
        if (groups.isEmpty()) return
        db.withTransaction {
            for (group in groups) {
                val keeper = group.sortedWith(compareBy<CategoryEntity> { it.code == null }.thenBy { it.id }).first()
                for (dup in group) {
                    if (dup.id == keeper.id) continue
                    flowDao.reassign(listOf(dup.id), keeper.id)
                    categoryDao.reparent(dup.id, keeper.id)
                    summaryDao.budgetFor(dup.id)?.let { b ->
                        val current = summaryDao.budgetFor(keeper.id)?.monthlyLimitRial ?: 0L
                        summaryDao.upsertBudget(BudgetEntity(keeper.id, current + b.monthlyLimitRial))
                        summaryDao.deleteBudget(dup.id)
                    }
                    categoryDao.archive(dup.id)
                }
            }
        }
    }

    private fun normalizeName(s: String): String =
        s.replace('ي', 'ی').replace('ك', 'ک').replace("\u200c", "").replace(" ", "").lowercase()

    /**
     * رنگ دسته‌های اصلی ← پالت اعتبارسنجی‌شده‌ی نمودار (CategoryPalette).
     * دسته‌های اصلی شخصی که رنگشان از پالت نیست، به نوبت یک رنگ پالت می‌گیرند.
     */
    private suspend fun syncColors() {
        val categoryDao = db.categoryDao()
        for ((code, color) in CategoryPalette.BY_CODE) categoryDao.setColorByCode(code, color)
        val palette = CategoryPalette.LIGHT.toSet()
        categoryDao.all()
            .filter { it.isCustom && it.parentId == null && !it.isArchived }
            .sortedBy { it.id }
            .forEachIndexed { i, c ->
                if (c.colorHex?.uppercase() !in palette) categoryDao.setColor(c.id, CategoryPalette.forCustom(i))
            }
    }

    private suspend fun migrateOldExpenseCategories(existing: List<CategoryEntity>, idByCode: Map<String, Long>) {
        val categoryDao = db.categoryDao()
        val flowDao = db.transactionFlowDao()
        val summaryDao = db.summaryDao()
        val old = existing.filter { it.flowType == 2 && !it.isArchived && it.code == null && !it.isCustom }

        for (cat in old) {
            if (cat.name == Taxonomy.OLD_KEEP_AS_CUSTOM) {
                // تراکنش‌ها و بودجه‌اش همان‌جا می‌مانند؛ فقط آخر فهرست می‌رود
                categoryDao.markCustom(cat.id, sortOrder = 10_000)
                continue
            }
            if (!Taxonomy.OLD_TO_NEW.containsKey(cat.name)) continue
            val newCode = Taxonomy.OLD_TO_NEW[cat.name]
            val newId = newCode?.let { idByCode[it] }

            if (newCode == null || newId == null) {
                // «سایر» ← بی‌دسته
                flowDao.uncategorize(cat.id)
                flowDao.renameSuggestion(cat.name, null)
                summaryDao.deleteBudget(cat.id)
            } else {
                flowDao.moveCategory(cat.id, newId)
                flowDao.renameSuggestion(cat.name, Taxonomy.byCode(newCode)?.name)
                // بودجه فقط روی دسته‌ی اصلی
                summaryDao.budgetFor(cat.id)?.let { oldBudget ->
                    val rootId = idByCode.getValue(newCode.substringBefore('.'))
                    val current = summaryDao.budgetFor(rootId)?.monthlyLimitRial ?: 0L
                    summaryDao.upsertBudget(BudgetEntity(rootId, current + oldBudget.monthlyLimitRial))
                    summaryDao.deleteBudget(cat.id)
                }
            }
            categoryDao.archive(cat.id)
        }
    }
}
