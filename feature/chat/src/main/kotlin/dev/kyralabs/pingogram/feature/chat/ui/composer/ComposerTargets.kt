package dev.kyralabs.pingogram.feature.chat.ui.composer

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.feature.chat.ui.findById
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The messages the composer's reply and edit bars show when they are not in the loaded history (a draft's reply to an
 * older message, an edit after process death, a message deleted meanwhile): fetched once each through
 * [MessageRepository.message], into [messages] by id, null while on their way. A message that is gone ends the reply
 * or edit through [onGone]; another failure leaves the bar without its message.
 *
 * Runs in [scope], the view model's; [require] may be called on any thread.
 */
internal class ComposerTargets(
    private val chatId: Long,
    private val repository: MessageRepository,
    private val scope: CoroutineScope,
    private val onGone: (messageId: Long) -> Unit,
) {
    private val fetched = MutableStateFlow<PersistentMap<Long, Message?>>(persistentMapOf())

    /** The fetched messages by id; null for one on its way or that could not be fetched. */
    val messages: StateFlow<PersistentMap<Long, Message?>> = fetched.asStateFlow()

    /** Fetches message [messageId], once, unless [history] holds it. */
    fun require(
        messageId: Long?,
        history: ChatHistory,
    ) {
        if (messageId == null || history.messages.findById(messageId) != null) return
        var added = false
        fetched.update { messages ->
            added = messageId !in messages
            if (added) messages.putting(messageId, null) else messages
        }
        if (!added) return
        scope.launch {
            when (val result = repository.message(chatId, messageId)) {
                is Outcome.Success -> fetched.update { it.putting(messageId, result.value) }
                is Outcome.Failure -> if (result.error == DomainError.NotFound) onGone(messageId)
            }
        }
    }

    /** Message [messageId], if it was fetched. */
    operator fun get(messageId: Long): Message? = fetched.value[messageId]
}
