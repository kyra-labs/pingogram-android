package dev.kyralabs.pingogram.core.domain.model

import java.time.Instant

/**
 * The header of a backup file (`docs/adr/0039-local-backup-and-restore.md`), which reads without the backup's password:
 * the [formatVersion] it is written in, the [appVersion] that wrote it, when it was made ([createdAt]) and which
 * [sections] it holds.
 */
data class BackupHeader(
    val formatVersion: Int,
    val appVersion: String,
    val createdAt: Instant,
    val sections: Set<BackupSection>,
)

/** A part of a backup: the app's settings, or each account's bookmarks, reminders, keyword hits and snippets. */
enum class BackupSection {
    Settings,
    Account,
}
