package dev.kyralabs.pingogram.feature.chats.ui.feed

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.BookmarkPreview
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.model.SponsoredMessages
import dev.kyralabs.pingogram.core.domain.model.SponsoredReportResult
import dev.kyralabs.pingogram.core.domain.repository.BookmarkRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.domain.repository.SponsoredMessageRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * What the feed's cards do besides opening and reading posts (`docs/DESIGN_SYSTEM.md` §6 "Channel feed"), through the
 * repositories the conversation uses: a post's link, a bookmark on this phone, and marking its channel as unread, the
 * only unread mark Telegram keeps (frame 6); and a channel's sponsored messages, their clicks and their reports
 * (frames 3 and 3b).
 */
class FeedCardActions
    @Inject
    constructor(
        private val messageActions: MessageActionRepository,
        private val bookmarks: BookmarkRepository,
        private val chats: ChatRepository,
        private val clock: WallClock,
        private val sponsored: SponsoredMessageRepository,
    ) {
        /** The sponsored messages TDLib has for channel [chatId]. */
        suspend fun ads(chatId: Long): Outcome<SponsoredMessages> = sponsored.sponsoredMessages(chatId)

        /** Tells TDLib the user opened sponsored message [id] of channel [chatId]: its button, or its media. */
        suspend fun clickAd(
            chatId: Long,
            id: Long,
            isMediaClick: Boolean,
        ): Outcome<Unit> = sponsored.click(chatId, id, isMediaClick)

        /** Reports sponsored message [id] of channel [chatId], first without a reason, then with [option]. */
        suspend fun reportAd(
            chatId: Long,
            id: Long,
            option: ReportOptionId,
        ): Outcome<SponsoredReportResult> = sponsored.report(chatId, id, option)

        /** The public or members-only link of [post], to its whole album. */
        suspend fun link(post: FeedPost): Outcome<String> =
            messageActions
                .messageLink(post.chatId, post.messageIds.first(), forAlbum = true, inThread = false)
                .map { it.link }

        /** The emoji reactions [post] takes, the most used first, at most [REACTION_BAR]. */
        suspend fun reactions(post: FeedPost): Outcome<List<String>> =
            messageActions.availableReactions(post.chatId, post.messageIds.first()).map { available ->
                (available.top + available.recent + available.popular)
                    .filterIsInstance<ReactionType.Emoji>()
                    .map(ReactionType.Emoji::emoji)
                    .distinct()
                    .take(REACTION_BAR)
            }

        /** Adds reaction [emoji] to [post], or takes it away if the user [chose] it already. */
        suspend fun react(
            post: FeedPost,
            emoji: String,
            chose: Boolean,
        ): Outcome<Unit> {
            val id = post.messageIds.first()
            val reaction = ReactionType.Emoji(emoji)
            return if (chose) {
                messageActions.removeReaction(post.chatId, id, reaction)
            } else {
                messageActions.addReaction(post.chatId, id, reaction)
            }
        }

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

/** How many reactions the bar above a lifted card offers. */
private const val REACTION_BAR = 7
