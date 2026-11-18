package dev.kyralabs.pingogram.core.data.backup

import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.BackupFileInfo
import dev.kyralabs.pingogram.core.domain.model.BackupFolder
import dev.kyralabs.pingogram.core.domain.model.BackupHeader
import dev.kyralabs.pingogram.core.domain.model.BackupOpening
import dev.kyralabs.pingogram.core.domain.repository.BackupFolderRepository
import dev.kyralabs.pingogram.core.domain.repository.BackupRepository
import dev.kyralabs.pingogram.core.domain.repository.BackupSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BackupFolderRepository] on [documents], the Storage Access Framework: the folder's tree URI kept in [settings], its
 * grant kept for good once picked (and given back when another one is), its backups the files whose name ends with
 * ".pgbackup", the newest first. Written and read through [backups], on the [io] dispatcher. Nothing about the folder
 * or its files is logged.
 */
@Singleton
class DocumentBackupFolderRepository
    @Inject
    constructor(
        private val documents: BackupDocuments,
        private val backups: BackupRepository,
        private val settings: BackupSettingsRepository,
        private val clock: WallClock,
        @param:Dispatcher(IO) private val io: CoroutineDispatcher,
    ) : BackupFolderRepository {
        /** Counts the backups written and folders kept, so [folder] reads the folder again. */
        private val changes = MutableStateFlow(0)

        override val folder: Flow<BackupFolder?> =
            combine(settings.folderUri, changes) { treeUri, _ -> treeUri }
                .map { treeUri -> treeUri?.let(::read) }
                .flowOn(io)

        override suspend fun keep(treeUri: String): Outcome<BackupFolder> =
            withContext(io) {
                val previous = settings.folderUri.first()
                val kept = guarded { documents.keepGrant(treeUri) }
                val folder = if (kept) read(treeUri) else null
                if (folder == null) {
                    Outcome.Failure(DomainError.FileUnavailable)
                } else {
                    settings.setFolderUri(treeUri).then {
                        if (previous != null && previous != treeUri) guarded { documents.releaseGrant(previous) }
                        changes.update { it + 1 }
                        Outcome.Success(folder)
                    }
                }
            }

        override suspend fun backUp(password: String): Outcome<BackupFileInfo> = backUp { backups.write(it, password) }

        /** Writes a dated backup into the kept folder with [write]; a file that fails is deleted. */
        internal suspend fun backUp(write: suspend (OutputStream) -> Outcome<BackupHeader>): Outcome<BackupFileInfo> =
            withContext(io) {
                val treeUri = settings.folderUri.first()
                val date = clock.now().atZone(clock.zone()).toLocalDate()
                val document = treeUri?.let { guardedOrNull { documents.create(it, "$NAME $date$EXTENSION") } }
                val written = document?.let { file -> guardedOrNull { documents.openOutput(file).use { write(it) } } }
                val info = document?.takeIf { written is Outcome.Success }?.let { guardedOrNull { documents.file(it) } }
                if (document != null && info == null) guarded { documents.delete(document) }
                changes.update { it + 1 }
                when {
                    written is Outcome.Failure -> written
                    info == null -> Outcome.Failure(DomainError.FileUnavailable)
                    else -> Outcome.Success(info.info())
                }
            }

        override suspend fun file(document: String): BackupFileInfo? =
            withContext(io) { guardedOrNull { documents.file(document) }?.info() }

        override suspend fun header(document: String): BackupHeader? =
            withContext(io) { guardedOrNull { documents.openInput(document).use { backups.header(it) } } }

        override suspend fun open(
            document: String,
            password: String,
        ): BackupOpening =
            withContext(io) {
                guardedOrNull { documents.openInput(document).use { backups.open(it, password) } }
                    ?: BackupOpening.NotABackup
            }

        /** Deletes the kept folder's backups but the newest [kept]; what cannot be deleted stays. */
        internal suspend fun prune(kept: Int) {
            withContext(io) {
                val treeUri = settings.folderUri.first() ?: return@withContext
                read(treeUri)?.backups?.drop(kept)?.forEach { guarded { documents.delete(it.document) } }
                changes.update { it + 1 }
            }
        }

        /** Folder [treeUri] with its backups; null when it is gone or its grant was taken back. */
        private fun read(treeUri: String): BackupFolder? =
            guardedOrNull {
                documents.folderName(treeUri)?.let { name ->
                    val files = documents.files(treeUri).filter { it.name.endsWith(EXTENSION) }
                    BackupFolder(treeUri, name, files.map { it.info() }.sortedByDescending { it.modifiedAt })
                }
            }

        private companion object {
            const val NAME = "Pingogram backup"
            const val EXTENSION = ".pgbackup"
        }
    }

private fun BackupDocument.info() = BackupFileInfo(document, name, Instant.ofEpochMilli(modifiedMillis), size)

/** Whether [action] ran without the Storage Access Framework's failures. */
private inline fun guarded(action: () -> Unit): Boolean = guardedOrNull(action) != null

/** What [action] answers; null when it fails as the Storage Access Framework fails. */
private inline fun <T> guardedOrNull(action: () -> T): T? =
    try {
        action()
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
