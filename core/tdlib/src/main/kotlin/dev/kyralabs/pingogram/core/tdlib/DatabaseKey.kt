package dev.kyralabs.pingogram.core.tdlib

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/** TDLib's `databaseEncryptionKey`. [isNew]: no database encrypted with an earlier key can be opened with it. */
class DatabaseKey(
    val bytes: ByteArray,
    val isNew: Boolean,
)

/** Blocking (file and Keystore IO): call off the main thread. */
interface DatabaseKeyProvider {
    fun databaseKey(): DatabaseKey

    /** Forgets the key; the next [databaseKey] is a new one. */
    fun discard()
}

/**
 * The database key per `docs/TDLIB.md`: 32 random bytes generated once, encrypted (AES-256-GCM) with a key that
 * lives in the Android Keystore and never leaves it, and stored as IV + ciphertext in `noBackupFilesDir`, which is
 * excluded from backups and device transfers.
 *
 * If the stored key can no longer be decrypted (the Keystore key was deleted or permanently invalidated, or the
 * file is corrupt), a new key is generated and reported as [DatabaseKey.isNew]: TDLib could not open the old
 * database with it, so the caller deletes the database and the user logs in again. Transient failures (IO, a busy
 * Keystore) are thrown instead, so they never cost the session.
 */
class KeystoreDatabaseKeyProvider internal constructor(
    private val wrappingKeys: WrappingKeyStore,
    private val keyFile: () -> File,
    private val random: SecureRandom,
    private val log: TdlibLog,
) : DatabaseKeyProvider {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        config: TdlibConfig,
        slot: AccountSlot,
    ) : this(
        wrappingKeys = AndroidKeystoreWrappingKeyStore(slot),
        // Lazy: Context.getNoBackupFilesDir() touches the disk, and this class is created on the main thread.
        keyFile = { context.noBackupFilesDir.resolve(keyFileName(slot)) },
        random = SecureRandom(),
        log = AndroidTdlibLog(config.isDebug),
    )

    @Synchronized
    override fun databaseKey(): DatabaseKey {
        val file = keyFile()
        val stored = if (file.isFile) StoredKey.parse(file.readBytes()) else null
        stored?.let(::unwrap)?.let { return DatabaseKey(it, isNew = false) }
        return DatabaseKey(createKey(file), isNew = true)
    }

    @Synchronized
    override fun discard() {
        keyFile().delete()
        wrappingKeys.delete()
    }

    private fun createKey(file: File): ByteArray {
        val key = ByteArray(KEY_SIZE_BYTES).also(random::nextBytes)
        // A fresh wrapping key too, so an invalidated one is never reused.
        wrappingKeys.delete()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Without parameters the provider picks a random IV; the Android Keystore insists on doing so.
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKeys.create())
        writeAtomically(file, StoredKey(cipher.iv, cipher.doFinal(key)).toBytes())
        return key
    }

    /** The database key, or null when it can never be decrypted again. */
    private fun unwrap(stored: StoredKey): ByteArray? =
        try {
            val wrappingKey = wrappingKeys.get()
            if (wrappingKey == null) log.warn("The Keystore key that encrypts the database key is gone")
            wrappingKey?.let {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, it, GCMParameterSpec(TAG_SIZE_BITS, stored.iv))
                cipher.doFinal(stored.ciphertext).takeIf { key -> key.size == KEY_SIZE_BYTES }
            }
        } catch (e: GeneralSecurityException) {
            if (!isPermanent(e)) throw e
            log.warn("The stored database key can no longer be decrypted", e)
            null
        }

    /**
     * The Keystore entry is unreadable, the key was invalidated (e.g. by the OS), or the file does not belong to this
     * key or was corrupted. Anything else may be transient and is not worth the session.
     */
    private fun isPermanent(e: GeneralSecurityException) =
        e is UnrecoverableKeyException ||
            e is AEADBadTagException ||
            (e is InvalidKeyException && wrappingKeys.isPermanentlyInvalid(e))

    /** Version, IV length, IV, ciphertext (with the GCM tag). */
    private class StoredKey(
        val iv: ByteArray,
        val ciphertext: ByteArray,
    ) {
        fun toBytes(): ByteArray =
            ByteBuffer
                .allocate(2 + iv.size + ciphertext.size)
                .put(FORMAT_VERSION)
                .put(iv.size.toByte())
                .put(iv)
                .put(ciphertext)
                .array()

        companion object {
            /** Null for anything that is not a stored key in this format. */
            fun parse(bytes: ByteArray): StoredKey? {
                val ivSize = if (bytes.size > 2 && bytes[0] == FORMAT_VERSION) bytes[1].toInt() else 0
                return if (ivSize > 0 && bytes.size > 2 + ivSize) {
                    StoredKey(bytes.copyOfRange(2, 2 + ivSize), bytes.copyOfRange(2 + ivSize, bytes.size))
                } else {
                    null
                }
            }
        }
    }

    internal companion object {
        const val KEY_FILE = "tdlib_database_key"

        /** [KEY_FILE] for the first account, as before multiple accounts; "tdlib_database_key_1" for the second. */
        fun keyFileName(slot: AccountSlot) = KEY_FILE + slot.suffix("_")

        const val KEY_SIZE_BYTES = 32
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_SIZE_BITS = 128
        private const val FORMAT_VERSION: Byte = 1

        /** Replaces [file] in one step and flushes it to disk: a key lost to a crash would cost the session. */
        private fun writeAtomically(
            file: File,
            bytes: ByteArray,
        ) {
            file.parentFile?.mkdirs()
            val temporary = File(file.path + ".tmp")
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }
    }
}

/** The AES key that encrypts the database key. */
internal interface WrappingKeyStore {
    /** The key, or null if there is none. Throws [UnrecoverableKeyException] if it cannot be read. */
    fun get(): SecretKey?

    fun create(): SecretKey

    fun delete()

    /** Whether [e], thrown while using the key, means it is permanently unusable. */
    fun isPermanentlyInvalid(e: InvalidKeyException): Boolean
}

/**
 * AES-256-GCM key in the Android Keystore, one per account. Usable without user authentication or an unlocked screen,
 * so TDLib can start in the background.
 */
internal class AndroidKeystoreWrappingKeyStore(
    slot: AccountSlot,
) : WrappingKeyStore {
    private val alias = aliasOf(slot)

    private val keyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    override fun get(): SecretKey? = keyStore.getKey(alias, null) as SecretKey?

    override fun create(): SecretKey {
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    override fun delete() {
        keyStore.deleteEntry(alias)
    }

    override fun isPermanentlyInvalid(e: InvalidKeyException) = e is KeyPermanentlyInvalidatedException

    internal companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "pingogram.tdlib.database_key"
        private const val KEY_SIZE_BITS = 256

        /** [ALIAS] for the first account, as before multiple accounts; "….database_key.1" for the second. */
        fun aliasOf(slot: AccountSlot) = ALIAS + slot.suffix(".")
    }
}
