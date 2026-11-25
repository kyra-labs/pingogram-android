package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * The replied messages of chat [chatId] fetched for messages whose replied message is not loaded, by replying message
 * id ([state]): a quote shows its message once it is here, or why it could not be ([ReplyState]).
 */
internal class RepliedMessages(
    private val chatId: Long,
    private val repository: MessageRepository,
    private val scope: CoroutineScope,
) {
    private val replies = MutableStateFlow<PersistentMap<Long, ReplyState>>(persistentMapOf())

    /** The replied messages asked for so far, by replying message id. */
    val state: StateFlow<PersistentMap<Long, ReplyState>> = replies.asStateFlow()

    /**
     * Asks once for each replied message that is not in [history], for messages of this chat: the quote shows it
     * once it is there. A failure is kept, and shown; it is not asked for again. The messages of one history are
     * marked pending in one change, as every change maps the conversation again (`docs/PERFORMANCE.md`). Each answer
     * then shows as soon as it comes, with whatever others came meanwhile in the same change: none waits for another,
     * as one TDLib must ask the server for waits for the connection while the others answer from its database.
     */
    fun fetchMissing(history: ChatHistory) {
        val known = replies.value
        val missing =
            history.messages.filter { message ->
                val replyTo = message.replyTo as? ReplyTo.Message
                val loaded = replyTo?.chatId == chatId && history.messages.findById(replyTo.messageId) != null
                replyTo != null && replyTo.messageId != 0L && !loaded && message.id !in known
            }
        if (missing.isEmpty()) return
        var added: List<Long> = emptyList()
        replies.update { replies ->
            added = missing.map { it.id }.filter { it !in replies }
            if (added.isEmpty()) replies else replies.puttingAll(added.associateWith { ReplyState.Pending })
        }
        if (added.isEmpty()) return
        val asked = added
        scope.launch {
            val answers = Channel<Pair<Long, ReplyState>>(Channel.UNLIMITED)
            launch {
                asked
                    .map { id ->
                        launch {
                            val state =
                                when (val replied = repository.repliedMessage(chatId, id)) {
                                    is Outcome.Success -> ReplyState.Loaded(replied.value)
                                    is Outcome.Failure -> ReplyState.Failed(replied.error)
                                }
                            answers.send(id to state)
                        }
                    }.joinAll()
                answers.close()
            }
            for (first in answers) {
                val came = mutableMapOf(first)
                var next = answers.tryReceive().getOrNull()
                while (next != null) {
                    came += next
                    next = answers.tryReceive().getOrNull()
                }
                replies.update { it.puttingAll(came) }
            }
        }
    }
}
