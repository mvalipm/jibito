package ir.jibito.app.data.pro

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64

/**
 * کلید RSA کافه‌بازار: کلید خراب نباید هیچ‌وقت به Poolakey برسد (آن‌جا اپ را می‌بندد).
 * کلیدی که در BazaarConfig گذاشته می‌شود، اگر خالی نیست، باید همین‌جا درست باشد؛ وگرنه ساخت می‌شکند.
 */
class BazaarConfigTest {

    private val realKey: String by lazy {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        Base64.getEncoder().encodeToString(pair.public.encoded)
    }

    @Test
    fun `کلید عمومی RSA درست پذیرفته می‌شود، حتی چندخطی`() {
        assertTrue(BazaarConfig.isValidKey(realKey))
        assertTrue(BazaarConfig.isValidKey(realKey.chunked(64).joinToString("\n", postfix = "\n")))
    }

    @Test
    fun `کلید خالی، بریده یا غلط پذیرفته نمی‌شود`() {
        assertFalse(BazaarConfig.isValidKey(""))
        assertFalse(BazaarConfig.isValidKey("   "))
        assertFalse(BazaarConfig.isValidKey(realKey.dropLast(20)))
        assertFalse(BazaarConfig.isValidKey("not a key"))
        assertFalse(BazaarConfig.isValidKey("کلید"))
        // base64 درست ولی کلید RSA نیست
        assertFalse(BazaarConfig.isValidKey(Base64.getEncoder().encodeToString(ByteArray(64) { it.toByte() })))
    }

    @Test
    fun `کلید گذاشته‌شده در اپ یا خالی است یا درست`() {
        val key = BazaarConfig.RSA_PUBLIC_KEY
        assertTrue("کلید RSA در BazaarConfig خراب است؛ دوباره از پیشخان کافه‌بازار کپی کنید", key.isBlank() || BazaarConfig.isValidKey(key))
    }
}
