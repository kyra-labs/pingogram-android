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
     */
    data class Content(
        val items: ImmutableList<MediaItem>,
        val currentIndex: Int,
        val count: MediaCount?,
        val files: ImmutableMap<Int, FileState> = persistentMapOf(),
        val playback: VideoPlayback? = null,
    ) : MediaViewerUiState {
        val current: MediaItem get() = items[currentIndex]
    }
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
 */
@Stable
data class VideoPlayback(
    val messageId: Long,
    val isPlaying: Boolean,
    val showsFrame: Boolean,
    val progress: () -> Float,
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

    /** The current video is to play from [fraction] (0–1) of its length. */
    data class Seek(
        val fraction: Float,
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
}
