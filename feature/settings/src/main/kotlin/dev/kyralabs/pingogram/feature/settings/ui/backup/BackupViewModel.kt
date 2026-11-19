package dev.kyralabs.pingogram.feature.settings.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BackupFrequency
import dev.kyralabs.pingogram.core.domain.repository.AutomaticBackupRepository
import dev.kyralabs.pingogram.core.domain.repository.BackupFolderRepository
import dev.kyralabs.pingogram.core.domain.repository.BackupRepository
import dev.kyralabs.pingogram.core.domain.repository.DeviceAccountsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Backup & restore page (`docs/adr/0039-local-backup-and-restore.md`): the kept folder and its newest backup from
 * [folders], the folder Android's picker answers kept in its place, "Back up now" through the password dialog, how
 * often backups write themselves ([automatic]), and restoring a backup ([BackupRestoreFlow]). Passwords go to the
 * backup and nowhere else; they are not kept, not even in the saved state.
 */
@HiltViewModel
class BackupViewModel
    @Inject
    constructor(
        private val folders: BackupFolderRepository,
        backups: BackupRepository,
        accounts: DeviceAccountsRepository,
        private val automatic: AutomaticBackupRepository,
    ) : ViewModel() {
        private val password = MutableStateFlow<PasswordDialog?>(null)
        private val backingUp = MutableStateFlow(false)
        private val effectChannel = Channel<BackupEffect>(Channel.BUFFERED)

        val effects: Flow<BackupEffect> = effectChannel.receiveAsFlow()

        private val restore = BackupRestoreFlow(folders, backups, accounts, viewModelScope, effectChannel::send)

        val uiState: StateFlow<BackupUiState> =
            combine(
                folders.folder,
                password,
                backingUp,
                restore.step,
                combine(automatic.frequency, automatic.isReady, ::Pair),
            ) { folder, password, backingUp, restore, (frequency, ready) ->
                BackupUiState.Content(
                    folderName = folder?.name,
                    lastBackup = folder?.backups?.firstOrNull(),
                    backingUp = backingUp,
                    password = password,
                    restore = restore,
                    automatic = frequency,
                    automaticReady = ready,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BackupUiState.Loading)

        fun onEvent(event: BackupEvent) {
            when (event) {
                is BackupEvent.FolderPicked -> keep(event.treeUri)
                BackupEvent.BackUp -> askPassword()
                is BackupEvent.ConfirmPassword -> backUp(event.password)
                BackupEvent.DismissPassword -> password.value = null
                is BackupEvent.SetAutomatic -> setAutomatic(event.frequency)
                is BackupEvent.RestoreFile -> restore.start(event.document)
                is BackupEvent.OpenBackup -> restore.open(event.password)
                BackupEvent.ConfirmRestore -> restore.restore()
                BackupEvent.DismissRestore -> restore.dismiss()
            }
        }

        private fun keep(treeUri: String) {
            viewModelScope.launch {
                val kept = folders.keep(treeUri)
                if (kept is Outcome.Failure) effectChannel.send(BackupEffect.ShowError(kept.error))
            }
        }

        private fun setAutomatic(frequency: BackupFrequency) {
            viewModelScope.launch {
                val saved = automatic.setFrequency(frequency)
                if (saved is Outcome.Failure) effectChannel.send(BackupEffect.ShowError(saved.error))
            }
        }

        private fun askPassword() {
            val content = uiState.value as? BackupUiState.Content ?: return
            if (content.canBackUp) {
                password.value = if (content.lastBackup == null) PasswordDialog.New else PasswordDialog.Existing
            }
        }

        private fun backUp(typed: String) {
            val content = uiState.value as? BackupUiState.Content
            if (password.value == null || content?.canBackUp != true || typed.length < MIN_PASSWORD_LENGTH) return
            password.value = null
            backingUp.value = true
            viewModelScope.launch {
                val effect =
                    when (val written = folders.backUp(typed)) {
                        is Outcome.Success -> BackupEffect.BackedUp(checkNotNull(content.folderName))
                        is Outcome.Failure -> BackupEffect.ShowError(written.error)
                    }
                backingUp.value = false
                effectChannel.send(effect)
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
