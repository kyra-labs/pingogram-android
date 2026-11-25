package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import java.time.Instant

/**
 * The folder the user picked for backups (`docs/adr/0039-local-backup-and-restore.md`): its tree URI ([treeUri]), the
 * [name] its provider gives it, and the backups in it ([backups]), the newest first.
 */
data class BackupFolder(
    val treeUri: String,
    val name: String,
    val backups: ImmutableList<BackupFileInfo>,
)

/**
 * A backup file, in the backup folder or picked by the user: its [document] URI, its file [name], when it was last
 * written ([modifiedAt]) and its size in bytes ([sizeBytes]).
 */
data class BackupFileInfo(
    val document: String,
    val name: String,
    val modifiedAt: Instant,
    val sizeBytes: Long,
)
