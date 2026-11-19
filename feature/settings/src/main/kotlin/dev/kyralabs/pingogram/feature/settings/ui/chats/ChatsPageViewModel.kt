package dev.kyralabs.pingogram.feature.settings.ui.chats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettings
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Chats page shows (`REQUIREMENTS.md` FR-S2): nothing until the settings are read, then the settings. */
@Immutable
sealed interface ChatsPageUiState {
    data object Loading : ChatsPageUiState

    /**
     * Which Enter sends the composer's messages ([sendKeys]), how voice messages play at the ear ([raiseToListen]),
     * whether they skip their silences ([skipSilence]), and the chat list's swipes, which the Swipe actions row sums
     * up ([chatList]).
     */
    data class Content(
        val sendKeys: SendKeys = SendKeys(),
        val raiseToListen: RaiseToListenSettings = RaiseToListenSettings(),
        val skipSilence: Boolean = false,
        val chatList: ChatListSettings = ChatListSettings(),
    ) : ChatsPageUiState
}

/** What the user changes on the Chats page, handled by [ChatsPageViewModel.onEvent]. */
sealed interface ChatsPageEvent {
    /** "Send with Enter" turned on or off for a keyboard or the screen keyboard. */
    data class SetSendKeys(
        val keys: SendKeys,
    ) : ChatsPageEvent

    /** "Raise to listen" turned on or off. */
    data class SetRaiseToListen(
        val enabled: Boolean,
    ) : ChatsPageEvent

    /** "Pause when lowered" turned on or off. */
    data class SetPauseWhenLowered(
        val enabled: Boolean,
    ) : ChatsPageEvent

    /** "Skip silence" turned on or off. */
    data class SetSkipSilence(
        val enabled: Boolean,
    ) : ChatsPageEvent
}

/** One-off side effects of [ChatsPageViewModel], handled once by its route. */
sealed interface ChatsPageEffect {
    /** A setting could not be saved, shown in a snackbar (errors are never dialogs). */
    data class ShowError(
        val error: DomainError,
    ) : ChatsPageEffect
}

/**
 * The Chats page (`REQUIREMENTS.md` FR-S2, owner decision 2026-11-19): how chats behave, apart from how they look.
 * It shows what is saved, so a change that cannot be saved does not show, and says so in a snackbar. The settings keep
 * the keys they had on Appearance ([ComposerSettingsRepository], [PlaybackSettingsRepository]).
 */
@HiltViewModel
class ChatsPageViewModel
    @Inject
    constructor(
        private val composerSettingsRepository: ComposerSettingsRepository,
        private val playbackSettingsRepository: PlaybackSettingsRepository,
        chatListSettingsRepository: ChatListSettingsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<ChatsPageUiState> =
            combine<SendKeys, RaiseToListenSettings, Boolean, ChatListSettings, ChatsPageUiState>(
                composerSettingsRepository.sendKeys,
                playbackSettingsRepository.raiseToListen,
                playbackSettingsRepository.skipSilence,
                chatListSettingsRepository.settings,
            ) { keys, raise, skipSilence, chatList ->
                ChatsPageUiState.Content(keys, raise, skipSilence, chatList)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatsPageUiState.Loading)

        private val effectChannel = Channel<ChatsPageEffect>(Channel.BUFFERED)
        val effects: Flow<ChatsPageEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: ChatsPageEvent) {
            viewModelScope.launch {
                save(event).onFailure { effectChannel.trySend(ChatsPageEffect.ShowError(it)) }
            }
        }

        private suspend fun save(event: ChatsPageEvent): Outcome<Unit> =
            when (event) {
                is ChatsPageEvent.SetSendKeys -> composerSettingsRepository.setSendKeys(event.keys)
                is ChatsPageEvent.SetRaiseToListen -> playbackSettingsRepository.setRaiseToListen(event.enabled)
                is ChatsPageEvent.SetPauseWhenLowered -> playbackSettingsRepository.setPauseWhenLowered(event.enabled)
                is ChatsPageEvent.SetSkipSilence -> playbackSettingsRepository.setSkipSilence(event.enabled)
            }
    }
