package dev.kyralabs.pingogram.feature.chat.ui.listen

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.UnreadPostsRepository
import dev.kyralabs.pingogram.feature.chat.ui.composer.textOrCaption
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * What Listen to posts reads in a chat: a message's text or caption, or those of a channel's posts after the last one
 * read (at most [MAX_POSTS]), oldest first; posts without text (a photo alone, an album's other photos) are left out.
 * Reading them marks nothing read.
 */
class ListenTexts
    @Inject
    constructor(
        private val chats: ChatRepository,
        private val messages: MessageRepository,
        private val unreadPosts: UnreadPostsRepository,
    ) {
        suspend fun message(
            chatId: Long,
            messageId: Long,
        ): Outcome<List<String>> = messages.message(chatId, messageId).map { listOf(it).texts() }

        suspend fun unread(chatId: Long): Outcome<List<String>> {
            val lastRead = chats.chat(chatId).first()?.lastReadInboxMessageId
            return if (lastRead == null) {
                Outcome.Success(emptyList())
            } else {
                unreadPosts.postsAfter(chatId, lastRead, MAX_POSTS).map { it.texts() }
            }
        }

        private fun List<Message>.texts(): List<String> =
            map { it.textOrCaption.text.trim() }.filter { it.isNotEmpty() }

        private companion object {
            /** More than anyone listens to in one go, and two pages of TDLib's history. */
            const val MAX_POSTS = 200
        }
    }
