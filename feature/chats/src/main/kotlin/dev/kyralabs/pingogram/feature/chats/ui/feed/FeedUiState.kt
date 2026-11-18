package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf

/**
 * The Channel feed (`REQUIREMENTS.md` FR-X5, `docs/DESIGN_SYSTEM.md` §6 "Channel feed"): its posts, the counts its top
 * bar shows, why reading new posts failed if it did ([error], a card above the posts already on the phone), and the
 * filter.
 */
@Immutable
data class FeedUiState(
    val content: FeedContent = FeedContent.Loading,
    val unreadCount: Int = 0,
    val channelCount: Int = 0,
    val error: DomainError? = null,
    val filter: FeedFilter = FeedFilter(),
)

/** What the feed's list shows. */
@Immutable
sealed interface FeedContent {
    /** The posts are on their way: skeleton cards. */
    data object Loading : FeedContent

    /** No unread post, and the read ones are not asked for ([FeedEvent.ShowReadPosts]). */
    data object NoNewPosts : FeedContent

    /**
     * The [unread] posts, newest first, then the [read] ones. [readHere] counts the posts read since the feed opened:
     * once none is unread, the "You're all caught up" card says how many, in place of the "Earlier · read" divider.
     */
    data class Posts(
        val unread: ImmutableList<FeedPost>,
        val read: ImmutableList<FeedPost> = persistentListOf(),
        val readHere: Int = 0,
    ) : FeedContent {
        override fun toString() = "Posts(unread=${unread.size}, read=${read.size}, readHere=$readHere)"
    }
}

/**
 * A post of channel [chatId] as its card shows it: one message, or an album's messages ([messageIds], the first its
 * key's), with the channel's name and photo, its time, whether it is [isUnread], its text, media, reactions and views.
 */
@Immutable
data class FeedPost(
    val chatId: Long,
    val messageIds: ImmutableList<Long>,
    val channelTitle: String,
    val channelPhoto: ProfilePhoto?,
    val time: String,
    val isUnread: Boolean,
    val text: FormattedText?,
    val media: FeedMedia?,
    val reactions: ImmutableList<FeedReaction>,
    val viewCount: Int,
) {
    /** The card's key in the list: its channel and its first message. */
    val key: String get() = "$chatId:${messageIds.first()}"

    override fun toString() = "FeedPost($key, unread=$isUnread)"
}

/** What a post shows besides its text. */
@Immutable
sealed interface FeedMedia {
    /** A photo, by its largest size's file and that size. */
    data class Photo(
        val fileId: Int,
        val width: Int,
        val height: Int,
    ) : FeedMedia

    /** An album: its first four pictures' files, and how many more it holds ("+N" on the last). */
    data class Album(
        val fileIds: ImmutableList<Int>,
        val more: Int,
    ) : FeedMedia

    /** A video: its still picture's file, if any, and its length in seconds. */
    data class Video(
        val thumbnailFileId: Int?,
        val duration: Int,
    ) : FeedMedia

    /** A file: its name and size in bytes. */
    data class File(
        val name: String,
        val size: Long,
    ) : FeedMedia

    /** A poll: its question, options with their shares, and how many voted. */
    data class Poll(
        val question: FormattedText,
        val options: ImmutableList<FeedPollOption>,
        val voterCount: Int,
        val isAnonymous: Boolean,
    ) : FeedMedia
}

/** A poll option: its text, its share of the votes in percent, and whether the user chose it. */
@Immutable
data class FeedPollOption(
    val text: FormattedText,
    val percentage: Int,
    val isChosen: Boolean,
)

/** A reaction under a post: its emoji (or none for a custom emoji), its count, and whether it is the user's. */
@Immutable
data class FeedReaction(
    val emoji: String?,
    val count: Int,
    val isChosen: Boolean,
)

/** Which posts the feed shows: the [chosen] channels' (null for all of them), and only unread ones if [onlyUnread]. */
@Immutable
data class FeedFilter(
    val chosen: ImmutableSet<Long>? = null,
    val onlyUnread: Boolean = false,
)
