package dev.kyralabs.pingogram.feature.chats.ui.feed

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.isUnreadPost
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The cards of [posts], in their order (newest first): a message each, except an album's messages, which make one
 * card, as the channel shows them. [time] formats a message's date (Unix time) as the chat list does.
 */
internal fun feedPosts(
    posts: List<FoundMessage>,
    time: (Long) -> String,
): List<FeedPost> =
    posts
        .groupBy { post ->
            post.message.mediaAlbumId
                .takeIf { it != 0L }
                ?.let { post.chat.id to it } ?: post
        }.values
        .map { album -> album.toFeedPost(time) }

/** One card of [this] album's messages (or of one message), the first of them the newest. */
private fun List<FoundMessage>.toFeedPost(time: (Long) -> String): FeedPost {
    val first = first()
    val messages = map(FoundMessage::message)
    val info = messages.firstNotNullOfOrNull(Message::interactionInfo)
    return FeedPost(
        chatId = first.chat.id,
        messageIds = messages.map(Message::id).toImmutableList(),
        channelTitle = first.chat.title,
        channelPhoto = first.chat.photo,
        time = time(first.message.date),
        isUnread = any { it.isUnreadPost },
        text = messages.firstNotNullOfOrNull { it.content.text()?.takeIf { text -> text.text.isNotBlank() } },
        media = if (size > 1) album(messages) else first.message.content.media(),
        reactions =
            info
                ?.reactions
                .orEmpty()
                .map { FeedReaction((it.type as? ReactionType.Emoji)?.emoji, it.totalCount, it.isChosen) }
                .toImmutableList(),
        viewCount = info?.viewCount ?: 0,
    )
}

/** An album's first [ALBUM_TILES] pictures and how many more it has. */
private fun album(messages: List<Message>): FeedMedia.Album {
    val pictures = messages.mapNotNull { it.content.pictureFileId() }
    return FeedMedia.Album(pictures.take(ALBUM_TILES).toImmutableList(), (pictures.size - ALBUM_TILES).coerceAtLeast(0))
}

/** The text a message shows: its text, or its media's caption. */
private fun MessageContent.text(): FormattedText? =
    when (this) {
        is MessageContent.Text -> text
        is MessageContent.Photo -> caption
        is MessageContent.Video -> caption
        is MessageContent.Animation -> caption
        is MessageContent.Document -> caption
        else -> null
    }

/** What a message shows besides its text; none for a text alone or what the feed does not draw. */
private fun MessageContent.media(): FeedMedia? =
    when (this) {
        is MessageContent.Photo -> {
            photo.sizes.maxByOrNull { it.width * it.height }?.let { FeedMedia.Photo(it.file.id, it.width, it.height) }
        }

        is MessageContent.Video -> {
            FeedMedia.Video(video.thumbnail?.file?.id, video.duration)
        }

        is MessageContent.Animation -> {
            FeedMedia.Video(animation.thumbnail?.file?.id, animation.duration)
        }

        is MessageContent.Document -> {
            FeedMedia.File(document.fileName, document.file.size)
        }

        is MessageContent.Poll -> {
            FeedMedia.Poll(
                question = poll.question,
                options =
                    poll.options
                        .map { FeedPollOption(it.text, it.votePercentage, it.isChosen) }
                        .toImmutableList(),
                voterCount = poll.totalVoterCount,
                isAnonymous = poll.isAnonymous,
            )
        }

        else -> {
            null
        }
    }

/** The picture an album tile shows: a photo's largest size, a video's still picture. */
private fun MessageContent.pictureFileId(): Int? =
    when (this) {
        is MessageContent.Photo -> {
            photo.sizes
                .maxByOrNull { it.width * it.height }
                ?.file
                ?.id
        }

        is MessageContent.Video -> {
            video.thumbnail?.file?.id
        }

        else -> {
            null
        }
    }

/** An album card shows its first four pictures, two by two, the last saying how many more there are. */
private const val ALBUM_TILES = 4

/** The channels of [cards], in the order of their newest card, with how many cards each has and how many are unread. */
internal fun feedChannels(cards: List<FeedPost>): ImmutableList<FeedChannel> =
    cards
        .groupBy(FeedPost::chatId)
        .map { (chatId, own) ->
            val first = own.first()
            FeedChannel(chatId, first.channelTitle, first.channelPhoto, own.size, own.count(FeedPost::isUnread))
        }.toImmutableList()
