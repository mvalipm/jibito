package ir.jibito.app.data.repository

/** قانون‌های یادداشت تراکنش (مثلاً متنی که در جعبه‌ی «بنویس» نوتیفیکیشن نوشته شده). */
object TransactionNotes {

    /** بلندترین یادداشت؛ بقیه‌اش بریده می‌شود */
    const val MAX_LENGTH = 200

    /** فاصله‌های اضافه و خط‌های تازه یکی می‌شوند؛ متن خالی ← null (یعنی یادداشت ندارد) */
    fun clean(note: String?): String? =
        note?.replace(Regex("\\s+"), " ")?.trim()?.take(MAX_LENGTH)?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * نتیجه‌ی نوشتن در جعبه‌ی «بنویس» نوتیفیکیشن: نوشته همیشه یادداشت شده و اگر با دسته‌ای جور شد، دسته هم گرفته.
 * @param categoryId null یعنی دسته‌ای جور نشد (تراکنش بی‌دسته ماند)
 */
data class NoteReply(val note: String, val categoryId: Long?, val categoryName: String?)
