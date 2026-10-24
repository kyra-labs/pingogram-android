package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.Video
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/** Everything the media viewer shows (`REQUIREMENTS.md` FR-C10), from [MediaViewerViewModel]. */
@Immutable
sealed interface MediaViewerUiState {
    /** The media around the opened message is loading. */
    data object Loading : MediaViewerUiState

    /** Loading it failed with [error]; "Try again" loads it again. */
    data class Error(
        val error: DomainError,
    ) : MediaViewerUiState

    /** The message was deleted, or holds nothing the viewer shows: "This media is no longer available". */
    data object Unavailable : MediaViewerUiState

    /**
     * The loaded media, a page each.
     *
     * @property items oldest first, as the pager shows them: newer media to the end, so swiping forward goes forward in
     *   time, as the conversation reads (mirrored in right-to-left layouts).
     * @property currentIndex the item shown, in [items].
     * @property count where the items are among all of the chat's media of their kind, for "3 of 120"; null while
     *   unknown.
     * @property files the states of the files of the shown item and its neighbours, by file id: each item's full file
     *   ([ViewerMedia.fileId]) and a photo's other sizes; a file TDLib has not reported yet is missing.
     * @property playback the video or GIF in the player; null when none is.
     * @property actions what the toolbar offers for the current item: Share, Save, Delete, and in its menu Copy.
     */
    data class Content(
        val items: ImmutableList<MediaItem>,
        val currentIndex: Int,
        val count: MediaCount?,
        val files: ImmutableMap<Int, FileState> = persistentMapOf(),
        val playback: VideoPlayback? = null,
        val actions: MediaActions = MediaActions(),
    ) : MediaViewerUiState {
        val current: MediaItem get() = items[currentIndex]
    }
}

/**
 * What the toolbar offers for the current item (`REQUIREMENTS.md` FR-C6, FR-C10): Share, Save and Copy from
 * `MediaSharing`, Forward and Delete from `MediaDeletion`. "Show in chat" is always there.
 *
 * @property canForward Forward shows: TDLib says the message can be forwarded (never protected content). False while
 *   that is not known.
 * @property canSave Share, Save and (for photos) Copy show: Telegram lets the content leave it (the chat does not
 *   protect its content, and TDLib says the message can be saved). False while that is not known.
 * @property canDelete Delete shows: TDLib says the message can be deleted, for the user or for everyone. False while
 *   that is not known.
 * @property pending the action waiting for the item's file to download, or copying it into the gallery; null if none.
 * @property showsStorageRationale Android 8–9: why saving needs the storage permission shows, before asking for it.
 * @property deleteConfirmation the confirmation before the current item is deleted, while it shows; null otherwise.
 */
@Immutable
data class MediaActions(
    val canForward: Boolean = false,
    val canSave: Boolean = false,
    val canDelete: Boolean = false,
    val pending: MediaAction? = null,
    val showsStorageRationale: Boolean = false,
    val deleteConfirmation: DeleteConfirmation? = null,
)

/** What the toolbar does with the current item's file. */
enum class MediaAction {
    /** Hand it to another app through the system's share sheet. */
    Share,

    /** Copy it into the gallery, `Pictures/Pingogram` or `Movies/Pingogram`. */
    Save,

    /** Put the photo on the clipboard, for pasting into another app. */
    Copy,
}

/** The confirmation before message [messageId]'s media is deleted, for whom [scope] says. */
@Immutable
data class DeleteConfirmation(
    val messageId: Long,
    val scope: DeleteScope,
)

/** For whom deleting a message deletes it, as TDLib allows (`getMessageProperties`). */
enum class DeleteScope {
    /** Only for the user; the others keep it. */
    ForMe,

    /** For everyone in the chat (supergroups, channels, and messages only one side has). */
    ForEveryone,

    /** For the user, or, if they choose, for everyone. */
    Choice,
}

/**
 * One photo, video or GIF of the viewer, from message [messageId].
 *
 * @property sender who sent it: a user's full name, a chat's title, or "Someone".
 * @property date when it was sent, e.g. "Oct 18, 9:41 AM", or "Oct 18, 2025, 9:41 AM" in another year.
 * @property caption its caption; empty if none.
 * @property isHidden a spoiler not revealed yet: it shows blurred until tapped. The opened item is revealed: tapping it
 *   in the conversation was the choice to see it.
 */
@Immutable
data class MediaItem(
    val messageId: Long,
    val media: ViewerMedia,
    val sender: UiText,
    val date: String,
    val caption: FormattedText,
    val isHidden: Boolean,
) {
    override fun toString() = "MediaItem(messageId=$messageId, media=${media::class.simpleName}, …)"
}

/** What an item shows: its size in pixels, the blurred [minithumbnail] until it loads, and the [fileId] it plays. */
@Immutable
sealed interface ViewerMedia {
    val width: Int
    val height: Int
    val minithumbnail: Minithumbnail?

    /** The file the viewer downloads in full: the largest size of a photo, a video's or GIF's own file. */
    val fileId: Int

    /** [photo] at its [full] size, the largest one; Telegram sends no larger one than about 2560 px. */
    data class Image(
        val photo: Photo,
        val full: PhotoSize,
    ) : ViewerMedia {
        override val width get() = full.width
        override val height get() = full.height
        override val minithumbnail get() = photo.minithumbnail
        override val fileId get() = full.file.id
    }

    data class Clip(
        val video: Video,
    ) : ViewerMedia {
        override val width get() = video.width
        override val height get() = video.height
        override val minithumbnail get() = video.minithumbnail
        override val fileId get() = video.file.id

        /** Shown until the first frame plays. */
        val thumbnail: Thumbnail? get() = video.thumbnail
    }

    data class Gif(
        val animation: Animation,
    ) : ViewerMedia {
        override val width get() = animation.width
        override val height get() = animation.height
        override val minithumbnail get() = animation.minithumbnail
        override val fileId get() = animation.file.id

        /** Shown until the first frame plays. */
        val thumbnail: Thumbnail? get() = animation.thumbnail
    }
}

/** Item [number] (1 for the oldest) of [total]: "3 of 120". */
@Immutable
data class MediaPosition(
    val number: Int,
    val total: Int,
)

/**
 * Where the loaded items are among the chat's [total] media of their kind: the first one is [firstNumber] (1 for the
 * oldest), and the others follow, as the items have no gaps.
 */
@Immutable
data class MediaCount(
    val firstNumber: Int,
    val total: Int,
) {
    /** The position of the item at [index], kept within 1 and [total], which are counts TDLib only estimates. */
    fun positionOf(index: Int): MediaPosition = MediaPosition((firstNumber + index).coerceIn(1, total), total)
}

/**
 * The video or GIF of message [messageId] in the player: playing or paused as [isPlaying] says, and [showsFrame] once
 * its first frame is on the surface, which uncovers it. How far a video has played is snapshot state that [progress]
 * reads (0–1), so the position moving re-emits nothing: only what reads it in the draw or layout phase follows it.
 * [waitsForTap] while power saving keeps it from playing by itself, until its play button is tapped.
 */
@Stable
data class VideoPlayback(
    val messageId: Long,
    val isPlaying: Boolean,
    val showsFrame: Boolean,
    val progress: () -> Float,
    /** How far the player has the video, 0–1, read in the seek bar's draw phase. */
    val buffered: () -> Float = progress,
    val waitsForTap: Boolean = false,
)

/** What the user and the screen's lifecycle do in the viewer, sent to [MediaViewerViewModel.onEvent]. */
sealed interface MediaViewerEvent {
    /** The screen is started: a video or GIF waiting for it plays. */
    data object Started : MediaViewerEvent

    /** The screen is stopped: what plays pauses. */
    data object Stopped : MediaViewerEvent

    /** The pager settled on the item of message [messageId]: it is the current one. */
    data class PageSettled(
        val messageId: Long,
    ) : MediaViewerEvent

    /** "Try again" after loading failed. */
    data object Retry : MediaViewerEvent

    /** The hidden spoiler of message [messageId] was tapped: it shows. */
    data class RevealSpoiler(
        val messageId: Long,
    ) : MediaViewerEvent

    /** The play or pause button of the current video was tapped. */
    data object PlayPause : MediaViewerEvent

    /** The video goes on in picture-in-picture: the button beside its seek bar, or a swipe up. */
    data object PictureInPicture : MediaViewerEvent

    /** The current video is to play from [fraction] (0–1) of its length. */
    data class Seek(
        val fraction: Float,
    ) : MediaViewerEvent

    /** Share was tapped: the current item's file goes to the share sheet, once it is downloaded. */
    data object Share : MediaViewerEvent

    /** Save was tapped: the current item's file is copied into the gallery, once it is downloaded. */
    data object Save : MediaViewerEvent

    /** Copy was chosen: the current photo goes to the clipboard, once it is downloaded. */
    data object Copy : MediaViewerEvent

    /** Delete was tapped: the confirmation shows. */
    data object Delete : MediaViewerEvent

    /** The confirmation was answered with Delete: for everyone if the user chose that ([forEveryone]). */
    data class DeleteConfirmed(
        val forEveryone: Boolean,
    ) : MediaViewerEvent

    /** The confirmation was cancelled. */
    data object DeleteDismissed : MediaViewerEvent

    /** "Show in chat" was chosen: the conversation opens at the current item's message. */
    data object ShowInChat : MediaViewerEvent

    /** Forward was tapped: the forward sheet opens for the current item's message. */
    data object Forward : MediaViewerEvent

    /**
     * The screen checked, after [MediaViewerEffect.CheckStoragePermission], whether the system wants the reason for
     * the storage permission shown first ([showRationale]).
     */
    data class StoragePermissionChecked(
        val showRationale: Boolean,
    ) : MediaViewerEvent

    /** The reason for the storage permission was answered: ask for it ([proceed]), or leave it. */
    data class StorageRationaleAnswered(
        val proceed: Boolean,
    ) : MediaViewerEvent

    /** The system's answer to the storage permission request ([MediaViewerEffect.RequestStoragePermission]). */
    data class StoragePermissionResult(
        val granted: Boolean,
    ) : MediaViewerEvent
}

/** One-off effects of [MediaViewerViewModel], handled by the screen once each. */
sealed interface MediaViewerEffect {
    /** Loading more media or downloading a file failed: a snackbar shows the error, none for `DomainError.Silent`. */
    data class ShowError(
        val error: DomainError,
    ) : MediaViewerEffect

    /** The video or GIF could not be played: a snackbar says so. */
    data object PlaybackFailed : MediaViewerEffect

    /** The video was handed to picture-in-picture: its window opens, and the viewer closes. */
    data object OpenPictureInPicture : MediaViewerEffect

    /**
     * Hand the downloaded file at [path] to the system's share sheet, as the type its extension names, else
     * [mimeType].
     */
    data class ShareFile(
        val path: String,
        val mimeType: String,
    ) : MediaViewerEffect {
        // The path ends in the file's name.
        override fun toString() = "ShareFile(mimeType=$mimeType, …)"
    }

    /** The file could not be shared (TDLib cannot download it): a snackbar says so. */
    data object ShareFailed : MediaViewerEffect

    /** The file is in the gallery: a snackbar says so. */
    data object SavedToGallery : MediaViewerEffect

    /** Put the downloaded photo at [path] on the clipboard, as the type its extension names, else [mimeType]. */
    data class CopyImage(
        val path: String,
        val mimeType: String,
    ) : MediaViewerEffect {
        // The path ends in the file's name.
        override fun toString() = "CopyImage(mimeType=$mimeType, …)"
    }

    /** The photo could not be copied (TDLib cannot download it): a snackbar says so. */
    data object CopyFailed : MediaViewerEffect

    /** Open the conversation of chat [chatId] at message [messageId]. */
    data class ShowInChat(
        val chatId: Long,
        val messageId: Long,
    ) : MediaViewerEffect

    /** Open the forward sheet for message [messageId] of chat [chatId]. */
    data class Forward(
        val chatId: Long,
        val messageId: Long,
    ) : MediaViewerEffect

    /** The user deleted the last item the viewer showed: it closes. */
    data object Close : MediaViewerEffect

    /** The file could not be saved into the gallery: a snackbar says so. */
    data object SaveFailed : MediaViewerEffect

    /**
     * Android 8–9, saving needs the storage permission: check whether the system wants its reason shown first, and
     * tell [MediaViewerEvent.StoragePermissionChecked].
     */
    data object CheckStoragePermission : MediaViewerEffect

    /** Ask for the storage permission, and tell [MediaViewerEvent.StoragePermissionResult]. */
    data object RequestStoragePermission : MediaViewerEffect

    /** The storage permission was refused: a snackbar says saving needs it, and offers the app's settings. */
    data object StoragePermissionDenied : MediaViewerEffect
}

/** Whether the current item is a video that plays, which a swipe up hands to picture-in-picture. */
internal val MediaViewerUiState.Content?.playsVideo: Boolean
    get() =
        this != null &&
            playback?.messageId == current.messageId &&
            current.media is ViewerMedia.Clip

/** The action on the current item's file that [MediaViewerEvent.Share], `Save` and `Copy` ask for; null for others. */
internal val MediaViewerEvent.mediaAction: MediaAction?
    get() =
        when (this) {
            MediaViewerEvent.Share -> MediaAction.Share
            MediaViewerEvent.Save -> MediaAction.Save
            MediaViewerEvent.Copy -> MediaAction.Copy
            else -> null
        }
