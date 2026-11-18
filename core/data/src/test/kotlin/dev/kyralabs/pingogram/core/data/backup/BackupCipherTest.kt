package dev.kyralabs.pingogram.core.data.backup

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** [BackupCipher]: AES-256-GCM with a key from the password by PBKDF2-HMAC-SHA512, as the passcode's (ADR 0005). */
class BackupCipherTest {
    private val salt = ByteArray(BackupCipher.SALT_BYTES) { it.toByte() }

    @Test
    fun `the key is PBKDF2-HMAC-SHA512 of the password over the salt, 256 bits, with the passcode's 210,000 rounds`() {
        val spec = PBEKeySpec("correct horse".toCharArray(), salt, 210_000, 256)
        val expected = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded

        assertEquals(210_000, BackupCipher.ROUNDS)
        assertArrayEquals(expected, BackupCipher.key("correct horse", salt, BackupCipher.ROUNDS).encoded)
    }

    @Test
    fun `what is encrypted decrypts with the same key, nonce and authenticated data, and with nothing else`() {
        val key = BackupCipher.key("pw", salt, rounds = 1)
        var nonce = ByteArray(0)
        val encrypted =
            BackupCipher.encrypt(
                key = key,
                aad = { used ->
                    nonce = used
                    byteArrayOf(1)
                },
                plain = byteArrayOf(9),
            )

        assertArrayEquals(byteArrayOf(9), BackupCipher.decrypt(key, nonce, byteArrayOf(1), encrypted))
        assertNull(BackupCipher.decrypt(key, nonce, byteArrayOf(2), encrypted))
        assertNull(BackupCipher.decrypt(BackupCipher.key("other", salt, rounds = 1), nonce, byteArrayOf(1), encrypted))
    }
}
