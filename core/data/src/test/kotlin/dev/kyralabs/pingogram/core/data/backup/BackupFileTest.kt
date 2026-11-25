package dev.kyralabs.pingogram.core.data.backup

import dev.kyralabs.pingogram.core.domain.model.BackupHeader
import dev.kyralabs.pingogram.core.domain.model.BackupSection
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * Backup files ([BackupFile]) written and read back: the header reads without the password, the body only with it, a
 * changed file not at all, and one of a newer format is refused. On Robolectric for Android's `org.json`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupFileTest {
    private val header =
        BackupHeader(
            formatVersion = BackupFile.FORMAT_VERSION,
            appVersion = "2.4.0",
            createdAt = Instant.ofEpochSecond(1_763_000_000),
            sections = persistentSetOf(BackupSection.Settings, BackupSection.Account),
        )

    private fun backup(
        header: BackupHeader = this.header,
        body: String = BODY,
    ): ByteArray {
        val salt = BackupCipher.newSalt()
        val key = BackupKey(BackupCipher.key(PASSWORD, salt, ROUNDS), salt, ROUNDS)
        return ByteArrayOutputStream().also { BackupFile.write(it, header, key, body) }.toByteArray()
    }

    @Test
    fun `a backup reads back with its password, header and body`() {
        assertEquals(BackupFileRead.Read(header, BODY), BackupFile.read(backup().inputStream(), PASSWORD))
    }

    @Test
    fun `its header reads without the password`() {
        assertEquals(header, BackupFile.readHeader(backup().inputStream()))
    }

    @Test
    fun `another password does not read it`() {
        assertEquals(BackupFileRead.WrongPassword, BackupFile.read(backup().inputStream(), "Password"))
    }

    @Test
    fun `a changed header does not read either, as the encryption covers it`() {
        val bytes = backup()
        val changed = bytes.copyOf().also { it[it.indexOfVersion()] = '3'.code.toByte() }

        assertEquals(BackupFileRead.WrongPassword, BackupFile.read(changed.inputStream(), PASSWORD))
    }

    @Test
    fun `a backup of a newer format is refused before its password is tried`() {
        val newer = header.copy(formatVersion = BackupFile.FORMAT_VERSION + 1)

        assertEquals(BackupFileRead.Newer(newer), BackupFile.read(backup(newer).inputStream(), "any"))
    }

    @Test
    fun `a file that is not a backup, or is cut short, reads as none`() {
        assertEquals(BackupFileRead.NotABackup, BackupFile.read("hello".toByteArray().inputStream(), PASSWORD))
        val whole = backup()
        assertEquals(
            BackupFileRead.WrongPassword,
            BackupFile.read(whole.copyOf(whole.size - 5).inputStream(), PASSWORD),
        )
        assertEquals(null, BackupFile.readHeader(backup().copyOf(20).inputStream()))
    }

    /** Where the app version's "2" is in the header. */
    private fun ByteArray.indexOfVersion(): Int {
        val version = "2.4.0".toByteArray()
        return (0..size - version.size).first { start -> version.indices.all { this[start + it] == version[it] } }
    }

    private companion object {
        const val PASSWORD = "correct horse"
        const val ROUNDS = 1_000
        const val BODY = """{"settings":{}}"""
    }
}
