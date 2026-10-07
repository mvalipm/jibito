package ir.jibito.app.util

import android.content.Context

/**
 * فهرست تغییرهای هر نسخه، از روی CHANGELOG.md ریشه‌ی ریپو (Gradle موقع ساخت آن را در assets اپ می‌گذارد).
 * یک منبع: همان متنی که در گیت نوشته می‌شود در صفحه‌ی «چه چیزی تازه است» دیده می‌شود.
 *
 * شکل فایل: هر نسخه یک سرتیتر `## 0.52.0` (تازه‌ترین بالا) و زیرش هر تغییر یک خطِ `- `. بقیه‌ی خط‌ها نادیده‌اند.
 */
object Changelog {

    const val ASSET = "CHANGELOG.md"

    data class Release(val version: String, val notes: List<String>)

    fun parse(text: String): List<Release> {
        val releases = mutableListOf<Release>()
        var version: String? = null
        var notes = mutableListOf<String>()
        fun flush() {
            version?.let { if (notes.isNotEmpty()) releases += Release(it, notes) }
        }
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("## ") -> {
                    flush()
                    version = line.removePrefix("## ").trim().substringBefore(' ')
                    notes = mutableListOf()
                }
                line.startsWith("- ") && version != null -> notes += line.removePrefix("- ").trim()
            }
        }
        flush()
        return releases
    }

    /** فایل داخل اپ؛ اگر نبود یا خوانده نشد، فهرست خالی (صفحه فقط خالی می‌ماند، اپ نمی‌شکند) */
    fun load(context: Context): List<Release> = runCatching {
        context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
    }.getOrDefault(emptyList())

    /**
     * مقایسه‌ی «0.52.0» با «0.51.10»؛ پسوند ساخت آزمایشی («0.52.0-165») نادیده گرفته می‌شود.
     */
    fun compare(a: String, b: String): Int {
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    private fun parts(v: String) = v.substringBefore('-').split('.').map { it.trim().toIntOrNull() ?: 0 }

    /**
     * بخش‌هایی که بعد از به‌روزرسانی باید یک بار نشان داده شوند: تازه‌تر از [lastSeen] تا [current].
     * [lastSeen] خالی یعنی کاربر از نسخه‌ای آمده که این صفحه را نداشت ← فقط بخش همین نسخه.
     */
    fun newSince(releases: List<Release>, lastSeen: String?, current: String): List<Release> =
        releases.filter { r ->
            compare(r.version, current) <= 0 &&
                if (lastSeen == null) compare(r.version, current) == 0 else compare(r.version, lastSeen) > 0
        }
}
