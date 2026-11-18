package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.component.SponsoredSheet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * The Channel feed (`REQUIREMENTS.md` FR-X5, `docs/DESIGN_SYSTEM.md` §6 "Channel feed"): its posts, the counts its top
 * bar shows, why reading new posts failed if it did ([error], a card above the posts already on the phone), the
 * filter, the followed channels with their counts, for the filter sheet ([channels]), each channel's sponsored
 * message ([ads], by channel), and "About this ad" while it is open ([adSheet]).
 */
@Immutable
data class FeedUiState(
    val content: FeedContent = FeedContent.Loading,
    val unreadCount: Int = 0,
    val channelCount: Int = 0,
    val error: DomainError? = null,
    val filter: FeedFilter = FeedFilter(),
    val channels: ImmutableList<FeedChannel> = persistentListOf(),
    val ads: ImmutableMap<Long, FeedAd> = persistentMapOf(),
    val adSheet: SponsoredSheet? = null,
)

/**
 * A channel's sponsored message as its card shows it (`REQUIREMENTS.md` §2), exactly as TDLib returned it: shown after
 * channel [chatId]'s newest post, labelled "Recommended" if [isRecommended] else "Sponsored", the sponsor's [title],
 * [text], [media] and [sponsorPhoto] (null where none, or it must not be shown), and its button ([buttonText]).
 */
@Immutable
data class FeedAd(
    val chatId: Long,
    val id: Long,
    val isRecommended: Boolean,
    val title: String,
    val text: FormattedText,
    val media: FeedMedia?,
    val sponsorPhoto: Photo?,
    val buttonText: String,
) {
    val key: String get() = "ad:$chatId:$id"

    override fun toString() = "FeedAd($key, isRecommended=$isRecommended)"
}

/**
 * A followed channel as the filter sheet lists it: its [title] and [photo], and how many of the feed's cards are its
 * own ([postCount]), [unreadCount] of them unread.
 */
@Immutable
data class FeedChannel(
    val chatId: Long,
    val title: String,
    val photo: ProfilePhoto?,
    val postCount: Int,
    val unreadCount: Int,
)

/** How many cards [filter] lets through of [this] list of channels, for the sheet's "Show N posts". */
fun List<FeedChannel>.postCount(filter: FeedFilter): Int =
    filter { filter.chosen == null || it.chatId in filter.chosen }
        .sumOf { if (filter.onlyUnread) it.unreadCount else it.postCount }

/** What the feed's list shows. */
@Immutable
sealed interface FeedContent {
    /** The posts are on their way: skeleton cards. */
    data object Loading : FeedContent

    /** No unread post, and the read ones are not asked for ([FeedEvent.ShowReadPosts]). */
    data object NoNewPosts : FeedContent

    /**
     * The [unread] posts, newest first (those read since the feed opened keep their place, styled read), then the
     * [read] ones. [readHere] counts the posts read here: once none is unread, the "You're all caught up" card says how
     * many, in place of the "Earlier · read" divider.
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
