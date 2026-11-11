package dev.kyralabs.pingogram.feature.chat.ui.interactions

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.MessageInteractionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Removing others' reactions from the sheet of who reacted to a message, for [MessageInteractionsViewModel], as a
 * group's administrators may where TDLib allows it (`MessageProperties.canDeleteReactions`): the question first
 * ([ask], kept in [savedState] so it survives process death), then on "Remove" ([confirm]) the sender's reactions to
 * the message (`removeReactions`) or all their recent ones in the chat (`removeAllRecentReactions`). Done, [onRemoved]
 * drops their rows and a snackbar says so; a refusal is a snackbar too ([onEffect]). Runs in [scope], the view
 * model's.
 */
internal class ReactionRemoval(
    private val repository: MessageInteractionRepository,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val onRemoved: (MessageSender) -> Unit,
    private val onEffect: (InteractionsEffect) -> Unit,
) {
    private val asked = MutableStateFlow(restored())
    private val removing = MutableStateFlow(false)

    /** The sender asked about, whether all their recent reactions go, and whether Telegram is answering. */
    val question: Flow<AskedRemoval?> =
        combine(asked, removing) { asked, removing -> asked?.copy(isRemoving = removing) }

    fun ask(
        sender: MessageSender,
        all: Boolean,
    ) {
        asked.value = AskedRemoval(sender, all)
        savedState[KEY_SENDER] = sender.key
        savedState[KEY_ALL] = all
    }

    fun dismiss() {
        if (!removing.value) clear()
    }

    /** Removes what was asked about, from message [messageId] of chat [chatId] or the whole chat. */
    fun confirm(
        chatId: Long,
        messageId: Long,
    ) {
        val question = asked.value ?: return
        if (removing.value) return
        removing.value = true
        scope.launch {
            val result =
                if (question.all) {
                    repository.removeAllRecentReactions(chatId, question.sender)
                } else {
                    repository.removeReactions(chatId, messageId, question.sender)
                }
            removing.value = false
            clear()
            when (result) {
                is Outcome.Success -> {
                    onRemoved(question.sender)
                    onEffect(InteractionsEffect.ReactionsRemoved(question.all))
                }

                is Outcome.Failure -> {
                    onEffect(InteractionsEffect.RemovalFailed(result.error))
                }
            }
        }
    }

    /** Forgets the question, as the sheet closes. */
    fun clear() {
        asked.value = null
        savedState.remove<String>(KEY_SENDER)
        savedState.remove<Boolean>(KEY_ALL)
    }

    private fun restored(): AskedRemoval? {
        val sender = savedState.get<String>(KEY_SENDER)?.let(::senderOf) ?: return null
        return AskedRemoval(sender, savedState.get<Boolean>(KEY_ALL) == true)
    }

    private companion object {
        const val KEY_SENDER = "interactions.removeSender"
        const val KEY_ALL = "interactions.removeAll"
    }
}

/** The question before removing [sender]'s reactions: [all] their recent ones or the message's; answering meanwhile. */
internal data class AskedRemoval(
    val sender: MessageSender,
    val all: Boolean,
    val isRemoving: Boolean = false,
)

/** "u123" for a user, "c-100" for a chat, as the saved state keeps a sender. */
private val MessageSender.key: String
    get() =
        when (this) {
            is MessageSender.User -> "u$userId"
            is MessageSender.Chat -> "c$chatId"
        }

private fun senderOf(key: String): MessageSender? {
    val id = key.drop(1).toLongOrNull() ?: return null
    return when (key.firstOrNull()) {
        'u' -> MessageSender.User(id)
        'c' -> MessageSender.Chat(id)
        else -> null
    }
}
