package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The profile's shared media (`REQUIREMENTS.md` FR-P2): the tab shown and what it holds, in the private chat
 * [chatId], which its items open in. Null [chatId] and [content] while the profile has no chat to search yet.
 */
@Immutable
data class SharedMediaUiState(
    val selected: SharedMediaKind = SharedMediaKind.PhotosAndVideos,
    val chatId: Long? = null,
    val content: SharedMediaContent? = null,
)

/** What the shown tab holds. */
@Immutable
sealed interface SharedMediaContent {
    data object Loading : SharedMediaContent

    /** The chat has nothing of the tab's kind. */
    data object Empty : SharedMediaContent

    /** The first page failed, because of [error]; [SharedMediaEvent.Retry]. */
    data class Error(
        val error: DomainError,
    ) : SharedMediaContent

    /** The items loaded so far, newest first, and whether more can load ([more]). */
    data class Items(
        val items: ImmutableList<SharedMediaItem>,
        val more: MoreMedia = MoreMedia.None,
    ) : SharedMediaContent
}

/** The end of a tab's items. */
@Immutable
sealed interface MoreMedia {
    /** Everything is loaded. */
    data object None : MoreMedia

    /** More can load, which the list asks for 20 items from its end ([SharedMediaEvent.LoadMore]). */
    data object Available : MoreMedia

    data object Loading : MoreMedia

    /** The next page failed, because of [error]; [SharedMediaEvent.LoadMore] tries again. */
    data class Failed(
        val error: DomainError,
    ) : MoreMedia
}

/**
 * An item of a tab, by the message it shows ([messageId]); the date and sizes are formatted where they are mapped.
 * `toString()` leaves names, titles and links out.
 */
@Immutable
sealed interface SharedMediaItem {
    val messageId: Long

    /** A photo, video or GIF in the grid; it opens in the media viewer. */
    data class Media(
        override val messageId: Long,
        val kind: MediaKind,
        /** A photo's sizes, from which the cell loads the smallest that fills it; null for videos and GIFs. */
        val photo: Photo?,
        /** A video's or GIF's preview image; null for photos, and for videos without one. */
        val thumbnail: Thumbnail?,
        val minithumbnail: Minithumbnail?,
        /** A video's length ("0:42"); null for photos and GIFs. */
        val duration: String?,
        val date: String,
    ) : SharedMediaItem {
        override fun toString() = "Media(messageId=$messageId, kind=$kind, …)"
    }

    /**
     * A file, a song or a voice message in a list; it opens in the chat, at its message. Its second line is [detail]
     * (a song's performer, a voice message's length), else the [size] in bytes, then the [date].
     */
    data class File(
        override val messageId: Long,
        val kind: FileKind,
        val title: UiText,
        val detail: String?,
        val size: Long,
        val date: String,
    ) : SharedMediaItem {
        override fun toString() = "File(messageId=$messageId, kind=$kind, …)"
    }

    /** A link found in a message's text or caption; it opens in the browser. */
    data class Link(
        override val messageId: Long,
        val url: String,
        /** The link preview's title, else the site's address. */
        val title: String,
        /** The link preview's description, else the link itself. */
        val description: String,
        val date: String,
    ) : SharedMediaItem {
        override fun toString() = "Link(messageId=$messageId, …)"
    }
}

/** What a [SharedMediaItem.Media] is: what screen readers hear, and whether it gets a play badge. */
enum class MediaKind {
    Photo,
    Video,
    Gif,
}

/** What a [SharedMediaItem.File] is: its icon. */
enum class FileKind {
    Document,
    Music,
    Voice,
}

/** What the user does in the shared media, handled by [ProfileViewModel.onMediaEvent]. */
sealed interface SharedMediaEvent {
    /** Show the tab of [kind]. */
    data class Select(
        val kind: SharedMediaKind,
    ) : SharedMediaEvent

    /** The list is 20 items from its end, or the next page failed and the user tries again. */
    data object LoadMore : SharedMediaEvent

    /** Load the first page again after it failed. */
    data object Retry : SharedMediaEvent
}

/** The tabs in their order: the profile shows each, as the chat may hold any. */
val SharedMediaTabs: ImmutableList<SharedMediaKind> =
    persistentListOf(
        SharedMediaKind.PhotosAndVideos,
        SharedMediaKind.Files,
        SharedMediaKind.Links,
        SharedMediaKind.Voice,
        SharedMediaKind.Music,
        SharedMediaKind.Gifs,
    )

/** What tapping an item does: open a photo, video or GIF in the media viewer, a file in the chat, a link. */
internal class SharedMediaActions(
    val onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    val onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
    val onOpenUrl: (url: String) -> Unit,
)
