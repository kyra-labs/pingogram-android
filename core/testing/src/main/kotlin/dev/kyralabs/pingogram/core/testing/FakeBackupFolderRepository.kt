package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BackupFileInfo
import dev.kyralabs.pingogram.core.domain.model.BackupFolder
import dev.kyralabs.pingogram.core.domain.model.BackupHeader
import dev.kyralabs.pingogram.core.domain.model.BackupOpening
import dev.kyralabs.pingogram.core.domain.repository.BackupFolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/**
 * [BackupFolderRepository] in memory: tests set the kept [folder], the backup files there or picked ([files], with
 * their [headers]), what opening one answers ([opening]), and [failure] for the writes. [passwords] are those backed
 * up with, in order.
 */
class FakeBackupFolderRepository : BackupFolderRepository {
    override val folder = MutableStateFlow<BackupFolder?>(null)
    val files = mutableMapOf<String, BackupFileInfo>()
    val headers = mutableMapOf<String, BackupHeader>()
    var opening: BackupOpening = BackupOpening.NotABackup
    var failure: DomainError? = null
    val passwords = mutableListOf<String>()

    override suspend fun keep(treeUri: String): Outcome<BackupFolder> {
        failure?.let { return Outcome.Failure(it) }
        val kept = BackupFolder(treeUri, name = treeUri.substringAfterLast('/'), backups = emptyList())
        folder.value = kept
        return Outcome.Success(kept)
    }

    override suspend fun backUp(password: String): Outcome<BackupFileInfo> {
        val current = folder.value
        val error = failure ?: DomainError.FileUnavailable.takeIf { current == null }
        if (error != null || current == null) return Outcome.Failure(error ?: DomainError.FileUnavailable)
        passwords += password
        val file = BackupFileInfo("${current.treeUri}/${current.backups.size}", BACKUP_NAME, BACKED_UP_AT, SIZE)
        files[file.document] = file
        folder.value = current.copy(backups = listOf(file) + current.backups)
        return Outcome.Success(file)
    }

    override suspend fun file(document: String): BackupFileInfo? = files[document]

    override suspend fun header(document: String): BackupHeader? = headers[document]

    override suspend fun open(
        document: String,
        password: String,
    ): BackupOpening = opening

    companion object {
        const val BACKUP_NAME = "Pingogram backup 2026-11-18.pgbackup"
        const val SIZE = 4_096L
        val BACKED_UP_AT: Instant = Instant.parse("2026-11-18T10:00:00Z")
    }
}
