package dev.kyralabs.pingogram.core.tdlib

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.InvalidKeyException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class AccountSlotTest {
    @TempDir
    lateinit var dir: File

    private val filesDir get() = dir.resolve("files")
    private val noBackupFilesDir get() = dir.resolve("no_backup")

    private fun names(slot: AccountSlot) =
        listOf(
            TdlibDirectories.of(filesDir, useTestDc = false, slot).root.name,
            TdlibDirectories.of(filesDir, useTestDc = true, slot).root.name,
            KeystoreDatabaseKeyProvider.keyFileName(slot),
            AndroidKeystoreWrappingKeyStore.aliasOf(slot),
        )

    @Test
    fun `the first account keeps the names of before multiple accounts`() {
        assertEquals(
            listOf("tdlib", "tdlib-test", "tdlib_database_key", "pingogram.tdlib.database_key"),
            names(AccountSlot.First),
        )
    }

    @Test
    fun `the default directories are the first account's`() {
        assertEquals(
            TdlibDirectories.of(filesDir, useTestDc = false, AccountSlot.First).root,
            TdlibDirectories.of(filesDir, useTestDc = false).root,
        )
    }

    @Test
    fun `other accounts add their index`() {
        assertEquals(
            listOf("tdlib-2", "tdlib-test-2", "tdlib_database_key_2", "pingogram.tdlib.database_key.2"),
            names(AccountSlot(2)),
        )
    }

    @Test
    fun `there are four slots, as Telegram's app allows four accounts`() {
        AccountSlot(AccountSlot.MAX_ACCOUNTS - 1)

        assertThrows<IllegalArgumentException> { AccountSlot(AccountSlot.MAX_ACCOUNTS) }
        assertThrows<IllegalArgumentException> { AccountSlot(-1) }
    }

    @Test
    fun `deleting an account removes its directories, key file and Keystore key, and nothing of the others`() {
        val keys = mutableMapOf(AccountSlot.First to RecordedKey(), AccountSlot(1) to RecordedKey())
        val storage = AccountStorage({ filesDir }, { noBackupFilesDir }, { keys.getValue(it) })
        val kept = listOf(AccountSlot.First, AccountSlot(1)).associateWith(::createStorage)

        storage.delete(AccountSlot(1))

        assertEquals(listOf(true, false), kept.values.map { files -> files.all(File::exists) })
        assertEquals(listOf(false, true), keys.values.map { it.deleted })
    }

    /** Both directories with a file each, and the key file. */
    private fun createStorage(slot: AccountSlot): List<File> =
        listOf(false, true).map { testDc ->
            TdlibDirectories.of(filesDir, testDc, slot).database.resolve("db.sqlite").apply {
                parentFile.mkdirs()
                writeText("database")
            }
        } +
            noBackupFilesDir.resolve(KeystoreDatabaseKeyProvider.keyFileName(slot)).apply {
                parentFile.mkdirs()
                writeText("key")
            }

    private class RecordedKey : WrappingKeyStore {
        var deleted = false

        override fun get(): SecretKey = SecretKeySpec(ByteArray(32), "AES")

        override fun create(): SecretKey = get()

        override fun delete() {
            deleted = true
        }

        override fun isPermanentlyInvalid(e: InvalidKeyException) = false
    }
}
