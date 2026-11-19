package dev.kyralabs.pingogram.feature.settings.ui.backup

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.BackupFrequency
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.ChoiceRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroupScope
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.feature.settings.ui.nestedItemColors
import kotlinx.collections.immutable.toImmutableList

/**
 * "Back up automatically" (`docs/adr/0039-local-backup-and-restore.md`, "Automatic backups"): Off, Daily and Weekly as
 * [ChoiceRow]'s connected buttons under what they do, usable once a folder is kept. While they are on but no backup
 * with a password has kept their key yet, the summary says they start after the next one.
 */
internal fun SettingsGroupScope.automaticBackupItem(
    state: BackupUiState.Content,
    onEvent: (BackupEvent) -> Unit,
) = item(key = AUTOMATIC) { shapes ->
    SettingsSegment(shapes) {
        SegmentedListItem(
            shapes = ListItemDefaults.shapes(),
            supportingContent = { Text(stringResource(state.automaticSummary())) },
            colors = nestedItemColors(),
        ) {
            Text(stringResource(R.string.backup_automatic))
        }
        val spacing = MaterialTheme.spacing
        ChoiceRow(
            options = FrequencyChoices,
            selected = state.automatic,
            label = { stringResource(it.labelRes()) },
            onSelect = { onEvent(BackupEvent.SetAutomatic(it)) },
            contentPadding = PaddingValues(start = spacing.large, end = spacing.large, bottom = spacing.large),
            enabled = state.folderName != null,
        )
    }
}

private const val AUTOMATIC = "automatic"

private val FrequencyChoices = BackupFrequency.entries.toImmutableList()

private fun BackupFrequency.labelRes(): Int =
    when (this) {
        BackupFrequency.Off -> R.string.backup_automatic_off
        BackupFrequency.Daily -> R.string.backup_automatic_daily
        BackupFrequency.Weekly -> R.string.backup_automatic_weekly
    }

private fun BackupUiState.Content.automaticSummary(): Int =
    when {
        folderName == null -> R.string.backup_automatic_no_folder
        automatic == BackupFrequency.Off -> R.string.backup_automatic_summary_off
        !automaticReady -> R.string.backup_automatic_waiting
        else -> R.string.backup_automatic_summary
    }
