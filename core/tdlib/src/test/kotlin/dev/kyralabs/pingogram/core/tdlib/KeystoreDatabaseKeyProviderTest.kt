package dev.kyralabs.pingogram.core.tdlib

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.InvalidKeyException
import java.security.KeyStoreException
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class KeystoreDatabaseKeyProviderTest {
    @TempDir
    lateinit var noBackupFilesDir: File

    private val keyStore = SoftwareWrappingKeyStore()
    private val log = RecordingTdlibLog()
    private val keyFile get() = noBackupFilesDir.resolve(KeystoreDatabaseKeyProvider.KEY_FILE)

    /** A new instance each time, as after a process restart. */
    private fun provider() = KeystoreDatabaseKeyProvider(keyStore, { keyFile }, SecureRandom(), log)

    @Test
    fun `the first key is 32 bytes and new`() {
        val key = provider().databaseKey()

        assertEquals(32 to true, key.bytes.size to key.isNew)
    }

    @Test
    fun `later calls return the same key, not new`() {
        val first = provider().databaseKey()

        val second = provider().databaseKey()

        assertTrue(first.bytes.contentEquals(second.bytes) && !second.isNew)
    }

    @Test
    fun `the stored file holds the key only in encrypted form`() {
        val key = provider().databaseKey()

        val stored = keyFile.readBytes()

        assertFalse(stored.toList().windowed(key.bytes.size).any { it == key.bytes.toList() })
    }

    @Test
    fun `a deleted Keystore key leads to a new key`() {
        val first = provider().databaseKey()
        keyStore.key = null

        val second = provider().databaseKey()

        assertTrue(second.isNew && !second.bytes.contentEquals(first.bytes))
    }

    @Test
    fun `a permanently invalidated Keystore key leads to a new key`() {
        provider().databaseKey()
        keyStore.key = SecretKeySpec(ByteArray(7), "AES") // Cipher.init throws InvalidKeyException
        keyStore.invalidKeyIsPermanent = true

        assertTrue(provider().databaseKey().isNew)
    }

    @Test
    fun `a corrupt key file leads to a new key`() {
        provider().databaseKey()
        keyFile.writeBytes(byteArrayOf(1, 12, 0))

        assertTrue(provider().databaseKey().isNew)
    }

    @Test
    fun `a key file encrypted with another Keystore key leads to a new key`() {
        provider().databaseKey()
        keyStore.create()

        assertTrue(provider().databaseKey().isNew)
    }

    @Test
    fun `a new key replaces the stored one`() {
        provider().databaseKey()
        keyStore.key = null
        val replacement = provider().databaseKey()

        assertArrayEquals(replacement.bytes, provider().databaseKey().bytes)
    }

    @Test
    fun `a transient Keystore failure is thrown instead of costing the session`() {
        provider().databaseKey()
        keyStore.failure = KeyStoreException("Keystore is busy")

        assertThrows<KeyStoreException> { provider().databaseKey() }
    }

    @Test
    fun `a key that is invalid only for now is thrown instead of costing the session`() {
        provider().databaseKey()
        keyStore.key = SecretKeySpec(ByteArray(7), "AES")

        assertThrows<InvalidKeyException> { provider().databaseKey() }
    }

    @Test
    fun `discard forgets the key`() {
        val provider = provider()
        provider.databaseKey()

        provider.discard()

        assertTrue(provider.databaseKey().isNew)
    }

    private class SoftwareWrappingKeyStore : WrappingKeyStore {
        var key: SecretKey? = null
        var failure: KeyStoreException? = null
        var invalidKeyIsPermanent = false

        override fun get(): SecretKey? {
            failure?.let { throw it }
            return key
        }

        override fun create(): SecretKey =
            KeyGenerator
                .getInstance("AES")
                .apply { init(256) }
                .generateKey()
                .also { key = it }

        override fun delete() {
            key = null
        }

        override fun isPermanentlyInvalid(e: InvalidKeyException) = invalidKeyIsPermanent
    }
}
