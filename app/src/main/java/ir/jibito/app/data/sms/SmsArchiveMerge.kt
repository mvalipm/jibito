package ir.jibito.app.data.sms

/**
 * کدام کپی‌های پیامک (sms_archive) باید خوانده شوند؟ فقط آن‌هایی که دیگر در صندوق گوشی نیستند.
 *
 * هر پیامکِ صندوق فقط یک کپی را «می‌پوشاند»:
 * ۱. اول کپی‌ای با همان زمان و همان متن؛
 * ۲. اگر نبود، یک کپی هم‌متن با زمان دیگر — در انتقال به گوشی تازه زمان پیامک‌ها ممکن است کمی جابه‌جا شود،
 *    و متن پیامک بانکی (مبلغ، مانده، ساعت) تقریباً همیشه یکتاست.
 * اگر دو پیامک واقعاً یکسان بوده و یکی‌شان از گوشی پاک شده، آن یکی از کپی خوانده می‌شود.
 */
object SmsArchiveMerge {

    /** @param inbox متن پیامک‌های بانکیِ خوانده‌شده از صندوق ← زمان‌هایشان */
    fun notInInbox(archived: List<ArchivedSms>, inbox: Map<String, List<Long>>): List<ArchivedSms> {
        val covered = HashSet<Long>()
        // ۱. همان زمان و همان متن
        val leftDates = HashMap<String, MutableList<Long>>()
        inbox.forEach { (body, dates) -> leftDates[body] = dates.toMutableList() }
        for (a in archived) {
            val dates = leftDates[a.body] ?: continue
            if (dates.remove(a.dateMillis)) covered += a.archiveId
        }
        // ۲. هم‌متن، زمان دیگر (نزدیک‌ترین زمان اول)
        for (a in archived) {
            if (a.archiveId in covered) continue
            val dates = leftDates[a.body]?.takeIf { it.isNotEmpty() } ?: continue
            dates.remove(dates.minBy { kotlin.math.abs(it - a.dateMillis) })
            covered += a.archiveId
        }
        return archived.filter { it.archiveId !in covered }
    }
}
