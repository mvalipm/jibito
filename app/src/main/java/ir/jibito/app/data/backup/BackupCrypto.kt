package ir.jibito.app.data.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * رمزگذاری فایل پشتیبان با رمزی که خود کاربر انتخاب می‌کند.
 * AES-256-GCM (هم محرمانه، هم جلوی دست‌کاری فایل را می‌گیرد) با کلیدی که از رمز با PBKDF2 ساخته می‌شود.
 *
 * ساختار فایل: MAGIC(8) | نسخه‌ی قالب(1) | تعداد تکرار PBKDF2(4) | salt(16) | iv(12) | متن رمزشده + برچسب GCM
 * کل سرآیند به‌عنوان AAD به GCM داده می‌شود، پس دست بردن در آن هم تشخیص داده می‌شود.
 */
object BackupCrypto {

    private val MAGIC = "JIBITOBK".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val HEADER_BYTES = 8 + 1 + 4 + SALT_BYTES + IV_BYTES

    /** حدود یک ثانیه روی گوشی متوسط؛ حدس زدن رمز را خیلی کند می‌کند */
    const val DEFAULT_ITERATIONS = 150_000

    /** کمترین طول رمز پشتیبان */
    const val MIN_PASSWORD_LENGTH = 6

    class WrongPasswordException : Exception("Wrong password or damaged file")
    class NotABackupException : Exception("Not a Jibito backup file")

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC).put(FORMAT_VERSION).putInt(iterations).put(salt).put(iv)
            .array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    /** @throws NotABackupException فایل پشتیبان جیبیتو نیست @throws WrongPasswordException رمز اشتباه یا فایل خراب */
    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        if (!isBackup(data)) throw NotABackupException()
        val buffer = ByteBuffer.wrap(data)
        buffer.position(MAGIC.size)
        if (buffer.get() != FORMAT_VERSION) throw NotABackupException()
        val iterations = buffer.int
        // جلوی فایل دست‌کاری‌شده با عدد عجیب (که برنامه را ساعت‌ها مشغول کند) را می‌گیرد
        if (iterations !in 1_000..5_000_000) throw NotABackupException()
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val iv = ByteArray(IV_BYTES).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(data, 0, HEADER_BYTES)
        return try {
            cipher.doFinal(data, HEADER_BYTES, data.size - HEADER_BYTES)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    fun isBackup(data: ByteArray): Boolean =
        data.size > HEADER_BYTES + TAG_BITS / 8 && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
