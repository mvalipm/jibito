package ir.jibito.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    // تعداد تکرار کم فقط برای سرعت تست؛ اپ از DEFAULT_ITERATIONS استفاده می‌کند
    private val fast = 2_000
    private val data = "تراکنش‌ها و دسته‌ها".toByteArray() + ByteArray(5_000) { it.toByte() }

    @Test
    fun roundTrip() {
        val encrypted = BackupCrypto.encrypt(data, "secret1".toCharArray(), fast)
        assertTrue(BackupCrypto.isBackup(encrypted))
        assertArrayEquals(data, BackupCrypto.decrypt(encrypted, "secret1".toCharArray()))
    }

    @Test
    fun encryptedDataDoesNotContainPlainText() {
        val encrypted = BackupCrypto.encrypt(data, "secret1".toCharArray(), fast)
        val needle = "تراکنش".toByteArray()
        val found = (0..encrypted.size - needle.size).any { i ->
            encrypted.copyOfRange(i, i + needle.size).contentEquals(needle)
        }
        assertFalse(found)
    }

    @Test
    fun sameInputEncryptsDifferentlyEachTime() {
        val a = BackupCrypto.encrypt(data, "secret1".toCharArray(), fast)
        val b = BackupCrypto.encrypt(data, "secret1".toCharArray(), fast)
        assertFalse(a.contentEquals(b))
    }

    @Test(expected = BackupCrypto.WrongPasswordException::class)
    fun wrongPasswordIsRejected() {
        val encrypted = BackupCrypto.encrypt(data, "secret1".toCharArray(), fast)
        BackupCrypto.decrypt(encrypted, "secret2".toCharArray())
    }

    @Test(expected = BackupCrypto.WrongPasswordException::class)
    fun tamperedFileIsRejected() {
        val encrypted = BackupCrypto.encrypt(data, "secret1".toCharArray(), fast)
        encrypted[encrypted.size - 40] = (encrypted[encrypted.size - 40] + 1).toByte()
        BackupCrypto.decrypt(encrypted, "secret1".toCharArray())
    }

    @Test(expected = BackupCrypto.WrongPasswordException::class)
    fun tamperedHeaderIsRejected() {
        val encrypted = BackupCrypto.encrypt(data, "secret1".toCharArray(), fast)
        encrypted[20] = (encrypted[20] + 1).toByte() // داخل salt
        BackupCrypto.decrypt(encrypted, "secret1".toCharArray())
    }

    @Test(expected = BackupCrypto.NotABackupException::class)
    fun randomFileIsNotABackup() {
        BackupCrypto.decrypt("hello, this is a photo".toByteArray() + ByteArray(100), "secret1".toCharArray())
    }
}
