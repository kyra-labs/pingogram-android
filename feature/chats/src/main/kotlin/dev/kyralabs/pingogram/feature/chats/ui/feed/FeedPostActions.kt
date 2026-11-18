package dev.kyralabs.pingogram.feature.chats.ui.feed

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.BookmarkPreview
import dev.kyralabs.pingogram.core.domain.repository.BookmarkRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * What a feed post's menu does besides opening and reading it (`docs/DESIGN_SYSTEM.md` §6 "Channel feed", frame 6),
 * through the repositories a message's own actions use: its link, a bookmark on this phone, and marking its channel
 * as unread, the only unread mark Telegram keeps.
 */
class FeedPostActions
    @Inject
    constructor(
        private val messageActions: MessageActionRepository,
        private val bookmarks: BookmarkRepository,
        private val chats: ChatRepository,
        private val clock: WallClock,
    ) {
        /** The public or members-only link of [post], to its whole album. */
        suspend fun link(post: FeedPost): Outcome<String> =
            messageActions
                .messageLink(post.chatId, post.messageIds.first(), forAlbum = true, inThread = false)
                .map { it.link }

        /** Bookmarks [post], keeping the tags and note of an earlier bookmark of it, with what it says now. */
        suspend fun bookmark(post: FeedPost): Outcome<Unit> {
            val messageId = post.messageIds.first()
            val preview =
                BookmarkPreview(
                    sender = post.channelTitle,
                    text =
                        post.text
                            ?.text
                            ?.lineSequence()
                            ?.first()
                            .orEmpty(),
                )
            val earlier = bookmarks.bookmark(post.chatId, messageId).first()
            val bookmark =
                earlier?.copy(preview = preview)
                    ?: Bookmark(post.chatId, messageId, clock.now().epochSecond, preview = preview)
            return bookmarks.save(bookmark)
        }

        /** Marks [post]'s channel as unread in the chat list. */
        suspend fun markChannelUnread(post: FeedPost): Outcome<Unit> = chats.markAsUnread(post.chatId)
    }
