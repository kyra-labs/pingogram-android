package dev.kyralabs.pingogram.feature.chat.ui.pinned

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.ui.content.mediaDurationText
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The photo as wide as the card, as tall as its aspect ratio (at most `mediaMaxHeight`), `large` corners. */
@Composable
internal fun CardPhoto(photo: Photo) {
    val sizePx = with(LocalDensity.current) { MaterialTheme.sizes.mediaMaxWidth.roundToPx() }
    val best = remember(photo, sizePx) { photo.sizes.firstOrNull { it.width >= sizePx } ?: photo.sizes.lastOrNull() }
    val ratio = best?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 1f
    CardPicture(
        fileId = best?.file?.id,
        description = stringResource(UiR.string.preview_photo),
        modifier = Modifier.mediaFrame(ratio, MaterialTheme.sizes.mediaMaxHeight),
    )
}

/**
 * A video's or a GIF's still picture as a photo is shown, with its length (or "GIF") after the play symbol on a
 * `mediaOverlay` pill at its bottom start; a round video as a circle `mediaMinSize` wide. Nothing plays here: "Jump to
 * message" shows it in the chat.
 */
@Composable
internal fun CardClip(clip: PinnedClip) {
    val label = if (clip.isGif) stringResource(UiR.string.preview_gif) else mediaDurationText(clip.duration)
    val kind =
        when {
            clip.isGif -> stringResource(UiR.string.preview_gif)
            clip.isRound -> stringResource(UiR.string.preview_video_message)
            else -> stringResource(UiR.string.preview_video)
        }
    Box(contentAlignment = Alignment.BottomStart) {
        if (clip.isRound) {
            CardPicture(
                fileId = clip.fileId,
                description = kind,
                modifier = Modifier.size(MaterialTheme.sizes.mediaMinSize),
                shape = CircleShape,
            )
        } else {
            CardPicture(
                fileId = clip.fileId,
                description = kind,
                modifier =
                    Modifier.mediaFrame(
                        clip.width.toFloat() / clip.height.coerceAtLeast(1),
                        MaterialTheme.sizes.mediaMaxHeight,
                    ),
            )
        }
        ClipLabel(label, Modifier.padding(MaterialTheme.spacing.small))
    }
}

/** The play symbol and the length on a `mediaOverlay` pill. Decorative: the picture names the kind. */
@Composable
private fun ClipLabel(
    label: String,
    modifier: Modifier = Modifier,
) {
    val overlay = MaterialTheme.pingogramColors
    Row(
        modifier =
            modifier
                .background(overlay.mediaOverlay, CircleShape)
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall)
                .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_play_arrow_filled),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.badgeIcon),
            tint = overlay.onMediaOverlay,
        )
        Text(label, color = overlay.onMediaOverlay, style = MaterialTheme.typography.labelSmall)
    }
}

/** As wide as the card and as tall as [ratio] makes it, at most [maxHeight] (`sizes.mediaMaxHeight`). */
private fun Modifier.mediaFrame(
    ratio: Float,
    maxHeight: Dp,
): Modifier = fillMaxWidth().heightIn(max = maxHeight).aspectRatio(ratio)

/** File [fileId]'s picture, cropped to [modifier]'s size, dimmed in dark theme as the chat's media. */
@Composable
private fun CardPicture(
    fileId: Int?,
    description: String,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
) {
    val sizePx = with(LocalDensity.current) { MaterialTheme.sizes.mediaMaxWidth.roundToPx() }
    val context = LocalPlatformContext.current
    Surface(modifier = modifier.clip(shape), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        fileId?.let { id ->
            AsyncImage(
                model =
                    remember(context, id, sizePx) {
                        ImageRequest
                            .Builder(context)
                            .data(TdlibFileImage(id))
                            .size(sizePx)
                            .build()
                    },
                contentDescription = description,
                contentScale = ContentScale.Crop,
                colorFilter = MaterialTheme.pingogramColors.mediaColorFilter,
            )
        }
    }
}
