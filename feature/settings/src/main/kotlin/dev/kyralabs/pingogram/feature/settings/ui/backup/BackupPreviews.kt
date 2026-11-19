package dev.kyralabs.pingogram.feature.settings.ui.backup

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.domain.model.BackupFileInfo
import dev.kyralabs.pingogram.core.domain.model.BackupFrequency
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import java.time.Instant

private val LastBackup =
    BackupFileInfo(
        document = "content://tree/backups/1",
        name = "Pingogram backup 2026-11-18.pgbackup",
        modifiedAt = Instant.parse("2026-11-18T10:00:00Z"),
        sizeBytes = 18_432,
    )

@Composable
private fun Sample(state: BackupUiState) {
    PingogramPreview { BackupScreen(state = state, onEvent = {}, onPickFolder = {}, onPickFile = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun BackupLoadingPreview() = Sample(BackupUiState.Loading)

@PingogramPreviews
@Composable
private fun BackupNoFolderPreview() = Sample(BackupUiState.Content())

/** A folder just picked: weekly backups chosen, waiting for the first backup with a password. */
@PingogramPreviews
@Composable
private fun BackupNoBackupYetPreview() =
    Sample(BackupUiState.Content(folderName = "Backups", automatic = BackupFrequency.Weekly))

@PingogramPreviews
@Composable
private fun BackupContentPreview() =
    Sample(
        BackupUiState.Content(
            folderName = "Backups",
            lastBackup = LastBackup,
            automatic = BackupFrequency.Weekly,
            automaticReady = true,
        ),
    )

@PingogramPreviews
@Composable
private fun BackupAutomaticOffPreview() =
    Sample(BackupUiState.Content(folderName = "Backups", lastBackup = LastBackup, automatic = BackupFrequency.Off))

@PingogramPreviews
@Composable
private fun BackupBackingUpPreview() =
    Sample(BackupUiState.Content(folderName = "Backups", lastBackup = LastBackup, backingUp = true))
