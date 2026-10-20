package dev.kyralabs.pingogram.core.datastore

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashes passcodes for the passcode lock with PBKDF2-HMAC-SHA512 from the platform's `SecretKeyFactory` (Android 8
 * and later; no crypto library), a random 16-byte salt per passcode and [iterations] rounds, OWASP's recommendation for
 * this hash (`docs/adr/0004-passcode-lock.md`). A hash keeps its own salt and rounds, so raising [iterations] later
 * still checks the passcodes set before.
 *
 * Slow on purpose (some hundred milliseconds on a phone): call it off the main thread.
 */
internal class PasscodeHasher(
    private val iterations: Int = ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
) {
    /** A new hash of [passcode], with a new salt. */
    fun hash(passcode: String): PasscodeHash {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return PasscodeHash(hash = derive(passcode, salt, iterations), salt = salt, iterations = iterations)
    }

    /** Whether [passcode] is the one [hash] was made from; compared in constant time. */
    fun matches(
        passcode: String,
        hash: PasscodeHash,
    ): Boolean = MessageDigest.isEqual(derive(passcode, hash.salt, hash.iterations), hash.hash)

    private fun derive(
        passcode: String,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val spec = PBEKeySpec(passcode.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    internal companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA512"

        /** OWASP's Password Storage Cheat Sheet for PBKDF2-HMAC-SHA512 (2023). */
        const val ITERATIONS = 210_000
        const val SALT_BYTES = 16
        const val KEY_BITS = 512
    }
}

/** A passcode's [hash], with the [salt] and the number of [iterations] it was made with. */
internal class PasscodeHash(
    val hash: ByteArray,
    val salt: ByteArray,
    val iterations: Int,
)
