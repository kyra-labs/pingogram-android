package dev.kyralabs.pingogram.feature.chat.ui.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ListenSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.TextSpeaker
import dev.kyralabs.pingogram.feature.chat.ui.ListenEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Listen to posts in chat [chatId] (`REQUIREMENTS.md` FR-X26): while the setting is on ([enabled]), "Listen" reads a
 * message and, in a channel with unread posts ([canListenToUnread]), "Listen to unread posts" reads them, oldest first
 * ([ListenTexts]); a [ListenReader] reads them and shows the [player] pill, and says what it cannot read in [notices].
 * Reading stops with the view model. A view model of its own beside the conversation's, as the reminders and bookmarks
 * are.
 */
@HiltViewModel(assistedFactory = ListenViewModel.Factory::class)
class ListenViewModel
    @AssistedInject
    constructor(
        @Assisted private val chatId: Long,
        settings: ListenSettingsRepository,
        chats: ChatRepository,
        playback: PlaybackSettingsRepository,
        private val texts: ListenTexts,
        speaker: TextSpeaker,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(chatId: Long): ListenViewModel
        }

        private val reader = ListenReader(viewModelScope, speaker, playback)

        val enabled: StateFlow<Boolean> =
            settings.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

        val canListenToUnread: StateFlow<Boolean> =
            combine(settings.enabled, chats.chat(chatId)) { on, chat ->
                on && (chat?.type as? ChatType.Supergroup)?.isChannel == true && chat.unreadCount > 0
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

        val player: StateFlow<ListenPlayer?> = reader.player

        val notices: Flow<ListenNotice> = reader.notices

        fun onEvent(event: ListenEvent) {
            when (event) {
                is ListenEvent.Message -> reader.read { texts.message(chatId, event.messageId) }
                ListenEvent.Unread -> reader.read { texts.unread(chatId) }
                ListenEvent.Pause -> reader.pause()
                ListenEvent.Resume -> reader.resume()
                ListenEvent.Next -> reader.next()
                ListenEvent.ChangeSpeed -> reader.changeSpeed()
                ListenEvent.Stop -> reader.stop()
            }
        }

        override fun onCleared() = reader.close()

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
