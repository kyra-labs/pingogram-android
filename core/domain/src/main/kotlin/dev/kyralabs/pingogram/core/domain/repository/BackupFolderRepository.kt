package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BackupFileInfo
import dev.kyralabs.pingogram.core.domain.model.BackupFolder
import dev.kyralabs.pingogram.core.domain.model.BackupHeader
import dev.kyralabs.pingogram.core.domain.model.BackupOpening
import kotlinx.coroutines.flow.Flow

/**
 * Backups in a folder the user picks (`docs/adr/0039-local-backup-and-restore.md`) through the Storage Access
 * Framework, with no storage permission: the folder's grant is kept for as long as the app is installed, so the app
 * finds its backups after the user logged out and in again; after a reinstall the user picks a file instead.
 */
interface BackupFolderRepository {
    /** The kept folder with its backups, null while none is kept or it is gone; then every change. */
    val folder: Flow<BackupFolder?>

    /**
     * Keeps folder [treeUri], just picked (a tree URI), in place of the folder kept before, whose grant is given back;
     * fails with `DomainError.FileUnavailable` if it cannot be used.
     */
    suspend fun keep(treeUri: String): Outcome<BackupFolder>

    /**
     * Writes a backup encrypted with [password] into the kept folder as "Pingogram backup YYYY-MM-DD.pgbackup" and
     * answers it; fails with `DomainError.FileUnavailable` without a folder, or if the file cannot be written (nothing
     * half-written is left).
     */
    suspend fun backUp(password: String): Outcome<BackupFileInfo>

    /** The backup file [document] (in the folder, or picked by the user): its name, date and size; null if gone. */
    suspend fun file(document: String): BackupFileInfo?

    /** The header of backup [document], which needs no password; null when it is not a backup. */
    suspend fun header(document: String): BackupHeader?

    /** Opens backup [document] with [password]: what it holds, to show before restoring it. Slow. */
    suspend fun open(
        document: String,
        password: String,
    ): BackupOpening
}
