package dev.kyralabs.pingogram.feature.settings.ui.backup

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.BackupFileInfo
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Backup & restore page with its [BackupViewModel]: Android's folder picker (`ACTION_OPEN_DOCUMENT_TREE`, no
 * permission) for "Backup folder", and snackbars for a backup written or what failed.
 */
@Composable
internal fun BackupRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    restore: String? = null,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    is BackupEffect.BackedUp -> {
                        resources.getString(R.string.backup_done, effect.folderName)
                    }

                    is BackupEffect.ShowError -> {
                        errorMessage(resources, effect.error, showErrorDetails)
                    }

                    is BackupEffect.Restored -> {
                        val restored = if (effect.otherAccount) R.string.backup_restored_other_account else null
                        resources.getString(restored ?: R.string.backup_restored)
                    }

                    is BackupEffect.RestoreRefused -> {
                        resources.getString(
                            when (effect.refusal) {
                                RestoreRefusal.NewerVersion -> R.string.backup_restore_newer
                                RestoreRefusal.NotABackup -> R.string.backup_restore_not_backup
                            },
                        )
                    }
                }
            // Snackbars queue up; later effects must not wait for them.
            launch { snackbarHostState.showSnackbar(message) }
        }
    }
    // The backup the chat list's restore card named, restored once, not again after a rotation.
    var restoreStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(restore) {
        if (restore != null && !restoreStarted) {
            restoreStarted = true
            viewModel.onEvent(BackupEvent.RestoreFile(restore))
        }
    }
    val pickFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { viewModel.onEvent(BackupEvent.RestoreFile(it.toString())) }
        }
    val pickFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let { viewModel.onEvent(BackupEvent.FolderPicked(it.toString())) }
        }
    BackupScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onPickFolder = { pickFolder.launch(null) },
        onPickFile = { pickFile.launch(arrayOf("*/*")) },
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Backup & restore (`docs/adr/0039-local-backup-and-restore.md`, `docs/DESIGN_SYSTEM.md` §6 "Backup & restore"): the
 * backup folder, picked once and shown by its name ([onPickFolder]), "Back up now" with the last backup's date and
 * size, which asks for the backup's password ([BackupPasswordDialog]), and "Restore from file" ([onPickFile]), whose
 * password and summary [RestoreDialog] asks for.
 */
@Composable
internal fun BackupScreen(
    state: BackupUiState,
    onEvent: (BackupEvent) -> Unit,
    onPickFolder: () -> Unit,
    onPickFile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.backup_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        if (state is BackupUiState.Content) {
            Folder(state.folderName, onPickFolder)
            BackUpNow(state, onEvent)
            Restore(onPickFile)
            state.restore?.let { RestoreDialog(it, onEvent) }
            state.password?.let { dialog ->
                BackupPasswordDialog(
                    dialog = dialog,
                    onConfirm = { onEvent(BackupEvent.ConfirmPassword(it)) },
                    onDismiss = { onEvent(BackupEvent.DismissPassword) },
                )
            }
        }
    }
}

/** "Backup folder": the kept folder's name, or "Not chosen", and "Choose" or "Change". */
@Composable
private fun Folder(
    folderName: String?,
    onPickFolder: () -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.backup_section_folder)) {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.backup_folder),
                shapes = shapes,
                icon = UiR.drawable.ic_folder_filled,
                supportingContent = { Text(folderName ?: stringResource(R.string.backup_folder_none)) },
                trailingContent = {
                    TextButton(onClick = onPickFolder) {
                        Text(
                            stringResource(
                                if (folderName ==
                                    null
                                ) {
                                    R.string.backup_folder_choose
                                } else {
                                    R.string.backup_folder_change
                                },
                            ),
                        )
                    }
                },
                onClick = onPickFolder,
            )
        }
    }
}

/**
 * "Back up now", with the last backup's date and size, or a loading indicator while one is written, then "Back up
 * automatically" ([automaticBackupItem]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackUpNow(
    state: BackupUiState.Content,
    onEvent: (BackupEvent) -> Unit,
) {
    val lastBackup = state.lastBackup?.let { backupText(it) } ?: stringResource(R.string.backup_none_yet)
    SettingsGroup(
        title = stringResource(R.string.backup_section_back_up),
        footer = stringResource(R.string.backup_footer),
    ) {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.backup_now),
                shapes = shapes,
                icon = UiR.drawable.ic_settings_backup_restore,
                supportingContent = { Text(lastBackup) },
                trailingContent =
                    if (state.backingUp) {
                        { LoadingIndicator() }
                    } else {
                        null
                    },
                onClick = { onEvent(BackupEvent.BackUp) },
                enabled = state.canBackUp,
            )
        }
        automaticBackupItem(state, onEvent)
    }
}

/** "Restore from file": Android's file picker (`ACTION_OPEN_DOCUMENT`), which finds backups in Drive or on USB. */
@Composable
private fun Restore(onPickFile: () -> Unit) {
    SettingsGroup(title = stringResource(R.string.backup_section_restore)) {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.backup_restore_file),
                shapes = shapes,
                icon = UiR.drawable.ic_download,
                supportingContent = { Text(stringResource(R.string.backup_restore_file_summary)) },
                onClick = onPickFile,
            )
        }
    }
}

/** "18 Nov 2026 · 4 KB": the backup's date in the user's medium format and its size. */
@Composable
private fun backupText(backup: BackupFileInfo): String {
    val locale = LocalConfiguration.current.locales[0]
    val date =
        DateTimeFormatter
            .ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .format(backup.modifiedAt.atZone(ZoneId.systemDefault()))
    val size = Formatter.formatShortFileSize(LocalContext.current, backup.sizeBytes)
    return stringResource(R.string.backup_last, date, size)
}
