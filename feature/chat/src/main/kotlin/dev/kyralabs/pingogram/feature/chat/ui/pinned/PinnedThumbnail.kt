package dev.kyralabs.pingogram.feature.chat.ui.pinned

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chat.ui.content.asPicture
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What the pinned messages bar shows before its text (the owner's board P1): a small picture of a photo, video, GIF or
 * round video, or a file's tile. Decorative: the preview beside it already says what the message is.
 */
@Immutable
sealed interface PinnedThumbnail {
    /** The picture of file [fileId]; [isVideo] adds a play symbol, [isRound] makes it a circle. */
    data class Picture(
        val fileId: Int,
        val isVideo: Boolean = false,
        val isRound: Boolean = false,
    ) : PinnedThumbnail

    /** A file or a song: the download symbol on `primaryContainer`. */
    data object File : PinnedThumbnail
}

/** The thumbnail of a pinned message with this content; null for text and kinds without one, and behind a spoiler. */
internal fun MessageContent.pinnedThumbnail(): PinnedThumbnail? =
    when (this) {
        is MessageContent.Photo -> {
            val size = photo.sizes.forTile()?.takeUnless { hasSpoiler }
            size?.let { PinnedThumbnail.Picture(it.file.id) }
        }

        is MessageContent.Video -> {
            video.thumbnail.picture(isVideo = true).takeUnless { hasSpoiler }
        }

        is MessageContent.Animation -> {
            animation.thumbnail.picture(isVideo = true).takeUnless { hasSpoiler }
        }

        is MessageContent.VideoNote -> {
            videoNote.thumbnail.picture(isVideo = true, isRound = true)
        }

        is MessageContent.Document, is MessageContent.Audio -> {
            PinnedThumbnail.File
        }

        else -> {
            null
        }
    }

/** The smallest size at least [THUMBNAIL_MIN_PX] on its longer side, or the largest. */
private fun List<PhotoSize>.forTile(): PhotoSize? =
    firstOrNull { maxOf(it.width, it.height) >= THUMBNAIL_MIN_PX } ?: lastOrNull()

private fun Thumbnail?.picture(
    isVideo: Boolean,
    isRound: Boolean = false,
): PinnedThumbnail.Picture? = this?.asPicture()?.let { PinnedThumbnail.Picture(it.file.id, isVideo, isRound) }

/** [thumbnail] as a `sizes.avatarSmall` tile with `medium` corners (a circle for a round video). */
@Composable
internal fun PinnedThumbnailTile(
    thumbnail: PinnedThumbnail,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val size = MaterialTheme.sizes.avatarSmall
    when (thumbnail) {
        is PinnedThumbnail.Picture -> {
            val sizePx = with(LocalDensity.current) { size.roundToPx() }
            val context = LocalPlatformContext.current
            val shape = if (thumbnail.isRound) CircleShape else MaterialTheme.shapes.medium
            Box(
                modifier.size(size).clip(shape).background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model =
                        remember(context, thumbnail.fileId, sizePx) {
                            ImageRequest
                                .Builder(context)
                                .data(TdlibFileImage(thumbnail.fileId))
                                .size(sizePx)
                                .build()
                        },
                    contentDescription = null,
                    modifier = Modifier.size(size),
                    contentScale = ContentScale.Crop,
                    colorFilter = MaterialTheme.pingogramColors.mediaColorFilter,
                )
                if (thumbnail.isVideo) {
                    Icon(
                        painterResource(UiR.drawable.ic_play_arrow_filled),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .background(MaterialTheme.pingogramColors.mediaOverlay, CircleShape)
                                .size(MaterialTheme.sizes.avatarInline),
                        tint = MaterialTheme.pingogramColors.onMediaOverlay,
                    )
                }
            }
        }

        PinnedThumbnail.File -> {
            Box(
                modifier.size(size).background(colors.primaryContainer, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_download),
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                )
            }
        }
    }
}

/** The smallest photo size to take for the tile: about its size on the densest screens. */
private const val THUMBNAIL_MIN_PX = 120
