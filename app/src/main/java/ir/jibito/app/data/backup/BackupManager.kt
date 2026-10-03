package ir.jibito.app.data.backup

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import ir.jibito.app.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * پشتیبان‌گیری و بازگردانی کامل داده‌های کاربر، در یک فایل رمزدار (BackupCrypto).
 *
 * داخل فایل: خود دیتابیس (همه‌ی تراکنش‌ها، دسته‌ها، بودجه‌ها، یادگرفته‌ها) + تنظیمات اپ.
 * چون خود فایل دیتابیس ذخیره می‌شود، پشتیبانِ نسخه‌ی قدیمی‌تر اپ هم با همان Migration های همیشگی بالا می‌آید.
 *
 * بازگردانی دو مرحله دارد تا دیتابیسِ در حال استفاده هیچ‌وقت زیر پای اپ عوض نشود:
 * ۱. [stageRestore]: فایل باز و بررسی می‌شود و کنار گذاشته می‌شود (هنوز چیزی عوض نشده).
 * ۲. اپ دوباره شروع می‌شود و [applyPendingRestore] قبل از باز شدن دیتابیس، آن را جایگزین می‌کند.
 */
class BackupManager(private val context: Context, private val db: AppDatabase) {

    data class Summary(val transactionCount: Int, val createdAt: Long?)

    /** نسخه‌ی پشتیبان از اپ جدیدتر است؛ اول اپ را به‌روز کنید */
    class TooNewException : Exception("Backup is from a newer app version")

    /** فایل باز شد ولی محتوایش سالم نیست */
    class DamagedException(message: String) : Exception(message)

    private val statePrefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
    private val _lastExportAt = MutableStateFlow(statePrefs.getLong(KEY_LAST_EXPORT, 0L).takeIf { it > 0 })

    /** آخرین باری که روی همین گوشی پشتیبان گرفته شد (null = هیچ‌وقت)؛ برای یادآوری در تنظیمات */
    val lastExportAt: StateFlow<Long?> = _lastExportAt

    private fun markExported(at: Long) {
        statePrefs.edit().putLong(KEY_LAST_EXPORT, at).apply()
        _lastExportAt.value = at
    }

    /** کل داده‌ها را رمزدار در [out] می‌نویسد. */
    suspend fun export(out: OutputStream, password: CharArray) = withContext(Dispatchers.IO) {
        val work = freshDir("backup_work")
        try {
            val dbCopy = snapshotDatabase(work)
            val meta = Properties().apply {
                setProperty(META_FORMAT, "1")
                setProperty(META_DB_VERSION, AppDatabase.VERSION.toString())
                setProperty(META_CREATED_AT, System.currentTimeMillis().toString())
            }
            val zipped = ByteArrayOutputStream()
            ZipOutputStream(zipped).use { zip ->
                zip.putNextEntry(ZipEntry(ENTRY_META))
                meta.store(zip, null)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(ENTRY_DB))
                dbCopy.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(ENTRY_PREFS))
                zip.write(prefsToJson(context).toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            out.write(BackupCrypto.encrypt(zipped.toByteArray(), password))
            out.flush()
            markExported(System.currentTimeMillis())
        } finally {
            work.deleteRecursively()
        }
    }

    /**
     * فایل پشتیبان را باز و بررسی می‌کند و برای جایگزینی در شروع بعدی اپ کنار می‌گذارد.
     * @throws BackupCrypto.NotABackupException @throws BackupCrypto.WrongPasswordException
     * @throws TooNewException @throws DamagedException
     */
    suspend fun stageRestore(input: InputStream, password: CharArray): Summary = withContext(Dispatchers.IO) {
        val data = readLimited(input)
        val plain = BackupCrypto.decrypt(data, password)

        val pending = freshDir(PENDING_DIR)
        var ok = false
        try {
            var meta: Properties? = null
            var prefs: String? = null
            var hasDb = false
            ZipInputStream(ByteArrayInputStream(plain)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    // فقط اسم‌های شناخته‌شده؛ هیچ مسیری از داخل فایل برای نوشتن استفاده نمی‌شود
                    when (entry.name) {
                        ENTRY_META -> meta = Properties().apply { load(ByteArrayInputStream(zip.readBytes())) }
                        ENTRY_PREFS -> prefs = zip.readBytes().toString(Charsets.UTF_8)
                        ENTRY_DB -> {
                            File(pending, AppDatabase.NAME).outputStream().use { zip.copyTo(it) }
                            hasDb = true
                        }
                    }
                }
            }
            if (meta == null || !hasDb) throw DamagedException("missing parts")
            val summary = inspectDatabase(File(pending, AppDatabase.NAME))
            prefs?.let { File(pending, ENTRY_PREFS).writeText(it) }
            // آخرین قدم: علامت «آماده‌ی جایگزینی» (تا نیمه‌کاره‌ها هرگز اعمال نشوند)
            File(pending, READY_MARK).writeText("1")
            ok = true
            summary.copy(createdAt = meta?.getProperty(META_CREATED_AT)?.toLongOrNull())
        } finally {
            if (!ok) pending.deleteRecursively()
        }
    }

    /**
     * کپی سالم و یک‌تکه از دیتابیس در حال استفاده.
     * در حالت WAL، داده‌ی ثبت‌شده = فایل اصلی + فایل -wal. هر دو داخل یک تراکنش نوشتنی کپی می‌شوند،
     * تا در همان لحظه کسی چیزی ننویسد؛ بعد کپی باز می‌شود تا WAL در فایل اصلی ادغام شود.
     */
    private fun snapshotDatabase(dir: File): File {
        val source = context.getDatabasePath(AppDatabase.NAME)
        val sourceWal = File(source.path + "-wal")
        val copy = File(dir, AppDatabase.NAME)
        val copyWal = File(copy.path + "-wal")
        db.openHelper.writableDatabase // اگر هنوز باز نشده، باز شود (و Migration ها اجرا شوند)
        db.runInTransaction(Runnable {
            source.copyTo(copy, overwrite = true)
            if (sourceWal.exists()) sourceWal.copyTo(copyWal, overwrite = true)
        })
        SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE).use { copyDb ->
            copyDb.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
        }
        File(copy.path + "-shm").delete()
        copyWal.delete()
        return copy
    }

    private fun freshDir(name: String): File =
        File(context.filesDir, name).apply {
            deleteRecursively()
            mkdirs()
        }

    companion object {
        private const val ENTRY_META = "meta.properties"
        private const val ENTRY_DB = "jibito.db"
        private const val ENTRY_PREFS = "prefs.json"
        // جدا از BACKED_UP_PREFS: تاریخ پشتیبانِ همین گوشی نباید با بازگردانی عوض شود
        private const val STATE_PREFS = "backup_state"
        private const val KEY_LAST_EXPORT = "last_export_at"
        private const val META_FORMAT = "format"
        private const val META_DB_VERSION = "dbVersion"
        private const val META_CREATED_AT = "createdAt"
        private const val PENDING_DIR = "pending_restore"
        private const val READY_MARK = "ready"
        private const val MAX_BACKUP_BYTES = 300L * 1024 * 1024

        /** تنظیماتی که همراه داده‌ها منتقل می‌شوند (نه وضعیت همگام‌سازی، نه قفل اپ — آن‌ها مال همین گوشی‌اند) */
        val BACKED_UP_PREFS = listOf("ui_prefs", "category_display", "custom_institutions")

        /** وضعیت همگام‌سازی پیامک؛ بعد از بازگردانی پاک می‌شود تا کل صندوق دوباره و درست خوانده شود */
        private const val SYNC_PREFS = "sms_sync_state"

        /**
         * اگر بازگردانی آماده‌ای منتظر است، دیتابیس و تنظیمات را جایگزین می‌کند.
         * باید در شروع اپ و قبل از هر استفاده از دیتابیس صدا زده شود. true یعنی جایگزین شد.
         */
        fun applyPendingRestore(context: Context): Boolean {
            val pending = File(context.filesDir, PENDING_DIR)
            val staged = File(pending, AppDatabase.NAME)
            if (!File(pending, READY_MARK).exists() || !staged.exists()) {
                if (pending.exists()) pending.deleteRecursively()
                return false
            }
            return try {
                val target = context.getDatabasePath(AppDatabase.NAME)
                target.parentFile?.mkdirs()
                // اول کنار فایل اصلی کپی، بعد جابه‌جایی یک‌باره (rename)؛ اگر وسط کار اپ بسته شود،
                // پوشه‌ی pending هنوز هست و دفعه‌ی بعد از اول انجام می‌شود
                val incoming = File(target.path + ".restoring")
                staged.copyTo(incoming, overwrite = true)
                // فایل‌های جانبی دیتابیس قبلی حتماً قبل از جایگزینی پاک شوند (وگرنه روی دیتابیس تازه اعمال می‌شوند)
                for (suffix in listOf("-wal", "-shm", "-journal")) File(target.path + suffix).delete()
                if (!incoming.renameTo(target)) {
                    incoming.copyTo(target, overwrite = true)
                    incoming.delete()
                }

                File(pending, ENTRY_PREFS).takeIf { it.exists() }?.let { file ->
                    runCatching { jsonToPrefs(context, JSONObject(file.readText())) }
                }
                context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
                pending.deleteRecursively()
                true
            } catch (e: Exception) {
                // شروع اپ هرگز نباید به خاطر بازگردانی از کار بیفتد
                false
            }
        }

        /** دیتابیس بازشده از فایل پشتیبان را بررسی می‌کند (نسخه، سلامت، جدول اصلی). */
        internal fun inspectDatabase(file: File): Summary {
            try {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { d ->
                    if (d.version > AppDatabase.VERSION) throw TooNewException()
                    if (d.version < 1) throw DamagedException("no schema version")
                    val integrity = d.rawQuery("PRAGMA integrity_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    if (integrity != "ok") throw DamagedException("integrity: $integrity")
                    val count = d.rawQuery("SELECT COUNT(*) FROM transaction_flows WHERE isDeleted = 0", null).use { c ->
                        c.moveToFirst()
                        c.getInt(0)
                    }
                    return Summary(transactionCount = count, createdAt = null)
                }
            } catch (e: TooNewException) {
                throw e
            } catch (e: DamagedException) {
                throw e
            } catch (e: Exception) {
                throw DamagedException(e.message ?: "unreadable database")
            } finally {
                File(file.path + "-journal").delete()
            }
        }

        private fun readLimited(input: InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_BACKUP_BYTES) throw BackupCrypto.NotABackupException()
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }

        internal fun prefsToJson(context: Context): JSONObject {
            val root = JSONObject()
            for (name in BACKED_UP_PREFS) {
                val values = JSONObject()
                for ((key, value) in context.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
                    val typed = when (value) {
                        is Boolean -> JSONObject().put("t", "b").put("v", value)
                        is Int -> JSONObject().put("t", "i").put("v", value)
                        is Long -> JSONObject().put("t", "l").put("v", value)
                        is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                        is String -> JSONObject().put("t", "s").put("v", value)
                        is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(value.filterIsInstance<String>()))
                        else -> null
                    } ?: continue
                    values.put(key, typed)
                }
                root.put(name, values)
            }
            return root
        }

        internal fun jsonToPrefs(context: Context, root: JSONObject) {
            for (name in BACKED_UP_PREFS) {
                val values = root.optJSONObject(name) ?: continue
                val editor: SharedPreferences.Editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                for (key in values.keys()) {
                    val typed = values.optJSONObject(key) ?: continue
                    when (typed.optString("t")) {
                        "b" -> editor.putBoolean(key, typed.getBoolean("v"))
                        "i" -> editor.putInt(key, typed.getInt("v"))
                        "l" -> editor.putLong(key, typed.getLong("v"))
                        "f" -> editor.putFloat(key, typed.getDouble("v").toFloat())
                        "s" -> editor.putString(key, typed.getString("v"))
                        "ss" -> {
                            val array = typed.getJSONArray("v")
                            editor.putStringSet(key, (0 until array.length()).mapTo(HashSet()) { array.getString(it) })
                        }
                    }
                }
                editor.commit()
            }
        }
    }
}
