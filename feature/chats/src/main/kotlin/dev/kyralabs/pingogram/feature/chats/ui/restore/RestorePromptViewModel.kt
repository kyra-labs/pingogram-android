package dev.kyralabs.pingogram.feature.chats.ui.restore

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.repository.BackupFolderRepository
import dev.kyralabs.pingogram.core.domain.repository.BackupSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.DemoRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** What the chat list offers about restoring a backup (`docs/adr/0039-local-backup-and-restore.md`). */
@Immutable
sealed interface RestorePromptUiState {
    /** Nothing: the demo, a backup hidden, a folder without backups, or no folder and no sign-in to offer a file for. */
    data object Hidden : RestorePromptUiState

    /** "Restore from your backup of 12 Nov?": the kept folder's newest backup, [document], written at [modifiedAt]. */
    data class FromFolder(
        val document: String,
        val modifiedAt: Instant,
    ) : RestorePromptUiState

    /** "Restore settings from a backup?" after a sign-in while no folder is kept (a new phone), with "Choose file". */
    data object FromFile : RestorePromptUiState
}

/** What the user does with the card. */
sealed interface RestorePromptEvent {
    /** Hides the card: for that backup, or the offer to restore from a file until the next sign-in. */
    data object NotNow : RestorePromptEvent

    /** "Restore" on the kept folder's backup. */
    data object Restore : RestorePromptEvent

    /** "Choose file": Android's file picker. */
    data object ChooseFile : RestorePromptEvent

    /** The file picker answered with backup [document]. */
    data class FilePicked(
        val document: String,
    ) : RestorePromptEvent
}

/** One-off things the card's host does. */
sealed interface RestorePromptEffect {
    /** Shows Android's file picker. */
    data object PickFile : RestorePromptEffect

    /** Opens Backup & restore restoring backup [document]. */
    data class OpenBackup(
        val document: String,
    ) : RestorePromptEffect
}

/**
 * The restore card above the chat list (`docs/adr/0039-local-backup-and-restore.md`, "Restoring"), shown after signing
 * in: while a backup folder is kept, its newest backup until the user restores it or hides it ("Not now" hides it for
 * that backup); while none is kept, after a sign-in on this phone, the offer to restore from a file until it is used or
 * hidden, so the users of an update never see it. Never in the demo.
 * Restoring itself is Backup & restore's, which the card opens with the backup named.
 */
@HiltViewModel
class RestorePromptViewModel
    @Inject
    constructor(
        folders: BackupFolderRepository,
        private val settings: BackupSettingsRepository,
        demo: DemoRepository,
    ) : ViewModel() {
        private val effectChannel = Channel<RestorePromptEffect>(Channel.BUFFERED)

        val effects: Flow<RestorePromptEffect> = effectChannel.receiveAsFlow()

        val uiState: StateFlow<RestorePromptUiState> =
            combine(
                folders.folder,
                settings.restoreDismissedFor,
                settings.restorePromptPending,
                demo.isDemo,
            ) { folder, dismissedFor, promptPending, isDemo ->
                val newest = folder?.backups?.firstOrNull()
                when {
                    isDemo -> {
                        RestorePromptUiState.Hidden
                    }

                    folder == null && promptPending -> {
                        RestorePromptUiState.FromFile
                    }

                    newest != null && newest.document != dismissedFor -> {
                        RestorePromptUiState.FromFolder(newest.document, newest.modifiedAt)
                    }

                    else -> {
                        RestorePromptUiState.Hidden
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RestorePromptUiState.Hidden)

        fun onEvent(event: RestorePromptEvent) {
            val state = uiState.value
            when (event) {
                RestorePromptEvent.NotNow -> {
                    hide(state)
                }

                RestorePromptEvent.Restore -> {
                    if (state is RestorePromptUiState.FromFolder) {
                        hide(state)
                        effectChannel.trySend(RestorePromptEffect.OpenBackup(state.document))
                    }
                }

                RestorePromptEvent.ChooseFile -> {
                    effectChannel.trySend(RestorePromptEffect.PickFile)
                }

                is RestorePromptEvent.FilePicked -> {
                    hide(state)
                    effectChannel.trySend(RestorePromptEffect.OpenBackup(event.document))
                }
            }
        }

        /** Hides the card as it is now; a write that fails leaves it, to be hidden next time. */
        private fun hide(state: RestorePromptUiState) {
            viewModelScope.launch {
                when (state) {
                    is RestorePromptUiState.FromFolder -> settings.dismissRestore(state.document)
                    RestorePromptUiState.FromFile -> settings.dismissRestorePrompt()
                    RestorePromptUiState.Hidden -> Unit
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
