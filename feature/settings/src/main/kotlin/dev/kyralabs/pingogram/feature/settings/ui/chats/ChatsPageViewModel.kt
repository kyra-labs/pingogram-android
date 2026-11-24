package dev.kyralabs.pingogram.feature.settings.ui.chats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.domain.model.TranscriptionSettings
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettings
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import dev.kyralabs.pingogram.core.domain.repository.TranscriptionSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.VoiceTranscriber
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
     * Which Enter sends the composer's messages ([sendKeys]), whether they go without sound unless chosen
     * ([sendsSilently], `REQUIREMENTS.md` FR-X11), how voice messages play at the ear ([raiseToListen]),
     * whether they skip their silences ([skipSilence]), the chat list's swipes, which the Swipe actions row sums up
     * ([chatList]), and whether voice messages are transcribed as they show ([transcribeAutomatically]; null where
     * the phone cannot transcribe).
     */
    data class Content(
        val sendKeys: SendKeys = SendKeys(),
        val raiseToListen: RaiseToListenSettings = RaiseToListenSettings(),
        val skipSilence: Boolean = false,
        val chatList: ChatListSettings = ChatListSettings(),
        val transcribeAutomatically: Boolean? = null,
        val sendsSilently: Boolean = false,
    ) : ChatsPageUiState
}

/** What the user changes on the Chats page, handled by [ChatsPageViewModel.onEvent]. */
sealed interface ChatsPageEvent {
    /** "Send with Enter" turned on or off for a keyboard or the screen keyboard. */
    data class SetSendKeys(
        val keys: SendKeys,
    ) : ChatsPageEvent

    /** "Send silently by default" turned on or off. */
    data class SetSendsSilently(
        val enabled: Boolean,
    ) : ChatsPageEvent

    /** "Raise to listen" turned on or off. */
    data class SetRaiseToListen(
        val enabled: Boolean,
    ) : ChatsPageEvent

    /** "Skip silence" turned on or off. */
    data class SetSkipSilence(
        val enabled: Boolean,
    ) : ChatsPageEvent

    /** "Transcribe automatically", the transcription row's switch, turned on or off. */
    data class SetTranscribeAutomatically(
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
 * the keys they had on Appearance ([ComposerSettingsRepository], [PlaybackSettingsRepository],
 * [TranscriptionSettingsRepository], whose switch the transcription page shares where [VoiceTranscriber] can work).
 */
@HiltViewModel
class ChatsPageViewModel
    @Inject
    constructor(
        private val composerSettingsRepository: ComposerSettingsRepository,
        private val playbackSettingsRepository: PlaybackSettingsRepository,
        chatListSettingsRepository: ChatListSettingsRepository,
        private val transcriptionSettingsRepository: TranscriptionSettingsRepository,
        private val transcriber: VoiceTranscriber,
    ) : ViewModel() {
        val uiState: StateFlow<ChatsPageUiState> =
            combine<
                Pair<SendKeys, Boolean>,
                RaiseToListenSettings,
                Boolean,
                ChatListSettings,
                TranscriptionSettings,
                ChatsPageUiState,
            >(
                composerSettingsRepository.sendKeys.combine(composerSettingsRepository.sendsSilently, ::Pair),
                playbackSettingsRepository.raiseToListen,
                playbackSettingsRepository.skipSilence,
                chatListSettingsRepository.settings,
                transcriptionSettingsRepository.settings,
            ) { (keys, silently), raise, skipSilence, chatList, transcription ->
                val automatic = transcription.automatic.takeIf { transcriber.isAvailable }
                ChatsPageUiState.Content(keys, raise, skipSilence, chatList, automatic, silently)
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
                is ChatsPageEvent.SetSendKeys -> {
                    composerSettingsRepository.setSendKeys(event.keys)
                }

                is ChatsPageEvent.SetSendsSilently -> {
                    composerSettingsRepository.setSendsSilently(event.enabled)
                }

                is ChatsPageEvent.SetRaiseToListen -> {
                    playbackSettingsRepository.setRaiseToListen(event.enabled)
                }

                is ChatsPageEvent.SetSkipSilence -> {
                    playbackSettingsRepository.setSkipSilence(event.enabled)
                }

                is ChatsPageEvent.SetTranscribeAutomatically -> {
                    transcriptionSettingsRepository.setAutomatic(event.enabled)
                }
            }
    }
