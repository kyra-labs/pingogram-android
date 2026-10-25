package dev.kyralabs.pingogram.feature.chats.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.domain.model.ArchiveSettings
import dev.kyralabs.pingogram.core.domain.repository.ArchiveRowRepository
import dev.kyralabs.pingogram.core.domain.repository.ArchiveSettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The archive's settings page (`REQUIREMENTS.md` FR-L9, `docs/DESIGN_SYSTEM.md` §6 "Archive settings").
 *
 * - **Telegram's settings** load when the page opens ([ArchiveSettingsRepository.archiveSettings]); TDLib sends no
 *   update for them, so the page shows what it read and what it changed. If loading fails, the page says why and
 *   offers to try again.
 * - **A flipped switch** shows at once and is sent at once; if Telegram refuses it, the switch flips back (unless
 *   something changed it meanwhile) and a snackbar says why. Archiving new chats from non-contacts can only be turned
 *   on where Telegram offers it ([ArchiveSettingsContent.Content.canChangeNewChats]).
 * - **The row's style** follows [ArchiveRowRepository], which the chat list's own menu changes too.
 *
 * Nothing to restore after process death: everything is read again.
 */
@HiltViewModel
class ArchiveSettingsViewModel
    @Inject
    constructor(
        private val repository: ArchiveSettingsRepository,
        private val rowRepository: ArchiveRowRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(ArchiveSettingsUiState())
        val uiState: StateFlow<ArchiveSettingsUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<ArchiveSettingsEffect>(Channel.BUFFERED)
        val effects: Flow<ArchiveSettingsEffect> = effectChannel.receiveAsFlow()

        private var loadJob: Job? = null

        init {
            viewModelScope.launch {
                rowRepository.style.collect { style -> mutableUiState.update { it.copy(rowStyle = style) } }
            }
            load()
        }

        fun onEvent(event: ArchiveSettingsEvent) {
            when (event) {
                is ArchiveSettingsEvent.Change -> change(event.settings)
                is ArchiveSettingsEvent.SetRowStyle -> setRowStyle(event.style)
                ArchiveSettingsEvent.Retry -> if (uiState.value.settings is ArchiveSettingsContent.Error) load()
            }
        }

        private fun load() {
            loadJob?.cancel()
            mutableUiState.update { it.copy(settings = ArchiveSettingsContent.Loading) }
            loadJob =
                viewModelScope.launch {
                    val content =
                        when (val result = repository.archiveSettings()) {
                            is Outcome.Success -> {
                                ArchiveSettingsContent.Content(
                                    result.value.settings,
                                    result.value.canArchiveAndMuteNewChats,
                                )
                            }

                            is Outcome.Failure -> {
                                ArchiveSettingsContent.Error(result.error)
                            }
                        }
                    mutableUiState.update { it.copy(settings = content) }
                }
        }

        private fun change(settings: ArchiveSettings) {
            val content = uiState.value.settings as? ArchiveSettingsContent.Content ?: return
            val previous = content.settings
            val turnsOnNewChats =
                settings.archiveAndMuteNewChatsFromNonContacts && !previous.archiveAndMuteNewChatsFromNonContacts
            // Nothing changed, or Telegram does not offer what changed.
            if (previous == settings || (turnsOnNewChats && !content.canArchiveAndMuteNewChats)) return
            showSettings { settings }
            viewModelScope.launch {
                repository.setArchiveSettings(settings).onFailure { error ->
                    // Back to what showed before, unless a later change replaced it meanwhile.
                    showSettings { current -> if (current == settings) previous else current }
                    effectChannel.trySend(ArchiveSettingsEffect.ShowError(error))
                }
            }
        }

        /** Replaces the settings shown with [transform]'s, while they show. */
        private fun showSettings(transform: (ArchiveSettings) -> ArchiveSettings) {
            mutableUiState.update { state ->
                val content = state.settings as? ArchiveSettingsContent.Content ?: return@update state
                state.copy(settings = content.copy(settings = transform(content.settings)))
            }
        }

        private fun setRowStyle(style: ArchiveRowStyle) {
            viewModelScope.launch {
                rowRepository.setStyle(style).onFailure { effectChannel.trySend(ArchiveSettingsEffect.ShowError(it)) }
            }
        }
    }
