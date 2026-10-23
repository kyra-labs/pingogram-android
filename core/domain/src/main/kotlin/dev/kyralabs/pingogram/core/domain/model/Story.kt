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

    /** A live stream: a group call Pingogram does not join from a story. */
    data object Live : StoryContent

    /** Content TDLib knows but this version of Telegram's API, or Pingogram, does not show. */
    data object Unsupported : StoryContent

    companion object {
        /** How long a photo (and what the viewer cannot show) stays on screen, as in Telegram's apps. */
        const val PHOTO_DURATION_MILLIS = 5_000L
    }
}
