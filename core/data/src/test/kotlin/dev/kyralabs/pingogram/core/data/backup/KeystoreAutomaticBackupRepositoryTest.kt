package dev.kyralabs.pingogram.core.data.backup

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.backup.DocumentBackupFolderRepositoryTest.FixedClock
import dev.kyralabs.pingogram.core.data.backup.DocumentBackupFolderRepositoryTest.MemoryDocuments
import dev.kyralabs.pingogram.core.domain.model.BackupFrequency
import dev.kyralabs.pingogram.core.domain.model.BackupHeader
import dev.kyralabs.pingogram.core.domain.model.BackupSection
import dev.kyralabs.pingogram.core.testing.FakeBackupRepository
import dev.kyralabs.pingogram.core.testing.FakeBackupSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.OutputStream
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

/**
 * [KeystoreAutomaticBackupRepository] on a folder in memory: Weekly once a folder is kept, ready with a kept key, and
 * a backup written with that key, the folder then keeping its newest 3 backups.
 */
class KeystoreAutomaticBackupRepositoryTest {
    private val documents = MemoryDocuments()
    private val settings = FakeBackupSettingsRepository()
    private val keys = MemoryBackupKeyStore()
    private val folders =
        DocumentBackupFolderRepository(documents, FakeBackupRepository(), settings, FixedClock, Dispatchers.Unconfined)
    private val writtenWith = mutableListOf<BackupKey>()
    private var fails = false
    private val repository =
        KeystoreAutomaticBackupRepository(settings, keys, folders, ::write, Dispatchers.Unconfined)

    private fun write(
        output: OutputStream,
        key: BackupKey,
    ): Outcome<BackupHeader> {
        if (fails) return Outcome.Failure(DomainError.Unknown(0, "full"))
        writtenWith += key
        output.write(1)
        return Outcome.Success(HEADER)
    }

    @Test
    fun `off without a folder, then weekly until another frequency is chosen`() =
        runTest {
            assertEquals(BackupFrequency.Off, repository.frequency.first())

            settings.setFolderUri(TREE)
            assertEquals(BackupFrequency.Weekly, repository.frequency.first())

            repository.setFrequency(BackupFrequency.Daily)
            assertEquals(BackupFrequency.Daily, repository.frequency.first())
            settings.setFolderUri(null)
            assertEquals(BackupFrequency.Off, repository.frequency.first())
        }

    @Test
    fun `ready only with a folder and a kept key, and without a key nothing is written`() =
        runTest {
            documents.folders[TREE] = "Backups"
            folders.keep(TREE)
            assertEquals(false, repository.isReady.first())
            assertEquals(Outcome.Failure(DomainError.FileUnavailable), repository.backUp())
            assertEquals(emptyMap<String, ByteArray>(), documents.contents)

            keys.keep(KEY)
            assertEquals(true, repository.isReady.first())
        }

    @Test
    fun `a backup is written with the kept key, and the folder keeps its newest 3`() =
        runTest {
            documents.folders[TREE] = "Backups"
            folders.keep(TREE)
            keys.keep(KEY)
            val notes = documents.add(TREE, "notes.txt", modified = 1)
            val oldest = documents.add(TREE, "Pingogram backup 2026-10-28.pgbackup", modified = 2)
            val old = documents.add(TREE, "Pingogram backup 2026-11-04.pgbackup", modified = 3)
            val recent = documents.add(TREE, "Pingogram backup 2026-11-11.pgbackup", modified = 4)

            val written = repository.backUp() as Outcome.Success

            assertEquals(listOf(KEY), writtenWith)
            assertEquals("Pingogram backup 2026-11-18.pgbackup", written.value.name)
            assertEquals(setOf(notes, old, recent, written.value.document), documents.contents.keys)
            assertEquals(false, oldest in documents.contents)
        }

    @Test
    fun `a backup that fails deletes nothing`() =
        runTest {
            documents.folders[TREE] = "Backups"
            folders.keep(TREE)
            keys.keep(KEY)
            val backups = (1..3L).map { documents.add(TREE, "Pingogram backup 2026-11-0$it.pgbackup", modified = it) }
            fails = true

            assertEquals(Outcome.Failure(DomainError.Unknown(0, "full")), repository.backUp())
            assertEquals(backups.toSet(), documents.contents.keys)
        }

    private companion object {
        const val TREE = "content://tree/backups"
        val KEY = BackupKey(SecretKeySpec(ByteArray(32), "AES"), ByteArray(16), 1)
        val HEADER = BackupHeader(1, "2.4.0", Instant.ofEpochSecond(1), setOf(BackupSection.Settings))
    }
}
