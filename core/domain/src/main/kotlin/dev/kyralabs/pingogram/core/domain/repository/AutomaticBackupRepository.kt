package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BackupFileInfo
import dev.kyralabs.pingogram.core.domain.model.BackupFrequency
import kotlinx.coroutines.flow.Flow

/**
 * Backups that write themselves (`docs/adr/0039-local-backup-and-restore.md`, "Automatic backups"): into the kept
 * folder, encrypted with a key made from the password of the last backup the user wrote and kept in Android's
 * Keystore (the password itself is never kept), the newest 3 kept. Scheduling them is WorkManager's.
 */
interface AutomaticBackupRepository {
    /**
     * How often they run: the user's choice, else Weekly once a folder is kept; Off while no folder is kept. Then
     * every change.
     */
    val frequency: Flow<BackupFrequency>

    /** Whether they can run: a folder is kept, and a key was kept at the last backup written with a password. */
    val isReady: Flow<Boolean>

    /** Saves how often they run; fails with `DomainError.Unknown` if it cannot be written. */
    suspend fun setFrequency(frequency: BackupFrequency): Outcome<Unit>

    /**
     * Writes a backup with the kept key into the kept folder, then deletes the folder's older backups but the newest
     * 3; fails with `DomainError.FileUnavailable` without a folder, a key, or room to write.
     */
    suspend fun backUp(): Outcome<BackupFileInfo>
}
