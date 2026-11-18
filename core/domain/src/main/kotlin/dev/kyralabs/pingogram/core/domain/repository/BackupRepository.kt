package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Backup
import dev.kyralabs.pingogram.core.domain.model.BackupHeader
import dev.kyralabs.pingogram.core.domain.model.BackupOpening
import dev.kyralabs.pingogram.core.domain.model.BackupRestored
import java.io.InputStream
import java.io.OutputStream

/**
 * Backups of what Pingogram keeps on this phone only (`docs/adr/0039-local-backup-and-restore.md`): the settings and,
 * for each account on the phone, its bookmarks, reminders, keyword hits and snippets, in one file encrypted with a
 * password the user chooses. Nothing of TDLib's, no message, no media, no session: signing in brings those back. The
 * caller opens the file (the Storage Access Framework) and closes it; nothing is sent anywhere.
 */
interface BackupRepository {
    /**
     * Writes a backup of the settings and of every signed-in account (never the demo) into [output], encrypted with
     * [password], and answers its header; the password's key is then kept for automatic backups
     * ([AutomaticBackupRepository]), never the password. Slow (the key's rounds); fails with `DomainError.Unknown` if
     * [output] cannot be written.
     */
    suspend fun write(
        output: OutputStream,
        password: String,
    ): Outcome<BackupHeader>

    /** The header of the backup in [input], which needs no password: its date, for one; null when it is not one. */
    suspend fun header(input: InputStream): BackupHeader?

    /** Opens the backup in [input] with [password]: what it holds, to show before restoring it. Slow. */
    suspend fun open(
        input: InputStream,
        password: String,
    ): BackupOpening

    /**
     * Restores [backup]: its settings replace the current ones, except what never goes into backups (the passcode
     * stays as it is); each account's data merges into the account on the phone with the same Telegram user id only,
     * the newer of two entries for one message winning and past reminders restored as done. Deletes nothing and sends
     * nothing to Telegram. Fails with `DomainError.Unknown` if the settings or an account's data cannot be written.
     */
    suspend fun restore(backup: Backup): Outcome<BackupRestored>
}
