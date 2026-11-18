package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BackupFrequency
import kotlinx.coroutines.flow.Flow

/**
 * What the backups (`docs/adr/0039-local-backup-and-restore.md`) keep on this phone, never in a backup: the folder the
 * user picked, how often backups write themselves, a sign-in that offers the restore card, and the card's "Not now".
 * Never fails to read: what cannot be read is none.
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

    /**
     * Whether the card that offers to restore from a file (while no folder is kept) shows: from a sign-in on this phone
     * until the user uses or hides it.
     */
    val restorePromptPending: Flow<Boolean>

    /**
     * Notes a sign-in that just completed, so the chat list offers to restore from a file. Written in the app's scope,
     * so leaving the login screen does not cancel it; a write that fails offers nothing.
     */
    fun offerRestorePrompt()

    /** Hides that card until the next sign-in; fails as [setFolderUri]. */
    suspend fun dismissRestorePrompt(): Outcome<Unit>

    /** How often backups write themselves, as the user chose; null while they have not chosen. */
    val frequency: Flow<BackupFrequency?>

    /** Saves how often backups write themselves; fails as [setFolderUri]. */
    suspend fun setFrequency(frequency: BackupFrequency): Outcome<Unit>
}
