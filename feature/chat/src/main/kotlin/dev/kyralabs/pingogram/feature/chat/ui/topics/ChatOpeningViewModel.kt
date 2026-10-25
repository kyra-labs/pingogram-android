package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What opening chat [chatId] shows (`REQUIREMENTS.md` FR-C9): its conversation, or for a forum (a supergroup with
 * topics) its topic list; a message search found in a forum ([messageId]) opens the conversation of its topic. Saved
 * Messages opens its conversation with its tabs. Decided
 * from TDLib's memory (the chat and its supergroup, which TDLib announces before the chat), so it takes a frame or
 * two; meanwhile [opening] is [ChatOpening.Unknown], and the destination draws what the conversation draws first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ChatOpeningViewModel.Factory::class)
class ChatOpeningViewModel
    @AssistedInject
    constructor(
        @Assisted("chatId") private val chatId: Long,
        @Assisted("messageId") private val messageId: Long?,
        private val chats: ChatRepository,
        private val groups: GroupRepository,
        private val messages: MessageRepository,
        private val users: UserRepository,
    ) : ViewModel() {
        /** Creates the view model of opening chat `chatId`, at message `messageId` if not null. */
        @AssistedFactory
        interface Factory {
            fun create(
                @Assisted("chatId") chatId: Long,
                @Assisted("messageId") messageId: Long?,
            ): ChatOpeningViewModel
        }

        val opening: StateFlow<ChatOpening> =
            flow {
                // A chat TDLib does not know opens as a conversation, which says so.
                val chat = chats.chat(chatId).first() ?: chats.getChat(chatId).getOrNull()
                val type = chat?.type as? ChatType.Supergroup
                val privateWith = (chat?.type as? ChatType.Private)?.userId
                // TDLib names the user (option my_id) before the login is ready, so before any chat can open.
                if (privateWith != null && privateWith == users.myUserId.value) {
                    emit(ChatOpening.SavedMessages)
                } else if (type == null || type.isChannel) {
                    emit(ChatOpening.Conversation)
                } else {
                    emitAll(
                        groups
                            .supergroup(type.supergroupId)
                            .filterNotNull()
                            .map { it.isForum }
                            .distinctUntilChanged()
                            .map { isForum -> if (isForum) forumOpening() else ChatOpening.Conversation },
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatOpening.Unknown)

        /** The topic list, or the topic of the message the forum opens at. */
        private suspend fun forumOpening(): ChatOpening {
            val messageId = messageId ?: return ChatOpening.Topics
            val topic = messages.message(chatId, messageId).getOrNull()?.topic as? MessageTopic.Forum
            return ChatOpening.Topic(topic ?: MessageTopic.Forum(MessageTopic.GENERAL_TOPIC_ID))
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** What a chat opens to. */
sealed interface ChatOpening {
    /** Not known yet. */
    data object Unknown : ChatOpening

    data object Conversation : ChatOpening

    /** Saved Messages: its conversation, with its tabs. */
    data object SavedMessages : ChatOpening

    /** A forum's topic list. */
    data object Topics : ChatOpening

    /** The conversation of a forum topic, at the message the forum was opened at. */
    data class Topic(
        val topic: MessageTopic.Forum,
    ) : ChatOpening
}
