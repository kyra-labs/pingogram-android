package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Replying in place (the owner's board 32d), a part of [CatchUpViewModel]: which chat's card shows the reply field
 * ([replyingTo], in the saved state, so a rotation keeps it open), and sending, which answers the card's message
 * (the newest that mentions the user, else the newest unread) with the text typed, through the conversation's own
 * `MessageRepository.sendText`; once Telegram took it, [onSent] marks the chat as read and moves on. A failure keeps
 * the field open, with what was typed, and goes to [onError].
 */
internal class CatchUpReply(
    private val scope: CoroutineScope,
    private val savedStateHandle: SavedStateHandle,
    private val sources: CatchUpSources,
    private val onSent: (chatId: Long) -> Unit,
    private val onError: (DomainError) -> Unit,
) {
    val replyingTo: StateFlow<Long> = savedStateHandle.getStateFlow(KEY_REPLYING, 0L)
    private val sending = MutableStateFlow(false)

    /** The reply field of chat `chatId` while it shows, else null. */
    fun state(chatId: Flow<Long?>): Flow<CatchUpReplyState?> =
        combine(chatId, replyingTo, sending) { shown, replying, isSending ->
            if (shown != null && shown == replying) CatchUpReplyState(isSending) else null
        }

    fun start(chatId: Long) {
        savedStateHandle[KEY_REPLYING] = chatId
    }

    fun cancel() {
        if (sending.value) return
        savedStateHandle[KEY_REPLYING] = 0L
    }

    /** Sends [text] to chat [chatId] in reply to message [messageId] (a plain message without one). */
    fun send(
        chatId: Long,
        messageId: Long?,
        text: String,
    ) {
        if (sending.value || text.isBlank() || replyingTo.value != chatId) return
        sending.value = true
        scope.launch {
            val outcome =
                sources.messageRepository.sendText(
                    chatId = chatId,
                    text = FormattedText(text.trim(), persistentListOf()),
                    replyTo = messageId?.let { ReplyTarget(messageId = it) },
                )
            sending.value = false
            when (outcome) {
                is Outcome.Success -> {
                    savedStateHandle[KEY_REPLYING] = 0L
                    onSent(chatId)
                }

                is Outcome.Failure -> {
                    onError(outcome.error)
                }
            }
        }
    }

    private companion object {
        const val KEY_REPLYING = "catchUp.replying"
    }
}
