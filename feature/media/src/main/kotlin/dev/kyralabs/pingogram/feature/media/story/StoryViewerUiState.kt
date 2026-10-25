package dev.kyralabs.pingogram.feature.media.story

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import kotlinx.collections.immutable.ImmutableList

/** What the stories viewer shows (`docs/DESIGN_SYSTEM.md` §6, Story viewer). */
@Immutable
sealed interface StoryViewerUiState {
    /** The chats to page through are not known yet. */
    data object Loading : StoryViewerUiState

    /**
     * The [posters] to swipe between, [current] among them, and the [story] of the current one; [isPaused] while a
     * finger holds the story, a sheet or the keyboard is up or the screen is stopped; [isMuted] for videos. [reply] is
     * the reply field, null where the story cannot be replied to (channels, the user's own); [reactions] the reactions
     * the user may put on the story, null while not known.
     */
    data class Content(
        val posters: ImmutableList<StoryPosterUi>,
        val current: Int,
        val story: StoryUi,
        val isPaused: Boolean,
        val isMuted: Boolean,
        val reply: StoryReplyUi?,
        val reactions: StoryReactionsUi?,
    ) : StoryViewerUiState
}

/**
 * A chat whose stories are a page of the viewer: its [name] and [photo], how many stories it has ([storyCount]) and
 * which one its page shows or opens at ([shownIndex]).
 */
@Immutable
data class StoryPosterUi(
    val chatId: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val storyCount: Int,
    val shownIndex: Int,
)

/**
 * The story on screen: story [storyId], number [index] of the poster's [count], posted at [date] (Unix seconds),
 * [ageSeconds] ago when it showed; what it shows ([media]) with its [caption]; and how far it played, 0–1
 * ([progress]), read in the draw phase.
 */
@Immutable
data class StoryUi(
    val storyId: Int,
    val index: Int,
    val count: Int,
    val date: Long,
    val ageSeconds: Long,
    val media: StoryMedia,
    val caption: FormattedText,
    val progress: () -> Float,
)

/** What the story on screen shows. */
@Immutable
sealed interface StoryMedia {
    /** The story itself is still on its way from TDLib. */
    data object Loading : StoryMedia

    /**
     * A photo: TDLib file [fileId], [width] × [height] pixels, with the [minithumbnail] blurred under it; [isReady]
     * once its file is on the device and its time runs.
     */
    data class Photo(
        val fileId: Int,
        val width: Int,
        val height: Int,
        val minithumbnail: Minithumbnail?,
        val isReady: Boolean,
    ) : StoryMedia

    /**
     * A video, [width] × [height] pixels: the [thumbnailFileId] (or the [minithumbnail]) until its first frame shows
     * ([showsFrame]), [isReady] once its file is on the device and it plays; [hasSound] unless it is an animation,
     * so the mute button shows.
     */
    data class Video(
        val width: Int,
        val height: Int,
        val minithumbnail: Minithumbnail?,
        val thumbnailFileId: Int?,
        val isReady: Boolean,
        val showsFrame: Boolean,
        val hasSound: Boolean,
    ) : StoryMedia

    /** A live stream, which Pingogram does not join from a story. */
    data object Live : StoryMedia

    /** Content Pingogram can't show yet. */
    data object Unsupported : StoryMedia

    /** The story was deleted or expired. */
    data object Unavailable : StoryMedia

    /** The story or its file could not be loaded; "Try again". */
    data class Failed(
        val error: DomainError,
    ) : StoryMedia
}

/** The reply field: [isSending] while a reply goes out. */
@Immutable
data class StoryReplyUi(
    val isSending: Boolean = false,
)

/**
 * The reactions the user may put on the story ([available], emoji only, the quick ones first), the one chosen
 * ([chosen], or null), and whether their row is open ([isOpen]); [isSending] while TDLib takes a new one.
 */
@Immutable
data class StoryReactionsUi(
    val available: ImmutableList<ReactionType.Emoji>,
    val chosen: ReactionType?,
    val isOpen: Boolean = false,
    val isSending: Boolean = false,
)

/** What happens in the stories viewer, handled by [StoryViewerViewModel.onEvent]. */
sealed interface StoryViewerEvent {
    /** The screen was started or stopped: the story plays only while started, and is open for TDLib meanwhile. */
    data class Started(
        val started: Boolean,
    ) : StoryViewerEvent

    /** A tap on the end side of the story (the right in left-to-right layouts). */
    data object Next : StoryViewerEvent

    /** A tap on the start side. */
    data object Previous : StoryViewerEvent

    /** A finger holds the story ([held]), or let go. */
    data class Hold(
        val held: Boolean,
    ) : StoryViewerEvent

    /** The pager is dragged or settling ([moving]), which pauses the story. */
    data class Paging(
        val moving: Boolean,
    ) : StoryViewerEvent

    /** The pager settled on poster [index]. */
    data class PosterSettled(
        val index: Int,
    ) : StoryViewerEvent

    data object ToggleMute : StoryViewerEvent

    /** A volume key was pressed: a muted video gets its sound back, as the key also changes the volume. */
    data object Unmute : StoryViewerEvent

    /** The reply field took the focus ([focused]: the keyboard is up), or lost it. */
    data class ReplyFocused(
        val focused: Boolean,
    ) : StoryViewerEvent

    /** Sends the reply field's text. */
    data object SendReply : StoryViewerEvent

    /** Opens or closes the row of reactions ([open]). */
    data class ReactionsOpen(
        val open: Boolean,
    ) : StoryViewerEvent

    /** Puts [reaction] on the story, or takes the chosen one back when it is [reaction] again. */
    data class React(
        val reaction: ReactionType.Emoji,
    ) : StoryViewerEvent

    data object Retry : StoryViewerEvent
}

/** One-off side effects of [StoryViewerViewModel], handled once by the route. */
sealed interface StoryViewerEffect {
    /** The last story of the last chat ended, or there is nothing to show: the viewer closes. */
    data object Close : StoryViewerEffect

    /** A reply was sent: a snackbar says so. */
    data object ReplySent : StoryViewerEffect

    /** The player could not play the video: its picture shows for a photo's time, and a snackbar says why. */
    data object PlaybackFailed : StoryViewerEffect

    data class ShowError(
        val error: DomainError,
    ) : StoryViewerEffect
}
