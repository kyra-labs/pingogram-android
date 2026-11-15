package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

// Stories, read only (`docs/ROADMAP.md` M6): the users' and channels' active stories, as `StoryRepository` keeps them,
// and one story as the viewer shows it. Times are Unix timestamps in seconds.

/** The two lists TDLib sorts the chats with active stories into (td_api.tl `StoryList`). */
enum class StoryList {
    /** The stories above the chat list. */
    Main,

    /** The stories the user hid (archived). */
    Archive,
}

/**
 * The active stories of chat [chatId] (a user's private chat, a channel or a supergroup), as TDLib announces them with
 * `updateChatActiveStories`: in [list] at [order] (chats sort by `order`, then chat id, both descending; null for a
 * chat in no list), oldest story first, and the newest the user has seen, [maxReadStoryId].
 */
data class ActiveStories(
    val chatId: Long,
    val list: StoryList?,
    val order: Long,
    val maxReadStoryId: Int,
    val stories: ImmutableList<StoryInfo>,
) {
    /** Whether a story is newer than the last one the user has seen: its ring shows as unseen. */
    val hasUnread: Boolean get() = stories.any { it.id > maxReadStoryId }

    /**
     * Where the viewer starts in these stories: at the oldest the user has not seen, or at the first when all are seen
     * (Telegram's apps do the same); 0 without stories.
     */
    val firstUnreadIndex: Int get() = stories.indexOfFirst { it.id > maxReadStoryId }.coerceAtLeast(0)
}

/** One active story, without its content: [id], posted at [date]; a live stream if [isLive]. */
data class StoryInfo(
    val id: Int,
    val date: Long,
    val isForCloseFriends: Boolean = false,
    val isLive: Boolean = false,
)

/**
 * A story as TDLib gives it (td_api.tl `story`), as far as a read-only viewer uses it: [posterChatId] posted it at
 * [date]; what it shows ([content]) with its [caption]; whether the user may reply to it in the poster's private chat
 * ([canBeReplied]); the reaction the user chose ([chosenReaction], an emoji, or null). Its areas (links, places,
 * reactions placed on the picture) are left out: the viewer does not show them.
 *
 * `toString()` leaves the caption out, so a story can be logged.
 */
data class Story(
    val id: Int,
    val posterChatId: Long,
    val date: Long,
    val content: StoryContent,
    val caption: FormattedText,
    val canBeReplied: Boolean,
    val chosenReaction: ReactionType?,
    /** Whether the user may delete it: their own story (td_api.tl `story.can_be_deleted`). */
    val canBeDeleted: Boolean = false,
    /** Who saw and reacted, for the user's own story (td_api.tl `story.interaction_info`); null for others. */
    val interactions: StoryInteractionInfo? = null,
) {
    override fun toString() = "Story(id=$id, posterChatId=$posterChatId, content=$content, …)"
}

/** What a [Story] shows. */
sealed interface StoryContent {
    /** A photo, shown for [StoryContent.PHOTO_DURATION_MILLIS]. */
    data class Photo(
        val photo: dev.kyralabs.pingogram.core.domain.model.Photo,
    ) : StoryContent

    /**
     * A video, [durationMillis] long, [width] × [height] pixels, with its [minithumbnail] and [thumbnail] to show while
     * it downloads; [isAnimation] if it has no sound.
     */
    data class Video(
        val durationMillis: Long,
        val width: Int,
        val height: Int,
        val isAnimation: Boolean,
        val minithumbnail: Minithumbnail?,
        val thumbnail: Thumbnail?,
        val file: FileRef,
    ) : StoryContent

    /** A live story: the poster streams in a group call, which the viewer offers to watch on the voice chat screen. */
    data class Live(
        /** The poster's group call (td_api.tl `storyContentLive.group_call_id`), which the user may watch. */
        val groupCallId: Int,
    ) : StoryContent

    /** Content TDLib knows but this version of Telegram's API, or Pingogram, does not show. */
    data object Unsupported : StoryContent

    companion object {
        /** How long a photo (and what the viewer cannot show) stays on screen, as in Telegram's apps. */
        const val PHOTO_DURATION_MILLIS = 5_000L
    }
}

/**
 * How many saw the user's own story ([viewCount]) and reacted to it ([reactionCount]), and the users who saw it last
 * ([recentViewerIds], newest first, at most a few), as TDLib reports them with the story.
 */
data class StoryInteractionInfo(
    val viewCount: Int,
    val reactionCount: Int,
    val recentViewerIds: ImmutableList<Long>,
)

/** User [userId] saw the user's own story at [date], and reacted with [reaction] (null for none). */
data class StoryViewer(
    val userId: Long,
    val date: Long,
    val reaction: ReactionType?,
)

/**
 * A page of who saw the user's own story (td_api.tl `getStoryInteractions`): [viewers] of [totalCount] (with
 * [reactionCount] reactions among them), and where the next page starts ([nextOffset], null at the end).
 */
data class StoryViewersPage(
    val totalCount: Int,
    val reactionCount: Int,
    val viewers: ImmutableList<StoryViewer>,
    val nextOffset: String?,
)

