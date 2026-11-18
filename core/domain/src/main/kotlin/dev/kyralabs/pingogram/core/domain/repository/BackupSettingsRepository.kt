package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * What the backups (`docs/adr/0039-local-backup-and-restore.md`) keep on this phone, never in a backup: the folder the
 * user picked, and the restore card's "Not now". Never fails to read: what cannot be read is none.
 */
interface BackupSettingsRepository {
    /** The backup folder's tree URI; null while none is kept. */
    val folderUri: Flow<String?>

    /** Keeps [treeUri] as the backup folder, or none; fails with `DomainError.Unknown` if it cannot be written. */
    suspend fun setFolderUri(treeUri: String?): Outcome<Unit>

    /** The backup (by document URI) whose restore card the user hid with "Not now"; null for none. */
    val restoreDismissedFor: Flow<String?>

    /** Hides the restore card of backup [document]; fails as [setFolderUri]. */
    suspend fun dismissRestore(document: String): Outcome<Unit>

    /** Whether the user hid the card that offers to restore from a file while no folder is kept. */
    val restorePromptDismissed: Flow<Boolean>

    /** Hides that card for good; fails as [setFolderUri]. */
    suspend fun dismissRestorePrompt(): Outcome<Unit>
}
