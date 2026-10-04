package ir.jibito.app.data.sms

/**
 * کدام ردیف دیتابیس مال کدام پیامک است؟
 *
 * معمولاً از روی شناسه‌ی پیامک (_id). اما بعد از بازگردانی پشتیبان یا انتقال به گوشی تازه، شناسه‌ها عوض می‌شوند
 * و یک _id ممکن است در گوشی تازه مال پیامک کاملاً دیگری باشد. برای همین:
 * ۱. شناسه فقط وقتی قبول است که زمان پیامک هم یکی باشد.
 * ۲. وگرنه (در صورت اجازه) از روی «زمان + متن» دنبال ردیف قبلی می‌گردیم ← دسته و انتخاب‌های کاربر حفظ می‌شود.
 * ۳. ردیفی که شناسه‌اش را پیامک دیگری لازم دارد، شناسه‌اش آزاد می‌شود (detach) تا ثبت تکراری یا گم شدن پیش نیاید.
 */
object SmsRowMatcher {

    data class Scanned(val smsId: Long, val dateMillis: Long, val body: String)

    data class Row(val rowId: Long, val smsId: Long?, val dateEpoch: Long)

    data class Result(
        /** شناسه‌ی پیامک ← شناسه‌ی ردیف قبلی‌اش */
        val matches: Map<Long, Long>,
        /** ردیف‌هایی که باید قبل از نوشتن، smsId شان خالی شود (چون پیامک دیگری آن شناسه را گرفته) */
        val detach: List<Long>,
    )

    /**
     * @param contentLookup (زمان، متن) ← شناسه‌ی ردیف؛ null یعنی جستجو با متن انجام نشود (اسکن افزایشی معمولی).
     *   فقط وقتی صدا زده می‌شود که شناسه جواب ندهد.
     */
    suspend fun match(
        scanned: List<Scanned>,
        rows: List<Row>,
        contentLookup: (suspend () -> Map<Pair<Long, String>, Long>)?,
    ): Result {
        val bySmsId = rows.filter { it.smsId != null }.associateBy { it.smsId!! }
        var content: Map<Pair<Long, String>, Long>? = null
        val claimed = HashSet<Long>()
        val matches = HashMap<Long, Long>()

        // اول همه‌ی تطابق‌های مطمئن (شناسه + زمان)، تا جستجوی متنی ردیفی را که مال خودش است ندزدد
        for (s in scanned) {
            val row = bySmsId[s.smsId] ?: continue
            if (row.dateEpoch == s.dateMillis && claimed.add(row.rowId)) matches[s.smsId] = row.rowId
        }
        if (contentLookup != null) {
            for (s in scanned) {
                if (s.smsId in matches) continue
                val lookup = content ?: contentLookup().also { content = it }
                val rowId = lookup[s.dateMillis to s.body] ?: continue
                if (claimed.add(rowId)) matches[s.smsId] = rowId
            }
        }

        // ردیفی که الان شناسه‌ی یک پیامک اسکن‌شده را دارد ولی مال آن پیامک نیست
        val detach = scanned.mapNotNull { s ->
            val holder = bySmsId[s.smsId] ?: return@mapNotNull null
            holder.rowId.takeIf { matches[s.smsId] != it }
        }.distinct()

        return Result(matches, detach)
    }

    /**
     * از ردیف‌هایی که در این اسکن به هیچ تراکنشی نرسیدند، کدام‌ها واقعاً باید حذف شوند؟
     * فقط آن‌هایی که پیامکشان هنوز در صندوق هست (پس حالا تراکنش حساب نمی‌شود، مثلاً پولِ برگشتی بوده).
     * پیامکی که کاربر پاک کرده یا در گوشی تازه نیست، تراکنشش را پاک نمی‌کند.
     *
     * @param inboxIds شناسه ← زمانِ پیامک‌های دیده‌شده؛ شناسه فقط با همان زمان قبول است (مثل [match])
     * @param inboxContent «زمان + متن» پیامک‌های دیده‌شده (وقتی شناسه‌ها عوض شده‌اند)
     * @param rowContent شناسه‌ی ردیف ← متن پیامکش؛ فقط وقتی صدا زده می‌شود که شناسه جواب ندهد
     * @return شناسه‌ی ردیف‌هایی که باید حذف شوند
     */
    suspend fun reclassified(
        unmatched: List<Row>,
        inboxIds: Map<Long, Long>,
        inboxContent: Set<Pair<Long, String>>,
        rowContent: suspend () -> Map<Long, String>,
    ): List<Long> {
        var content: Map<Long, String>? = null
        return unmatched.filter { row ->
            if (row.smsId != null && inboxIds[row.smsId] == row.dateEpoch) return@filter true
            val lookup = content ?: rowContent().also { content = it }
            val body = lookup[row.rowId] ?: return@filter false
            (row.dateEpoch to body) in inboxContent
        }.map { it.rowId }
    }
}
