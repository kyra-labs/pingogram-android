package dev.kyralabs.pingogram.core.data.backup

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The encryption of backup files (`docs/adr/0039-local-backup-and-restore.md`, "Protecting the file"): AES-256-GCM with
 * a key from the user's password by PBKDF2-HMAC-SHA512 over a random salt, [ROUNDS] rounds as the passcode's
 * (`docs/adr/0005-passcode-lock.md`), from the platform's own crypto (no library). Deriving a key is slow on purpose,
 * some hundred milliseconds on a phone: off the main thread.
 */
internal object BackupCipher {
    /** OWASP's Password Storage Cheat Sheet for PBKDF2-HMAC-SHA512 (2023), as the passcode's. */
    const val ROUNDS = 210_000

    const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val KDF = "PBKDF2WithHmacSHA512"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val random = SecureRandom()

    /** A new random salt for a password's key. */
    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    /** The AES key of [password] with [salt] and [rounds]. */
    fun key(
        password: String,
        salt: ByteArray,
        rounds: Int,
    ): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, rounds, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * [plain] encrypted with [key] under a new random nonce, which [aad] is given to authenticate with the rest. A key
     * kept in Android's Keystore ([key] without its bytes) picks its own nonce, as the Keystore requires.
     */
    fun encrypt(
        key: SecretKey,
        aad: (nonce: ByteArray) -> ByteArray,
        plain: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        if (key.encoded == null) {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } else {
            val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        }
        cipher.updateAAD(aad(cipher.iv))
        return cipher.doFinal(plain)
    }

    /** [encrypted] decrypted with [key] and [nonce]; null when the key is wrong or anything authenticated changed. */
    fun decrypt(
        key: SecretKey,
        nonce: ByteArray,
        aad: ByteArray,
        encrypted: ByteArray,
    ): ByteArray? {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return try {
            cipher.doFinal(encrypted)
        } catch (_: AEADBadTagException) {
            null
        }
    }
}
