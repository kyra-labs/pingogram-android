package dev.kyralabs.pingogram.feature.chat.ui.rich

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButtonType
import dev.kyralabs.pingogram.core.domain.model.RichBlock
import dev.kyralabs.pingogram.core.domain.model.RichCaption
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.content.GifBadge
import dev.kyralabs.pingogram.feature.chat.ui.content.PhotoImage
import dev.kyralabs.pingogram.feature.chat.ui.content.PlayBadge
import dev.kyralabs.pingogram.feature.chat.ui.content.VideoStill
import dev.kyralabs.pingogram.feature.chat.ui.content.VideoThumbnail
import dev.kyralabs.pingogram.feature.chat.ui.content.largestPhotoSize
import dev.kyralabs.pingogram.feature.chat.ui.content.mediaDisplaySize
import dev.kyralabs.pingogram.feature.chat.ui.content.still
import kotlinx.collections.immutable.ImmutableList

/**
 * A photo, video or GIF of a rich message, as wide as the bubble's content, as tall as its aspect ratio makes it at
 * `mediaMaxWidth` (within `mediaMinSize` and `mediaMaxHeight`), cropped to fill it, with `medium` corners; its
 * caption below it. Photos load the smallest size that covers the box, dimmed in dark theme as all media in the
 * conversation (`docs/DESIGN_SYSTEM.md` §10), and follow the automatic download rules; videos and GIFs show their
 * still with the play or GIF badge. Spoilers show only the blurred minithumbnail.
 */
@Composable
internal fun RichMedia(
    block: RichBlock.Media,
    scope: RichScope,
) {
    val sizes = MaterialTheme.sizes
    val shape = MaterialTheme.shapes.medium
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        when (block) {
            is RichBlock.Photo -> {
                val largest = largestPhotoSize(block.photo.sizes)
                val size =
                    mediaDisplaySize(
                        largest?.width ?: 0,
                        largest?.height ?: 0,
                        sizes.mediaMaxWidth,
                        sizes.mediaMaxHeight,
                        sizes.mediaMinSize,
                    )
                PhotoImage(
                    photo = block.photo,
                    displaySize = size,
                    hidden = block.hasSpoiler,
                    modifier = Modifier.fillMaxWidth().height(size.height).clip(shape),
                    downloads = scope.downloads,
                    onDownload = { scope.onEvent(ChatEvent.DownloadFile(it)) },
                    onCancelDownload = { scope.onEvent(ChatEvent.CancelDownload(it)) },
                )
            }

            is RichBlock.Video -> {
                RichVideoStill(block.video.still(), block.hasSpoiler, isGif = false)
            }

            is RichBlock.Animation -> {
                RichVideoStill(block.animation.still(), block.hasSpoiler, isGif = true)
            }
        }
        RichCaptionText(block.caption, scope)
    }
}

@Composable
private fun RichVideoStill(
    still: VideoStill,
    hidden: Boolean,
    isGif: Boolean,
) {
    val sizes = MaterialTheme.sizes
    val size =
        mediaDisplaySize(still.width, still.height, sizes.mediaMaxWidth, sizes.mediaMaxHeight, sizes.mediaMinSize)
    Box(Modifier.fillMaxWidth().height(size.height).clip(MaterialTheme.shapes.medium)) {
        VideoThumbnail(still, size, hidden, Modifier.matchParentSize())
        if (isGif) GifBadge(Modifier.align(Alignment.Center)) else PlayBadge(Modifier.align(Alignment.Center))
    }
}

/** Photos and videos together (a collage or a slideshow), one after another, with their [caption] below them. */
@Composable
internal fun RichMediaGroup(
    blocks: ImmutableList<RichBlock>,
    caption: RichCaption,
    scope: RichScope,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        RichBlocks(blocks, scope)
        RichCaptionText(caption, scope)
    }
}

/** A media block's caption in `bodyMedium` and who it credits in `labelMedium`, both in the secondary content color. */
@Composable
private fun RichCaptionText(
    caption: RichCaption,
    scope: RichScope,
) {
    if (caption.isEmpty) return
    val typography = MaterialTheme.typography
    if (caption.text.text.isNotBlank()) {
        RichText(caption.text, scope, typography.bodyMedium, color = scope.colors.secondaryContent)
    }
    if (caption.credit.text.isNotBlank()) {
        RichText(caption.credit, scope, typography.labelMedium, color = scope.colors.secondaryContent)
    }
}

/**
 * A row of buttons sharing its width: tonal buttons (`secondaryContainer`) with their label on one line. Buttons that
 * open a link open it as links in texts do, with the open-in-new symbol; Pingogram offers no other kind here yet, so
 * those show disabled, never a tap that does nothing.
 */
@Composable
internal fun RichButtonRow(block: RichBlock.ButtonRow) {
    val iconSize = MaterialTheme.sizes.inlineIcon
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        block.buttons.forEach { button ->
            val url = (button.type as? InlineKeyboardButtonType.Url)?.url
            FilledTonalButton(
                onClick = { url?.let(uriHandler::openUri) },
                modifier = Modifier.weight(1f),
                enabled = url != null,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.SmallContentPadding,
            ) {
                Text(
                    text = button.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.userContent,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (url != null) {
                    Icon(
                        painter = painterResource(R.drawable.ic_open_in_new),
                        contentDescription = null,
                        modifier = Modifier.padding(start = MaterialTheme.spacing.extraSmall).size(iconSize),
                    )
                }
            }
        }
    }
}
