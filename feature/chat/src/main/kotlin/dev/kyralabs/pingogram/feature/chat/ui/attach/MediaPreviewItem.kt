package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.content.MediaLabel
import dev.kyralabs.pingogram.feature.chat.ui.content.PlayBadge
import dev.kyralabs.pingogram.feature.chat.ui.content.documentKind
import dev.kyralabs.pingogram.feature.chat.ui.content.mediaDurationText
import java.io.File
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A photo or video of the attach preview ([AttachPreviewSheet]), [position] of [count]: its square thumbnail, or a
 * loading indicator while it is prepared, and its remove button at the top end. Screen readers hear "Photo 2 of 3",
 * or "Video 1 of 2, 0:12".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaPreviewItem(
    item: PreviewItem,
    position: Int,
    count: Int,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val size = MaterialTheme.sizes.attachmentPreview
    val attachment = (item as? PreviewItem.Ready)?.attachment
    val description = mediaItemDescription(attachment, position, count)
    Box(modifier.size(size)) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            MediaThumbnail(attachment, size)
        }
        val colors = MaterialTheme.pingogramColors
        FilledIconButton(
            onClick = onRemove,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(MaterialTheme.spacing.extraSmall)
                    .size(IconButtonDefaults.extraSmallContainerSize()),
            shapes = IconButtonDefaults.shapes(),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.mediaOverlay,
                    contentColor = colors.onMediaOverlay,
                ),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_close),
                contentDescription = stringResource(R.string.attach_remove, description),
                modifier = Modifier.size(IconButtonDefaults.extraSmallIconSize),
            )
        }
    }
}

/** What screen readers hear for [attachment], [position] of [count]; [attachment] is null while it is prepared. */
@Composable
private fun mediaItemDescription(
    attachment: Attachment?,
    position: Int,
    count: Int,
): String =
    when (attachment) {
        null -> {
            stringResource(R.string.attach_item_preparing, position, count)
        }

        is Attachment.Photo -> {
            stringResource(R.string.attach_item_photo, position, count)
        }

        is Attachment.Video -> {
            stringResource(R.string.attach_item_video, position, count, mediaDurationText(attachment.duration))
        }

        is Attachment.Document -> {
            stringResource(R.string.attach_item_file, attachment.fileName)
        }
    }

/**
 * The thumbnail of [attachment] at [size]: the photo, or the video's still with a play badge and its length; a
 * loading indicator while it is prepared, and its kind's icon for a file.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.MediaThumbnail(
    attachment: Attachment?,
    size: Dp,
) {
    when (attachment) {
        null -> {
            LoadingIndicator()
        }

        is Attachment.Photo -> {
            PreviewImage(attachment.path, size, Modifier.matchParentSize())
        }

        is Attachment.Video -> {
            attachment.thumbnail?.let { PreviewImage(it.path, size, Modifier.matchParentSize()) }
            PlayBadge()
            MediaLabel(
                text = mediaDurationText(attachment.duration),
                modifier = Modifier.align(Alignment.BottomStart).padding(MaterialTheme.spacing.small),
            )
        }

        is Attachment.Document -> {
            Icon(
                painter = painterResource(documentKind(attachment.fileName, attachment.mimeType).icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The image file at [path], decoded by Coil at [size] (`docs/PERFORMANCE.md` §4), cropped to fill. Decorative. */
@Composable
private fun PreviewImage(
    path: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val request =
        remember(context, path, sizePx) {
            ImageRequest
                .Builder(context)
                .data(File(path))
                .size(sizePx)
                .build()
        }
    AsyncImage(model = request, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}
