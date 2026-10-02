package ir.jibito.app.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * گزارش خطاها، فقط روی همین گوشی (فایلی در حافظه‌ی خود اپ).
 * هیچ‌وقت خودکار جایی فرستاده نمی‌شود؛ کاربر اگر بخواهد، از تنظیمات با «ارسال گزارش» خودش می‌فرستد.
 * فقط [MAX_ENTRIES] خطای آخر نگه داشته می‌شود.
 */
object ErrorLog {

    private const val FILE_NAME = "error_log.txt"
    private const val SEPARATOR = "\n----------\n"
    private const val MAX_ENTRIES = 30
    private const val MAX_TRACE_CHARS = 4_000

    /** خطاهای پیش‌بینی‌نشده‌ای که اپ را می‌بندند هم ثبت شوند (بعد رفتار عادی اندروید ادامه پیدا می‌کند) */
    fun installCrashHandler(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { record(appContext, "crash (${thread.name})", throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** یک خطا را ثبت می‌کند. خودش هرگز خطا نمی‌دهد. */
    @Synchronized
    fun record(context: Context, where: String, throwable: Throwable) {
        runCatching {
            val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString().take(MAX_TRACE_CHARS)
            val entry = "${System.currentTimeMillis()} · $where\n$trace"
            val entries = (readEntries(context) + entry).takeLast(MAX_ENTRIES)
            file(context).writeText(entries.joinToString(SEPARATOR))
        }
    }

    @Synchronized
    fun count(context: Context): Int = readEntries(context).size

    /** زمان آخرین خطا، یا null */
    @Synchronized
    fun lastAt(context: Context): Long? =
        readEntries(context).lastOrNull()?.substringBefore(' ')?.toLongOrNull()

    /** متن کامل برای ارسال: نسخه‌ی اپ و گوشی + خطاها (تازه‌ترین اول) */
    @Synchronized
    fun report(context: Context): String {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        val header = "Jibito $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"
        return (listOf(header) + readEntries(context).reversed()).joinToString(SEPARATOR)
    }

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
    }

    private fun readEntries(context: Context): List<String> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching { f.readText().split(SEPARATOR).filter { it.isNotBlank() } }.getOrDefault(emptyList())
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)
}
