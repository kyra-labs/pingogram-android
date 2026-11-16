package dev.kyralabs.pingogram.widget

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.WidgetReplyRoute
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The reply sheet: chat [chatId]'s title (a string resource in its place for Saved Messages, [titleRes]), photo,
 * avatar shape and placeholder icon; whether the reply is on its way, and why the last one was not sent.
 */
@Immutable
data class WidgetReplyState(
    val chatId: Long,
    val title: String = "",
    val photo: ProfilePhoto? = null,
    val shape: AvatarShape = AvatarShape.Circle,
    val placeholderIcon: Int? = null,
    val titleRes: Int? = null,
    val isSending: Boolean = false,
    val error: DomainError? = null,
)

/**
 * The small reply sheet a Conversation widget's Reply opens (board 33, owner decision 2026-11-15): it names the chat
 * and sends the text typed as a message of it ([send], `MessageRepository.sendText`, which clears the chat's draft as
 * the composer does), only on the send button's tap. Once TDLib took the message, [sent]; a refusal keeps the text
 * and says why ([WidgetReplyState.error]).
 */
@HiltViewModel
class WidgetReplyViewModel
    internal constructor(
        private val chatId: Long,
        chat: Flow<Chat?>,
        myUserId: Flow<Long?>,
        private val sendText: suspend (FormattedText) -> Outcome<Unit>,
    ) : ViewModel() {
        @Inject
        internal constructor(
            savedStateHandle: SavedStateHandle,
            chatRepository: ChatRepository,
            userRepository: UserRepository,
            messageRepository: MessageRepository,
        ) : this(
            savedStateHandle.toRoute<WidgetReplyRoute>().chatId,
            chatRepository.chat(savedStateHandle.toRoute<WidgetReplyRoute>().chatId),
            userRepository.myUserId,
            { text -> messageRepository.sendText(savedStateHandle.toRoute<WidgetReplyRoute>().chatId, text) },
        )

        private val sending = MutableStateFlow(Sending())

        private val sentChannel = Channel<Unit>(Channel.CONFLATED)

        /** One event once TDLib took the reply. */
        val sent: Flow<Unit> = sentChannel.receiveAsFlow()

        val state: StateFlow<WidgetReplyState> =
            combine(chat, myUserId, sending) { chat, me, now ->
                (chat?.toState(me) ?: WidgetReplyState(chatId)).copy(isSending = now.isSending, error = now.error)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WidgetReplyState(chatId))

        /** Sends [text] to the chat; nothing for blank text or while a reply is on its way. */
        fun send(text: String) {
            if (text.isBlank() || sending.value.isSending) return
            sending.value = Sending(isSending = true)
            viewModelScope.launch {
                when (val outcome = sendText(FormattedText(text.trim(), persistentListOf()))) {
                    is Outcome.Success -> sentChannel.send(Unit)
                    is Outcome.Failure -> sending.update { Sending(error = outcome.error) }
                }
                if (sending.value.isSending) sending.value = Sending()
            }
        }

        private data class Sending(
            val isSending: Boolean = false,
            val error: DomainError? = null,
        )

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private fun Chat.toState(myUserId: Long?): WidgetReplyState {
    val type = type
    val isSavedMessages = type is ChatType.Private && type.userId == myUserId
    val supergroup = type as? ChatType.Supergroup
    return WidgetReplyState(
        chatId = id,
        title = title,
        photo = if (isSavedMessages) null else photo,
        shape =
            AvatarShape.ofChat(
                isForum = false,
                isGroup = type is ChatType.BasicGroup || (supergroup != null && !supergroup.isChannel),
                isChannel = supergroup?.isChannel == true,
                isSavedMessages = isSavedMessages,
            ),
        placeholderIcon = UiR.drawable.ic_bookmark_filled.takeIf { isSavedMessages },
        titleRes = UiR.string.chat_saved_messages.takeIf { isSavedMessages },
    )
}
