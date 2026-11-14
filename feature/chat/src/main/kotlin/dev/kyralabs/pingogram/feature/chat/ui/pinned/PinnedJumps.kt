package dev.kyralabs.pingogram.feature.chat.ui.pinned

import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Jump to message" of the pinned messages page, handed to the conversation below it, in memory: the page asks for a
 * message ([request]) and leaves, and the conversation of that chat and topic takes it ([take]) and shows the message,
 * as a tapped reply quote does. A conversation pushed below the page and one in the Chats tab's detail pane alike, as
 * neither is the page's navigation entry.
 */
@Singleton
class PinnedJumps
    @Inject
    constructor() {
        private val requests = MutableStateFlow<Map<Target, Long>>(emptyMap())

        /** The jumps waiting, by chat and topic, for a conversation to [take]. */
        internal val waiting: StateFlow<Map<Target, Long>> = requests.asStateFlow()

        /** Asks the conversation of chat [chatId] (its [topic], if not null) to show message [messageId]. */
        fun request(
            chatId: Long,
            topic: MessageTopic?,
            messageId: Long,
        ) {
            requests.update { it + (Target(chatId, topic) to messageId) }
        }

        /** The message the conversation of chat [chatId] (its [topic]) should show, once; null if none waits. */
        fun take(
            chatId: Long,
            topic: MessageTopic?,
        ): Long? {
            val target = Target(chatId, topic)
            var taken: Long? = null
            requests.update { waiting ->
                taken = waiting[target]
                waiting - target
            }
            return taken
        }

        /** A conversation: a chat, or one of its topics or threads. */
        internal data class Target(
            val chatId: Long,
            val topic: MessageTopic?,
        )
    }
