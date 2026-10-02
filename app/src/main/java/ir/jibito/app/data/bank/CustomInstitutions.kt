package ir.jibito.app.data.bank

import android.content.Context

/**
 * بانک یا موسسه‌ای که در فهرست اپ نیست و کاربر خودش اضافه کرده (مثلاً «کارگزاری مفید»).
 * در SharedPreferences نگه داشته می‌شود و موقع باز شدن اپ در BankDirectory ثبت می‌شود،
 * تا همه‌جا (تراکنش‌ها، کارت مانده‌ها، بررسی) با همین اسم دیده شود.
 */
class CustomInstitutions(context: Context) {

    private val prefs = context.getSharedPreferences("custom_institutions", Context.MODE_PRIVATE)

    /** از حافظه بخوان و در BankDirectory ثبت کن */
    fun load() {
        BankDirectory.setCustom(read())
    }

    /**
     * یک موسسه‌ی تازه اضافه می‌کند و شناسه‌اش را برمی‌گرداند.
     * اگر اسم خالی باشد null؛ اگر همین اسم قبلاً هست (بانک یا موسسه)، همان را برمی‌گرداند.
     */
    @Synchronized
    fun add(rawName: String): Int? {
        val name = rawName.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)
        if (name.isEmpty()) return null
        BankDirectory.all.firstOrNull { key(it.name) == key(name) }?.let { return it.id }
        val current = read()
        val id = maxOf(FIRST_ID, (current.maxOfOrNull { it.id } ?: (FIRST_ID - 1)) + 1)
        val next = current + Bank(id = id, name = name, parserKey = "smart", senders = emptySet())
        prefs.edit().putStringSet(KEY, next.map { "${it.id}|${it.name}" }.toSet()).apply()
        BankDirectory.setCustom(next)
        return id
    }

    private fun read(): List<Bank> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { entry ->
            val id = entry.substringBefore('|').toIntOrNull() ?: return@mapNotNull null
            Bank(id = id, name = entry.substringAfter('|'), parserKey = "smart", senders = emptySet())
        }.sortedBy { it.id }

    private fun key(s: String) = s.replace('ي', 'ی').replace('ك', 'ک').replace("\u200c", "").replace(" ", "")

    private companion object {
        const val KEY = "items"
        const val FIRST_ID = 1000
        const val MAX_NAME = 40
    }
}
