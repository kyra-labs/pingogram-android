package dev.kyralabs.pingogram.core.data.backup

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Backup
import dev.kyralabs.pingogram.core.domain.model.BackupFileInfo
import dev.kyralabs.pingogram.core.domain.model.BackupFolder
import dev.kyralabs.pingogram.core.domain.model.BackupHeader
import dev.kyralabs.pingogram.core.domain.model.BackupOpening
import dev.kyralabs.pingogram.core.domain.model.BackupRestored
import dev.kyralabs.pingogram.core.domain.model.BackupSection
import dev.kyralabs.pingogram.core.domain.repository.BackupRepository
import dev.kyralabs.pingogram.core.testing.FakeBackupSettingsRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId

/** [DocumentBackupFolderRepository] on a folder in memory and a fake backup writer. */
class DocumentBackupFolderRepositoryTest {
    private val documents = MemoryDocuments()
    private val settings = FakeBackupSettingsRepository()
    private val backups = FakeBackups()
    private val keys = MemoryBackupKeyStore()
    private val repository =
        DocumentBackupFolderRepository(documents, backups, settings, keys, FixedClock, Dispatchers.Unconfined)

    @Test
    fun `keeping a folder keeps its grant and gives back the one kept before`() =
        runTest {
            documents.folders += mapOf(TREE to "Backups", OTHER_TREE to "Old")
            repository.keep(OTHER_TREE)

            val kept = repository.keep(TREE)

            assertEquals(Outcome.Success(BackupFolder(TREE, "Backups", persistentListOf())), kept)
            assertEquals(TREE, settings.folderUri.value)
            assertEquals(setOf(TREE), documents.grants)
        }

    @Test
    fun `a folder that cannot be used is not kept`() =
        runTest {
            assertEquals(Outcome.Failure(DomainError.FileUnavailable), repository.keep(TREE))
            assertEquals(null, settings.folderUri.value)
        }

    @Test
    fun `backing up writes a dated file into the folder, which lists its backups newest first`() =
        runTest {
            documents.folders[TREE] = "Backups"
            repository.keep(TREE)
            documents.add(TREE, "notes.txt", modified = 9)
            val older = documents.add(TREE, "Pingogram backup 2026-11-01.pgbackup", modified = 1)

            val written = repository.backUp("pw")

            val file =
                BackupFileInfo("$TREE/3", "Pingogram backup 2026-11-18.pgbackup", Instant.ofEpochMilli(NOW_MILLIS), 2)
            assertEquals(Outcome.Success(file), written)
            assertEquals("pw", documents.contents.getValue(file.document).decodeToString())
            // Its password's key is kept for automatic backups.
            val key = checkNotNull(keys.kept.value)
            assertEquals(key.key, BackupCipher.key("pw", key.salt, key.rounds))
            assertEquals(
                BackupFolder(
                    TREE,
                    "Backups",
                    persistentListOf(
                        file,
                        BackupFileInfo(older, "Pingogram backup 2026-11-01.pgbackup", Instant.ofEpochMilli(1), 0),
                    ),
                ),
                repository.folder.first(),
            )
        }

    @Test
    fun `a backup that fails leaves no file behind, and none is written without a folder`() =
        runTest {
            assertEquals(Outcome.Failure(DomainError.FileUnavailable), repository.backUp("pw"))
            documents.folders[TREE] = "Backups"
            repository.keep(TREE)
            backups.fails = true

            assertEquals(Outcome.Failure(DomainError.Unknown(0, "full")), repository.backUp("pw"))
            assertEquals(emptyMap<String, ByteArray>(), documents.contents)
            assertEquals(null, keys.kept.value)
        }

    @Test
    fun `a file is opened, and its header read, from its own stream`() =
        runTest {
            documents.folders[TREE] = "Backups"
            val file = documents.add(TREE, "picked.pgbackup", modified = 5, content = "secret")

            assertEquals(HEADER, repository.header(file))
            assertEquals(BackupOpening.WrongPassword, repository.open(file, "guess"))
            assertEquals(BackupOpening.Opened(BACKUP), repository.open(file, "secret"))
            assertEquals(BackupOpening.NotABackup, repository.open("$TREE/gone", "secret"))
        }

    @Test
    fun `a folder whose grant was taken back reads as none`() =
        runTest {
            documents.folders[TREE] = "Backups"
            repository.keep(TREE)
            documents.folders.clear()

            assertEquals(null, repository.folder.first())
        }

    /** Folders by tree URI, files by "<tree>/<n>", the grants kept. */
    class MemoryDocuments : BackupDocuments {
        val folders = mutableMapOf<String, String>()
        val contents = mutableMapOf<String, ByteArray>()
        private val files = mutableMapOf<String, BackupDocument>()
        val grants = mutableSetOf<String>()

        fun add(
            treeUri: String,
            name: String,
            modified: Long,
            content: String = "",
        ): String {
            val document = "$treeUri/${files.size + 1}"
            files[document] = BackupDocument(document, name, modified, content.length.toLong())
            contents[document] = content.toByteArray()
            return document
        }

        override fun keepGrant(treeUri: String) {
            if (treeUri !in folders) throw SecurityException("No such folder")
            grants += treeUri
        }

        override fun releaseGrant(treeUri: String) {
            grants -= treeUri
        }

        override fun folderName(treeUri: String): String? = folders[treeUri]

        override fun files(treeUri: String): List<BackupDocument> =
            files.values.filter { it.document.startsWith("$treeUri/") }

        override fun file(document: String): BackupDocument? = files[document]

        override fun create(
            treeUri: String,
            name: String,
        ): String = add(treeUri, name, modified = NOW_MILLIS)

        override fun openInput(document: String): InputStream =
            ByteArrayInputStream(contents[document] ?: throw FileNotFoundException(document))

        override fun openOutput(document: String): OutputStream =
            object : ByteArrayOutputStream() {
                override fun close() {
                    contents[document] = toByteArray()
                    files[document] = files.getValue(document).copy(size = size().toLong())
                }
            }

        override fun delete(document: String): Boolean {
            files -= document
            return contents.remove(document) != null
        }
    }

    /** Writes the password as the backup; opens a backup with the password it holds. */
    private class FakeBackups : BackupRepository {
        var fails = false

        override suspend fun write(
            output: OutputStream,
            password: String,
        ): Outcome<BackupHeader> {
            if (fails) return Outcome.Failure(DomainError.Unknown(0, "full"))
            output.write(password.toByteArray())
            return Outcome.Success(HEADER)
        }

        override suspend fun header(input: InputStream): BackupHeader? = HEADER.takeIf { input.read() >= 0 }

        override suspend fun open(
            input: InputStream,
            password: String,
        ): BackupOpening =
            if (input.readBytes().decodeToString() ==
                password
            ) {
                BackupOpening.Opened(BACKUP)
            } else {
                BackupOpening.WrongPassword
            }

        override suspend fun restore(backup: Backup): Outcome<BackupRestored> =
            Outcome.Success(
                BackupRestored(settings = true, accounts = persistentListOf(), otherAccounts = persistentListOf()),
            )
    }

    object FixedClock : WallClock {
        override fun now(): Instant = Instant.ofEpochMilli(NOW_MILLIS)

        override fun zone(): ZoneId = ZoneId.of("UTC")

        override val minutes: SharedFlow<Instant> = MutableSharedFlow()
    }

    private companion object {
        const val TREE = "content://tree/backups"
        const val OTHER_TREE = "content://tree/old"
        val NOW_MILLIS = Instant.parse("2026-11-18T10:00:00Z").toEpochMilli()
        val HEADER = BackupHeader(1, "2.4.0", Instant.ofEpochSecond(1), persistentSetOf(BackupSection.Settings))
        val BACKUP = Backup(HEADER, settings = null, accounts = persistentListOf())
    }
}
