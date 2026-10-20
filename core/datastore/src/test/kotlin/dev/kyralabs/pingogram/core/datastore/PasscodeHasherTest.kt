package dev.kyralabs.pingogram.core.datastore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.HexFormat
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasscodeHasherTest {
    // Few rounds keep the tests fast; the rounds do not change what is checked.
    private val hasher = PasscodeHasher(iterations = 1_000)

    @Test
    fun `accepts the passcode a hash was made from and nothing else`() {
        val hash = hasher.hash("2580")

        assertTrue(hasher.matches("2580", hash))
        assertFalse(hasher.matches("2581", hash))
        assertFalse(hasher.matches("", hash))
        assertFalse(hasher.matches("25800", hash))
    }

    @Test
    fun `salts every hash anew, so equal passcodes get different hashes`() {
        val first = hasher.hash("password")
        val second = hasher.hash("password")

        assertEquals(PasscodeHasher.SALT_BYTES, first.salt.size)
        assertNotEquals(HexFormat.of().formatHex(first.salt), HexFormat.of().formatHex(second.salt))
        assertNotEquals(HexFormat.of().formatHex(first.hash), HexFormat.of().formatHex(second.hash))
    }

    @Test
    fun `is PBKDF2 with HMAC-SHA512 over the salt and the rounds kept in the hash`() {
        val hash = hasher.hash("Correct horse")
        val spec = PBEKeySpec("Correct horse".toCharArray(), hash.salt, 1_000, 512)
        val expected = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded

        assertEquals(1_000, hash.iterations)
        assertEquals(HexFormat.of().formatHex(expected), HexFormat.of().formatHex(hash.hash))
    }

    @Test
    fun `checks a passcode hashed with other rounds by the rounds it was hashed with`() {
        val older = PasscodeHasher(iterations = 500).hash("1234")

        assertTrue(hasher.matches("1234", older))
    }

    @Test
    fun `hashes new passcodes with OWASP's rounds by default`() {
        assertEquals(210_000, PasscodeHasher.ITERATIONS)
    }
}
