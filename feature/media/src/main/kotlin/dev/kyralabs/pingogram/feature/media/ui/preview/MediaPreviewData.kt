package dev.kyralabs.pingogram.feature.media.ui.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import coil3.ColorImage
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.ThumbnailFormat
import dev.kyralabs.pingogram.core.domain.model.Video
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.media.ui.DeleteConfirmation
import dev.kyralabs.pingogram.feature.media.ui.DeleteScope
import dev.kyralabs.pingogram.feature.media.ui.MediaAction
import dev.kyralabs.pingogram.feature.media.ui.MediaActions
import dev.kyralabs.pingogram.feature.media.ui.MediaCount
import dev.kyralabs.pingogram.feature.media.ui.MediaItem
import dev.kyralabs.pingogram.feature.media.ui.MediaViewerUiState
import dev.kyralabs.pingogram.feature.media.ui.VideoPlayback
import dev.kyralabs.pingogram.feature.media.ui.ViewerMedia
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * The viewer's states for previews and screenshot tests: a photo with a caption (with Share and Save, or in a chat
 * that protects its content), a video in the player, a photo still downloading (and waiting to be shared), a hidden
 * spoiler, and the error, unavailable and loading states. Images come from [PreviewImages].
 */
internal object MediaPreviewData {
    private const val PHOTO = 1L
    private const val VIDEO = 2L
    private const val GIF = 3L
    private const val SPOILER = 4L

    private val samplePhoto =
        Photo(
            sizes =
                persistentListOf(
                    PhotoSize("m", 320, 240, FileRef(10, 20_000)),
                    PhotoSize("y", 1280, 960, FileRef(11, 240_000)),
                ),
            minithumbnail = null,
        )

    private val caption =
        FormattedText(
            "The Analytical Engine weaves algebraic patterns just as the Jacquard loom weaves flowers and leaves. " +
                "Notes on the engine, part one: the mill, the store and the cards.",
            persistentListOf(TextEntity(offset = 4, length = 17, type = TextEntityType.Bold)),
        )

    private val photoItem =
        MediaItem(
            messageId = PHOTO,
            media = ViewerMedia.Image(samplePhoto, samplePhoto.sizes.last()),
            sender = UiText.Plain("Ada Lovelace"),
            date = "Oct 18, 9:41 AM",
            caption = caption,
            isHidden = false,
        )

    private val sampleVideo =
        Video(
            duration = 95,
            width = 1920,
            height = 1080,
            minithumbnail = null,
            thumbnail = Thumbnail(ThumbnailFormat.Jpeg, 320, 180, FileRef(20, 9_000)),
            supportsStreaming = true,
            file = FileRef(21, 12_000_000),
        )

    private val videoItem =
        MediaItem(
            messageId = VIDEO,
            media = ViewerMedia.Clip(sampleVideo),
            sender = UiText.Plain("Charles Babbage"),
            date = "Oct 17, 6:05 PM",
            caption = FormattedText("", persistentListOf()),
            isHidden = false,
        )

    private val gifItem =
        MediaItem(
            messageId = GIF,
            media =
                ViewerMedia.Gif(
                    Animation(3, 480, 270, "video/mp4", null, null, FileRef(31, 400_000)),
                ),
            sender = UiText.Plain("Ada Lovelace"),
            date = "Oct 16, 8:00 AM",
            caption = FormattedText("", persistentListOf()),
            isHidden = false,
        )

    private val spoilerItem =
        photoItem.copy(messageId = SPOILER, caption = FormattedText("", persistentListOf()), isHidden = true)

    private val items = persistentListOf(photoItem, videoItem, gifItem, spoilerItem)

    private val count = MediaCount(firstNumber = 3, total = 120)

    /** The photo, its full size on the device, with its caption. */
    val photo: MediaViewerUiState.Content =
        MediaViewerUiState.Content(
            items = items,
            currentIndex = 0,
            count = count,
            files = persistentMapOf(11 to FileState.Downloaded("/files/11.jpg", 240_000)),
        )

    /** The photo while its full size downloads, 40% done. */
    val downloading: MediaViewerUiState.Content =
        photo.copy(files = persistentMapOf(11 to FileState.Downloading(96_000, 240_000, isActive = true)))

    /** The photo in a chat that lets its content leave Telegram: Share and Save in the toolbar, Copy in its menu. */
    val withActions: MediaViewerUiState.Content = photo.copy(actions = MediaActions(canSave = true))

    /** The photo the user may also forward and delete: Forward, Share, Save and Delete in the toolbar. */
    val allActions: MediaViewerUiState.Content =
        photo.copy(actions = MediaActions(canForward = true, canSave = true, canDelete = true))

    /** Delete tapped on a photo the user may delete for everyone too: the confirmation with its check box. */
    val deleteConfirmation: MediaViewerUiState.Content =
        allActions.copy(
            actions = allActions.actions.copy(deleteConfirmation = DeleteConfirmation(PHOTO, DeleteScope.Choice)),
        )

    /** The photo in a chat that protects its content: no Share, no Save, no Copy. */
    val protectedContent: MediaViewerUiState.Content = photo.copy(actions = MediaActions(canSave = false))

    /** Share tapped while the full size downloads: the button shows the download's progress. */
    val sharing: MediaViewerUiState.Content =
        downloading.copy(actions = MediaActions(canSave = true, pending = MediaAction.Share))

    /** The video in the player, a third played, its first frame not on the (absent) surface. */
    val video: MediaViewerUiState.Content =
        MediaViewerUiState.Content(
            items = items,
            currentIndex = 1,
            count = count,
            files = persistentMapOf(21 to FileState.Downloaded("/files/21.mp4", 12_000_000)),
            playback = VideoPlayback(VIDEO, isPlaying = true, showsFrame = false, progress = { VIDEO_PROGRESS }),
        )

    /** The video while power saving keeps it from playing by itself: its play button waits for a tap. */
    val videoWaitingForTap: MediaViewerUiState.Content =
        video.copy(
            playback =
                VideoPlayback(VIDEO, isPlaying = false, showsFrame = false, progress = { 0f }, waitsForTap = true),
        )

    /** A spoiler not revealed yet. */
    val spoiler: MediaViewerUiState.Content = photo.copy(currentIndex = 3)

    val error = MediaViewerUiState.Error(DomainError.Network)

    val unavailable = MediaViewerUiState.Unavailable

    val loading = MediaViewerUiState.Loading

    private const val VIDEO_PROGRESS = 0.33f
}

/**
 * Images for previews and screenshot tests, which Coil takes from `LocalAsyncImagePreviewHandler` in inspection mode
 * only: TDLib files as solid colors of the theme, picked by file id, or, with [loading], kept loading, so the
 * placeholders show.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
internal fun PreviewImages(
    loading: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val previewHandler =
        remember(colorScheme, loading) {
            if (loading) {
                object : AsyncImagePreviewHandler {
                    override suspend fun handle(
                        imageLoader: ImageLoader,
                        request: ImageRequest,
                    ): AsyncImagePainter.State = AsyncImagePainter.State.Loading(painter = null)
                }
            } else {
                val palette = listOf(colorScheme.tertiary, colorScheme.secondary, colorScheme.primary)
                AsyncImagePreviewHandler { request ->
                    val fileId = (request.data as? TdlibFileImage)?.fileId ?: 0
                    ColorImage(palette[fileId.mod(palette.size)].toArgb())
                }
            }
        }
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides previewHandler, content = content)
}

/** Every state of the media viewer, for its previews. */
internal class MediaViewerPreviewStates : PreviewParameterProvider<MediaViewerUiState> {
    override val values: Sequence<MediaViewerUiState> =
        with(MediaPreviewData) {
            sequenceOf(
                photo,
                withActions,
                allActions,
                deleteConfirmation,
                sharing,
                video,
                videoWaitingForTap,
                downloading,
                spoiler,
                loading,
                error,
                unavailable,
            )
        }
}
