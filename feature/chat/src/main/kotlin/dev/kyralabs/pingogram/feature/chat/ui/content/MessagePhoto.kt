package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A photo message without its caption (`REQUIREMENTS.md` FR-C4; `docs/DESIGN_SYSTEM.md` §8): edge to edge in its
 * bubble ([isEdgeToEdge]), clipped to [shape].
 *
 * - **Size**: the photo's aspect ratio within [maxWidth] × `mediaMaxHeight`, no side shorter than `mediaMinSize`
 *   ([mediaDisplaySize]); the image cropped to fill it.
 * - **Image**: the smallest TDLib size that covers it in pixels ([selectPhotoSize], `docs/PERFORMANCE.md` §7), faded
 *   in over the blurred minithumbnail on the motion scheme's default effects spring ([PhotoImage]). The full size is
 *   for the media viewer.
 * - **Hidden**: spoilers and self-destructing photos show only the blurred minithumbnail; revealing them is the
 *   viewer's job.
 * - **Dark theme**: dimmed to 90% of its brightness, as all media in the conversation (`docs/DESIGN_SYSTEM.md` §10);
 *   the media viewer shows it undimmed.
 *
 * Tapping it calls [onOpen]. Screen readers hear "Photo".
 *
 * @param shape the clip, `large` by default (§8); the bubble passes another one where its own corners differ.
 * @param maxWidth the widest the photo gets: `mediaMaxWidth`, or less in a narrower bubble.
 */
@Composable
internal fun MessagePhoto(
    content: MessageContent.Photo,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    maxWidth: Dp = MaterialTheme.sizes.mediaMaxWidth,
) {
    val photo = content.photo
    val sizes = MaterialTheme.sizes
    val largest = largestPhotoSize(photo.sizes)
    val displaySize =
        mediaDisplaySize(
            width = largest?.width ?: 0,
            height = largest?.height ?: 0,
            maxWidth = maxWidth,
            maxHeight = sizes.mediaMaxHeight,
            minSize = sizes.mediaMinSize,
        )
    val description = stringResource(UiR.string.preview_photo)
    PhotoImage(
        photo = photo,
        displaySize = displaySize,
        hidden = content.hasSpoiler || content.isSecret,
        modifier =
            modifier
                .size(displaySize)
                .clip(shape)
                .clickable(onClickLabel = stringResource(R.string.content_open), onClick = onOpen)
                .semantics { contentDescription = description },
    )
}

/**
 * [photo] cropped to fill [displaySize]: its blurred minithumbnail, and over it the smallest size that covers the box
 * ([selectPhotoSize]), faded in once loaded. With [hidden], the minithumbnail only. The caller sizes and clips it.
 * Both are tinted by [colorFilter]: dimmed in dark theme by default (`docs/DESIGN_SYSTEM.md` §10); null for a photo
 * that is no media, such as a sponsor's avatar.
 */
@Composable
internal fun PhotoImage(
    photo: Photo,
    displaySize: DpSize,
    hidden: Boolean,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = MaterialTheme.pingogramColors.mediaColorFilter,
) {
    Box(modifier) {
        MinithumbnailPlaceholder(photo.minithumbnail, displaySize, Modifier.matchParentSize(), colorFilter)
        if (!hidden) {
            val density = LocalDensity.current
            val widthPx = with(density) { displaySize.width.roundToPx() }
            val heightPx = with(density) { displaySize.height.roundToPx() }
            val size = remember(photo.sizes, widthPx, heightPx) { selectPhotoSize(photo.sizes, widthPx, heightPx) }
            if (size != null) {
                TdlibImage(
                    fileId = size.file.id,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    alpha = rememberImageAlpha(size.file.id),
                    modifier = Modifier.matchParentSize(),
                    colorFilter = colorFilter,
                )
            }
        }
    }
}
