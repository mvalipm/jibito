package ir.jibito.app.data.export

import ir.jibito.app.data.parser.EventKind
import ir.jibito.app.data.parser.FlowType
import ir.jibito.app.domain.Category
import ir.jibito.app.domain.Transaction
import ir.jibito.app.util.Jalali
import java.util.Calendar

/**
 * خروجی CSV از تراکنش‌ها، برای اکسل و Google Sheets.
 * - UTF-8 با BOM، تا اکسل حروف فارسی را درست نشان دهد.
 * - مبلغ‌ها به تومان و با رقم انگلیسی، تا اکسل آن‌ها را عدد بشناسد (جمع، نمودار، ...).
 * - تاریخ شمسی (yyyy/MM/dd) و ساعت در ستون جدا.
 */
object CsvExport {

    private const val BOM = "\uFEFF"

    val HEADER = listOf("تاریخ", "ساعت", "نوع", "مبلغ (تومان)", "دسته", "زیردسته", "طرف حساب / توضیح", "بانک", "کارمزد (تومان)", "منبع", "رویداد بانکی")

    fun build(transactions: List<Transaction>, categories: List<Category>): String {
        val byId = categories.associateBy { it.id }
        val rows = transactions
            .sortedWith(compareBy({ it.dateMillis }, { it.id }))
            .map { row(it, byId) }
        return BOM + (listOf(HEADER) + rows).joinToString("\r\n") { cells -> cells.joinToString(",") { escape(it) } } + "\r\n"
    }

    private fun row(t: Transaction, byId: Map<Long, Category>): List<String> {
        val cal = Calendar.getInstance().apply { timeInMillis = t.dateMillis }
        val (jy, jm, jd) = Jalali.fromGregorian(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        val isDeposit = t.transaction.type == FlowType.DEPOSIT
        val type = when {
            t.isFailedPurchase -> "خرید ناموفق (برگشت پول)"
            t.isSelfTransfer -> "انتقال به حساب خودم"
            isDeposit -> "درآمد"
            else -> "خرج"
        }
        // مسیر دسته: دسته‌ی اصلی، و بقیه‌ی مسیر به‌عنوان زیردسته (مثلاً «رستوران › فست‌فود»)
        val path = t.categoryId?.let { pathOf(it, byId) }.orEmpty()
        val root = path.firstOrNull() ?: t.categoryName.orEmpty()
        val sub = path.drop(1).joinToString(" › ")
        return listOf(
            "%04d/%02d/%02d".format(java.util.Locale.US, jy, jm, jd),
            "%02d:%02d".format(java.util.Locale.US, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)),
            type,
            (t.transaction.amountRial / 10).toString(),
            root,
            sub,
            t.merchant.orEmpty(),
            t.bank?.name.orEmpty(),
            t.feeRial?.let { (it / 10).toString() }.orEmpty(),
            if (t.isManual) "دستی" else "پیامک",
            kindLabel(t.kind),
        )
    }

    private fun kindLabel(kind: EventKind): String = when (kind) {
        EventKind.PURCHASE -> "خرید کارتی"
        EventKind.TRANSFER -> "انتقال"
        EventKind.CASH_WITHDRAWAL -> "خودپرداز"
        EventKind.BILL_PAYMENT -> "قبض"
        EventKind.FEE -> "کارمزد"
        EventKind.REFUND -> "برگشت پول"
        EventKind.UNKNOWN -> ""
    }

    private fun pathOf(id: Long, byId: Map<Long, Category>): List<String> {
        val names = ArrayList<String>()
        var current = byId[id]
        while (current != null && names.size < 10) {
            names += current.name
            current = current.parentId?.let { byId[it] }
        }
        return names.reversed()
    }

    /**
     * CSV استاندارد (RFC 4180)، به‌علاوه‌ی خنثی کردن متنی که با = + - @ شروع شود
     * (وگرنه اکسل آن را فرمول اجرا می‌کند؛ مثلاً اسم فروشگاه در پیامک).
     */
    internal fun escape(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r" && value.toLongOrNull() == null) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }
}
