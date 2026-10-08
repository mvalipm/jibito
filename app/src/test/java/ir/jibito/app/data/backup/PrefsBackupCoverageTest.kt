package ir.jibito.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * هر فایل تنظیماتی که اپ می‌سازد باید آگاهانه تصمیم داشته باشد: یا در پشتیبان می‌رود (BACKED_UP_PREFS)
 * یا مال همین گوشی است (DEVICE_ONLY_PREFS). تنظیم تازه‌ای که در هیچ‌کدام نباشد، این تست را می‌شکند
 * تا مثل «حساب‌های بیرون از جمع» بی‌صدا از پشتیبان جا نماند.
 *
 * اسم فایل‌ها از کد اپ خوانده می‌شود: getSharedPreferences("اسم", …) یا ثابتی (const val) در همان فایل.
 */
class PrefsBackupCoverageTest {

    private val sourceRoot = File("src/main/java")

    private val call = Regex("""getSharedPreferences\(\s*([^,]+?)\s*,""")
    private val literal = Regex(""""([^"]+)"""")
    private val constant = Regex("""^[A-Z][A-Z0-9_]*$""")

    /** اسم همه‌ی فایل‌های تنظیماتی که کد اپ باز می‌کند */
    private fun prefsNamesInSource(): Set<String> {
        assertTrue("پوشه‌ی کد اپ پیدا نشد: ${sourceRoot.absolutePath}", sourceRoot.isDirectory)
        val names = mutableSetOf<String>()
        sourceRoot.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val text = file.readText()
            for (match in call.findAll(text)) {
                val arg = match.groupValues[1]
                val quoted = literal.matchEntire(arg)
                when {
                    quoted != null -> names += quoted.groupValues[1]
                    constant.matches(arg) -> {
                        val definition = Regex("""const val $arg\s*=\s*"([^"]+)"""").find(text)
                        checkNotNull(definition) {
                            "${file.name}: اسم فایل تنظیمات ($arg) باید رشته یا const val همان فایل باشد"
                        }
                        names += definition.groupValues[1]
                    }
                    // متغیر محلی (مثل حلقه‌ی خود BackupManager روی همین فهرست‌ها) اسم تازه‌ای نمی‌سازد
                }
            }
        }
        return names
    }

    @Test
    fun everyPrefsFileIsBackedUpOrDeviceOnly() {
        val backedUp = BackupManager.BACKED_UP_PREFS.keys
        val deviceOnly = BackupManager.DEVICE_ONLY_PREFS
        assertEquals("هیچ فایلی نباید هم پشتیبان شود هم مال گوشی باشد", emptySet<String>(), backedUp intersect deviceOnly)

        val inSource = prefsNamesInSource()
        assertEquals(
            "این فایل‌های تنظیمات تصمیم پشتیبان ندارند؛ به BACKED_UP_PREFS یا DEVICE_ONLY_PREFS اضافه شوند",
            emptySet<String>(),
            inSource - backedUp - deviceOnly,
        )
        assertEquals(
            "این اسم‌ها در فهرست‌ها هستند ولی اپ دیگر بازشان نمی‌کند (غلط تایپی یا کد حذف‌شده)",
            emptySet<String>(),
            (backedUp + deviceOnly) - inSource,
        )
    }
}
