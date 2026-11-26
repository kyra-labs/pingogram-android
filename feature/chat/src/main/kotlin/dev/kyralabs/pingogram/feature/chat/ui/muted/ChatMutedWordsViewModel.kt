package dev.kyralabs.pingogram.feature.chat.ui.muted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MutedWordsRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.MutedMessageEvent
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Muted words in chat [chatId] (`REQUIREMENTS.md` FR-X21): the [filter] that collapses its messages with a muted word,
 * while the setting is on, has words, and the chat is a group (a basic group or a supergroup that is no channel,
 * forum topics included); null otherwise. The messages tapped open stay open while the view model lives, until the
 * user leaves the chat. A view model of its own, beside the conversation's, as the bookmarks and reminders are.
 */
@HiltViewModel(assistedFactory = ChatMutedWordsViewModel.Factory::class)
class ChatMutedWordsViewModel
    @AssistedInject
    constructor(
        @Assisted chatId: Long,
        mutedWords: MutedWordsRepository,
        chatRepository: ChatRepository,
        userRepository: UserRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(chatId: Long): ChatMutedWordsViewModel
        }

        private val shown = MutableStateFlow(persistentSetOf<Long>())

        private val isGroup =
            chatRepository
                .chat(chatId)
                .map { chat ->
                    when (val type = chat?.type) {
                        is ChatType.BasicGroup -> true
                        is ChatType.Supergroup -> !type.isChannel
                        else -> false
                    }
                }.distinctUntilChanged()

        internal val filter: StateFlow<MutedWordsFilter?> =
            combine(mutedWords.settings, isGroup, userRepository.myUserId, shown) { settings, group, me, opened ->
                if (settings.enabled && settings.words.isNotEmpty() && group) {
                    MutedWordsFilter(settings.words, me, opened)
                } else {
                    null
                }
            }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        /** [next], with the events of muted messages ([MutedMessageEvent]) handled here instead. */
        fun routing(next: (ChatEvent) -> Unit): (ChatEvent) -> Unit =
            { event -> if (event is MutedMessageEvent) onEvent(event) else next(event) }

        fun onEvent(event: MutedMessageEvent) {
            when (event) {
                is MutedMessageEvent.Show -> shown.update { it.adding(event.messageId) }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
