package dev.kyralabs.pingogram.core.domain.model

/**
 * Where in a chat a message is, besides the chat itself (`REQUIREMENTS.md` FR-C9): a forum topic, or the thread of
 * replies to one message (a channel post's comments in its discussion group, a group message's replies). A
 * conversation shows one of them, or the whole chat without one.
 */
sealed interface MessageTopic {
    /** Topic [topicId] of a forum supergroup (td_api.tl `messageTopicForum.forum_topic_id`). */
    data class Forum(
        val topicId: Int,
    ) : MessageTopic {
        /** The forum's General topic, where messages without a topic go. */
        val isGeneral: Boolean get() = topicId == GENERAL_TOPIC_ID
    }

    /**
     * The thread of message [threadId] of a supergroup: the replies to it, the message that starts the thread
     * excluded (td_api.tl `messageTopicThread.message_thread_id`).
     */
    data class Thread(
        val threadId: Long,
    ) : MessageTopic

    companion object {
        /** The id of a forum's General topic, Telegram's own. */
        const val GENERAL_TOPIC_ID = 1
    }
}
